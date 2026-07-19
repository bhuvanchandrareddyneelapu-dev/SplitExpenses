package com.splitwisemoney.service.provider;

import com.splitwisemoney.service.SmtpDiagnosticService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * SMTP Email Provider using Spring {@link JavaMailSender}.
 *
 * <p>Includes automatic port fallback strategy:
 * <pre>
 *   Port 587 (STARTTLS) -> Port 465 (SSL SMTPS)
 * </pre>
 * This prevents invitation failures when cloud hosting providers block Port 587.
 */
@Service("gmailSmtpProvider")
public class GmailSmtpProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(GmailSmtpProvider.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;
    private final SmtpDiagnosticService smtpDiagnosticService;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public GmailSmtpProvider(JavaMailSender mailSender, SmtpDiagnosticService smtpDiagnosticService) {
        this.mailSender = mailSender;
        this.smtpDiagnosticService = smtpDiagnosticService;
    }

    @Override
    public String getProviderName() {
        return "gmail";
    }

    @Override
    public boolean isConfigured() {
        return mailEnabled
                && fromAddress != null && !fromAddress.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
    }

    @Override
    public void sendExistingUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        String subject = inviterName + " invited you to join \"" + groupName + "\" on SplitWiseMoney";
        String acceptUrl = baseUrl + "/invite.html?token=" + token;
        String rejectUrl = baseUrl + "/api/invitations/" + token + "/reject";
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildExistingUserHtml(inviterName, groupName, acceptUrl, rejectUrl, expiry);
        sendWithFallback(toEmail, subject, html, false);
    }

    @Override
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        String subject = "You're invited to join \"" + groupName + "\" on SplitWiseMoney";
        String registerUrl = baseUrl + "/register.html?invitationToken=" + token;
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildNewUserHtml(inviterName, groupName, registerUrl, expiry);
        sendWithFallback(toEmail, subject, html, false);
    }

    @Override
    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        String subject = accepterName + " joined \"" + groupName + "\"";
        String html = buildStatusHtml("🎉 Invitation Accepted",
                accepterName + " has accepted your invitation and joined <strong>" + groupName + "</strong>.", "#27ae60");
        sendWithFallback(toEmail, subject, html, false);
    }

    @Override
    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        String subject = rejectorName + " declined your invitation to \"" + groupName + "\"";
        String html = buildStatusHtml("Invitation Declined",
                rejectorName + " has declined your invitation to join <strong>" + groupName + "</strong>.", "#e74c3c");
        sendWithFallback(toEmail, subject, html, false);
    }

    @Override
    public void sendTestEmail(String toEmail) {
        String subject = "SplitWise Direct SMTP Test";
        String text = "This is a direct SMTP test.\n\nHost: " + smtpHost + ":" + smtpPort;
        sendWithFallback(toEmail, subject, text, true);
    }

    /**
     * Executes mail send with automatic Port 587 -> Port 465 fallback.
     */
    private void sendWithFallback(String to, String subject, String content, boolean isPlainText) {
        if (!isConfigured()) {
            throw new MailSendException("Gmail SMTP is not configured (MAIL_USERNAME / MAIL_PASSWORD missing or spring.mail.enabled=false)");
        }

        // Try primary configured sender first
        try {
            log.info("[GmailSmtpProvider] Attempting dispatch via {}:{}", smtpHost, smtpPort);
            sendInternal(mailSender, to, subject, content, isPlainText);
            log.info("[GmailSmtpProvider] ✓ Delivered via {}:{}", smtpHost, smtpPort);
            return;
        } catch (Exception primaryEx) {
            String errorMsg = primaryEx.getMessage() != null ? primaryEx.getMessage() : primaryEx.toString();
            log.warn("[GmailSmtpProvider] ⚠️ Primary transport ({}:{}) failed: {}", smtpHost, smtpPort, errorMsg);

            // If primary port was 587 and connection timed out, attempt Port 465 SMTPS Fallback
            if (smtpPort == 587) {
                log.info("[GmailSmtpProvider] 🔄 Triggering automatic fallback to SMTPS Port 465 (SSL)...");
                try {
                    JavaMailSender fallbackSender = createPort465Sender();
                    sendInternal(fallbackSender, to, subject, content, isPlainText);
                    log.info("[GmailSmtpProvider] ✓ Fallback succeeded! Delivered via {}:465 (SSL)", smtpHost);
                    return;
                } catch (Exception fallbackEx) {
                    log.error("[GmailSmtpProvider] ✗ SMTPS Port 465 fallback also failed: {}", fallbackEx.getMessage());
                }
            }

            if (primaryEx instanceof MailException) {
                throw (MailException) primaryEx;
            }
            throw new MailSendException("Gmail SMTP delivery failed on both ports: " + errorMsg, primaryEx);
        }
    }

    private void sendInternal(JavaMailSender sender, String to, String subject, String content, boolean isPlainText) throws Exception {
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, !isPlainText, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, !isPlainText);
        sender.send(msg);
    }

    /** Dynamically creates a JavaMailSender configured for Port 465 SSL. */
    private JavaMailSender createPort465Sender() {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(smtpHost);
        impl.setPort(465);
        impl.setUsername(fromAddress);
        impl.setPassword(smtpPassword);
        impl.setDefaultEncoding("UTF-8");

        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.ssl.trust", "*");

        return impl;
    }

    private String buildExistingUserHtml(String inviterName, String groupName, String acceptUrl, String rejectUrl, String expiry) {
        return "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f0f1a;color:#e2e8f0;padding:20px;'>"
                + "<div style='max-width:500px;margin:auto;background:#1a1a2e;padding:30px;border-radius:12px;border:1px solid #6366f1;'>"
                + "<h2 style='color:#a5b4fc;'>💸 SplitWiseMoney Invitation</h2>"
                + "<p><strong>" + esc(inviterName) + "</strong> invited you to join <strong>" + esc(groupName) + "</strong>.</p>"
                + "<p>Expires: " + expiry + "</p>"
                + "<div style='margin:20px 0;'><a href='" + acceptUrl + "' style='background:#10b981;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;'>✓ Accept Invitation</a></div>"
                + "</div></body></html>";
    }

    private String buildNewUserHtml(String inviterName, String groupName, String registerUrl, String expiry) {
        return "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f0f1a;color:#e2e8f0;padding:20px;'>"
                + "<div style='max-width:500px;margin:auto;background:#1a1a2e;padding:30px;border-radius:12px;border:1px solid #6366f1;'>"
                + "<h2 style='color:#a5b4fc;'>💸 Join SplitWiseMoney</h2>"
                + "<p><strong>" + esc(inviterName) + "</strong> invited you to join <strong>" + esc(groupName) + "</strong>.</p>"
                + "<p>Create an account to join: <a href='" + registerUrl + "' style='color:#a5b4fc;'>" + registerUrl + "</a></p>"
                + "</div></body></html>";
    }

    private String buildStatusHtml(String title, String body, String color) {
        return "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f0f1a;color:#e2e8f0;padding:20px;'>"
                + "<div style='max-width:450px;margin:auto;background:#1a1a2e;padding:25px;border-radius:12px;border:1px solid " + color + ";'>"
                + "<h3 style='color:" + color + ";'>" + title + "</h3><p>" + body + "</p></div></body></html>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
