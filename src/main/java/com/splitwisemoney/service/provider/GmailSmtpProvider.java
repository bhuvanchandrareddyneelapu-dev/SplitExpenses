package com.splitwisemoney.service.provider;

import com.splitwisemoney.config.FrontendUrlResolver;
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

    private final FrontendUrlResolver frontendUrlResolver;

    public GmailSmtpProvider(JavaMailSender mailSender, FrontendUrlResolver frontendUrlResolver) {
        this.mailSender = mailSender;
        this.frontendUrlResolver = frontendUrlResolver;
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
        String baseUrl = frontendUrlResolver.getBaseUrl();
        String subject = inviterName + " invited you to join \"" + groupName + "\" on SplitWiseMoney";
        String acceptUrl = baseUrl + "/invite.html?token=" + token;
        String declineUrl = baseUrl + "/invite.html?token=" + token;
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "48 hours from now";

        String html = buildExistingUserHtml(inviterName, groupName, acceptUrl, declineUrl, expiry);
        sendWithFallback(toEmail, subject, html, false);
    }

    @Override
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        String baseUrl = frontendUrlResolver.getBaseUrl();
        String subject = "You're invited to join \"" + groupName + "\" on SplitWiseMoney";
        String registerUrl = baseUrl + "/register.html?invite=" + token + "&email=" + toEmail;
        String declineUrl = baseUrl + "/invite.html?token=" + token;
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "48 hours from now";

        String html = buildNewUserHtml(inviterName, groupName, registerUrl, declineUrl, expiry);
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

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Mail service initialized successfully. SMTP host: {}:{}, Username: {}", smtpHost, smtpPort, fromAddress);
    }

    /**
     * Executes mail send with automatic Port 587 -> Port 465 fallback.
     */
    private static final java.util.Set<String> BLOCKED_TEST_DOMAINS = java.util.Set.of("test.com", "example.com", "invalid.com", "fake.com", "tempmail.com");

    private void sendWithFallback(String to, String subject, String content, boolean isPlainText) {
        if (!isConfigured()) {
            log.error("Authentication failure / Invalid credentials: Mail service disabled or missing credentials (username='{}', mailEnabled={})", fromAddress, mailEnabled);
            throw new MailSendException("Gmail SMTP is not configured (MAIL_USERNAME / MAIL_PASSWORD missing or spring.mail.enabled=false)");
        }

        if (to == null || to.isBlank()) {
            log.error("[GmailSmtpProvider] Cannot send email: recipient address is null or empty");
            throw new IllegalArgumentException("Recipient email address is required");
        }

        String cleanedTo = to.trim().toLowerCase();
        String domain = cleanedTo.contains("@") ? cleanedTo.substring(cleanedTo.indexOf('@') + 1) : "";

        if (BLOCKED_TEST_DOMAINS.contains(domain)) {
            log.warn("[GmailSmtpProvider] [SAFETY GUARD] Blocked live SMTP dispatch to test/fake domain '{}' for recipient '{}'. No real email sent.", domain, to);
            return;
        }

        log.info("""
                ======================================================================
                [SMTP DISPATCH AUDIT LOG]
                SMTP FROM                       : {}
                SMTP TO                         : {}
                SUBJECT                         : {}
                Recipient Passed to helper.setTo: {}
                Recipient Passed to SMTP        : {}
                ======================================================================
                """, fromAddress, to, subject, to, to);

        // Try primary configured sender first
        try {
            sendInternal(mailSender, to, subject, content, isPlainText);
            log.info("Email successfully sent to {}.", to);
            return;
        } catch (Exception primaryEx) {
            String errorMsg = primaryEx.getMessage() != null ? primaryEx.getMessage() : primaryEx.toString();

            if (primaryEx instanceof jakarta.mail.AuthenticationFailedException || errorMsg.contains("535") || errorMsg.toLowerCase().contains("authentication")) {
                log.error("Authentication failure / Invalid credentials connecting to {}:{}", smtpHost, smtpPort, primaryEx);
            } else if (primaryEx instanceof java.net.SocketTimeoutException || errorMsg.toLowerCase().contains("timeout")) {
                log.error("SMTP timeout connecting to {}:{}", smtpHost, smtpPort, primaryEx);
            } else if (primaryEx instanceof java.net.ConnectException || primaryEx instanceof java.net.UnknownHostException) {
                log.error("Mail server unavailable at {}:{}", smtpHost, smtpPort, primaryEx);
            } else {
                log.error("Failed to send invitation email to {}: {}", to, errorMsg, primaryEx);
            }

            // If primary port was 587 and connection timed out, attempt Port 465 SMTPS Fallback
            if (smtpPort == 587) {
                log.info("🔄 Triggering automatic fallback to SMTPS Port 465 (SSL)...");
                try {
                    JavaMailSender fallbackSender = createPort465Sender();
                    sendInternal(fallbackSender, to, subject, content, isPlainText);
                    log.info("Email successfully sent to {} via SMTPS Port 465 (SSL).", to);
                    return;
                } catch (Exception fallbackEx) {
                    log.error("SMTPS Port 465 fallback also failed: {}", fallbackEx.getMessage(), fallbackEx);
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

    private String buildExistingUserHtml(String inviterName, String groupName, String acceptUrl, String declineUrl, String expiry) {
        return "<!DOCTYPE html><html><body style='font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f0f1a;color:#e2e8f0;padding:40px 20px;'>"
                + "<div style='max-width:540px;margin:auto;background:#1a1a2e;padding:35px;border-radius:16px;border:1px solid #6366f1;box-shadow:0 20px 25px -5px rgba(0,0,0,0.5);'>"
                + "<div style='text-align:center;margin-bottom:25px;'><h1 style='color:#818cf8;font-size:26px;margin:0;font-weight:800;'>💸 SplitWiseMoney</h1><p style='color:#94a3b8;font-size:13px;margin-top:4px;'>Smart Expense Sharing</p></div>"
                + "<h2 style='color:#ffffff;font-size:20px;margin-bottom:14px;'>You've been invited to join \"" + esc(groupName) + "\"</h2>"
                + "<p><strong>" + esc(inviterName) + "</strong> has invited you to join the expense sharing group <strong>" + esc(groupName) + "</strong>.</p>"
                + "<p style='color:#94a3b8;font-size:14px;margin-bottom:25px;'>⏳ Valid for <strong>48 hours</strong> (Expires: " + expiry + ").</p>"
                + "<div style='margin:30px 0;text-align:center;'>"
                + "<a href='" + acceptUrl + "' style='background:#10b981;color:#fff;padding:12px 26px;border-radius:10px;text-decoration:none;font-weight:700;margin-right:12px;display:inline-block;'>✓ Accept & Join</a>"
                + "<a href='" + declineUrl + "' style='background:rgba(239,68,68,0.15);color:#f87171;border:1px solid rgba(239,68,68,0.3);padding:12px 20px;border-radius:10px;text-decoration:none;font-weight:600;display:inline-block;'>Decline</a>"
                + "</div>"
                + "<hr style='border:0;border-top:1px solid #334155;margin:25px 0;'/>"
                + "<p style='text-align:center;color:#64748b;font-size:12px;'>SplitWiseMoney — Simplify group expenses with ease.</p>"
                + "</div></body></html>";
    }

    private String buildNewUserHtml(String inviterName, String groupName, String registerUrl, String declineUrl, String expiry) {
        return "<!DOCTYPE html><html><body style='font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#0f0f1a;color:#e2e8f0;padding:40px 20px;'>"
                + "<div style='max-width:540px;margin:auto;background:#1a1a2e;padding:35px;border-radius:16px;border:1px solid #6366f1;box-shadow:0 20px 25px -5px rgba(0,0,0,0.5);'>"
                + "<div style='text-align:center;margin-bottom:25px;'><h1 style='color:#818cf8;font-size:26px;margin:0;font-weight:800;'>💸 SplitWiseMoney</h1><p style='color:#94a3b8;font-size:13px;margin-top:4px;'>Smart Expense Sharing</p></div>"
                + "<h2 style='color:#ffffff;font-size:20px;margin-bottom:14px;'>Join SplitWiseMoney — You've been invited!</h2>"
                + "<p><strong>" + esc(inviterName) + "</strong> invited you to join <strong>" + esc(groupName) + "</strong>.</p>"
                + "<p style='color:#94a3b8;font-size:14px;margin-bottom:25px;'>⏳ Valid for <strong>48 hours</strong> (Expires: " + expiry + ").</p>"
                + "<p style='color:#cbd5e1;margin-bottom:25px;'>Create your account to automatically accept and join the group:</p>"
                + "<div style='margin:30px 0;text-align:center;'>"
                + "<a href='" + registerUrl + "' style='background:#6366f1;color:#fff;padding:12px 26px;border-radius:10px;text-decoration:none;font-weight:700;margin-right:12px;display:inline-block;'>Create Account & Join</a>"
                + "<a href='" + declineUrl + "' style='background:rgba(239,68,68,0.15);color:#f87171;border:1px solid rgba(239,68,68,0.3);padding:12px 20px;border-radius:10px;text-decoration:none;font-weight:600;display:inline-block;'>Decline</a>"
                + "</div>"
                + "<hr style='border:0;border-top:1px solid #334155;margin:25px 0;'/>"
                + "<p style='text-align:center;color:#64748b;font-size:12px;'>SplitWiseMoney — Simplify group expenses with ease.</p>"
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
