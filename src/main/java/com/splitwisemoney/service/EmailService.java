package com.splitwisemoney.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends HTML invitation emails via Spring Mail / Gmail SMTP.
 *
 * <p><b>Startup behaviour:</b>
 * <ol>
 *   <li>Dumps the complete mail configuration on startup (passwords masked).</li>
 *   <li>If {@code spring.mail.enabled=true} AND credentials are present, sends a
 *       one-shot self-test email to verify SMTP connectivity. Application startup
 *       fails fast with the full SMTP exception if this test fails.</li>
 * </ol>
 *
 * <p><b>Per-send behaviour:</b>
 * <ul>
 *   <li>Logs FROM / TO / SUBJECT / SMTP HOST / SMTP PORT before every send.</li>
 *   <li>Logs {@code SMTP ACCEPTED MESSAGE} after successful transport.</li>
 *   <li>Logs complete stack trace with SMTP status on failure and re-throws.</li>
 * </ul>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;

    // ── Injected Spring Mail properties ──────────────────────────────────────

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${spring.mail.password:}")
    private String smtpPassword;          // value never logged; only .isBlank() is checked

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ─────────────────────────────────────────────────────────────────────────

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** True when mail is enabled AND credentials are actually configured. */
    private boolean isMailConfigured() {
        return mailEnabled
                && fromAddress != null && !fromAddress.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Startup Diagnostic & Self-Test
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs immediately after the bean is constructed.
     * <ol>
     *   <li>Prints every relevant mail property (no passwords in clear text).</li>
     *   <li>Detects Railway / system environment variables that affect mail.</li>
     *   <li>If mail is fully configured, attempts a startup SMTP self-test.</li>
     * </ol>
     *
     * <p><b>CRITICAL:</b> This method NEVER throws. Any failure is recorded as an
     * ERROR log only. The Spring context must always load regardless of SMTP state.
     * Throwing from @PostConstruct kills the Spring context and causes a crash loop
     * on Railway, because Railway periodically restarts containers and a transient
     * SMTP error would permanently prevent the app from restarting.
     */
    @PostConstruct
    public void startupMailDiagnostic() {

        // ── 1. Property Dump ─────────────────────────────────────────────────
        log.info("=================================================================");
        log.info("[EmailService] STARTUP MAIL CONFIGURATION DIAGNOSTIC");
        log.info("=================================================================");
        log.info("[EmailService]  spring.mail.enabled    = {}", mailEnabled);
        log.info("[EmailService]  spring.mail.host       = {}", smtpHost);
        log.info("[EmailService]  spring.mail.port       = {}", smtpPort);
        log.info("[EmailService]  spring.mail.username   = {}",
                isBlank(fromAddress) ? "<EMPTY>" : fromAddress);
        log.info("[EmailService]  MAIL_USERNAME set      = {}", !isBlank(fromAddress));
        log.info("[EmailService]  MAIL_PASSWORD set      = {}", !isBlank(smtpPassword));

        // ── 2. Railway / System Environment Variable Detection ───────────────
        log.info("-----------------------------------------------------------------");
        log.info("[EmailService]  ENVIRONMENT VARIABLE DETECTION");
        log.info("-----------------------------------------------------------------");
        String[] envKeys = {
            "MAIL_USERNAME", "MAIL_PASSWORD",
            "SPRING_MAIL_USERNAME", "SPRING_MAIL_PASSWORD"
        };
        for (String key : envKeys) {
            String val = System.getenv(key);
            if (val == null) {
                log.warn("[EmailService]    {} = <NOT SET in environment>", key);
            } else if (key.contains("PASSWORD")) {
                log.info("[EmailService]    {} = <SET, {} chars>", key, val.length());
            } else {
                log.info("[EmailService]    {} = {}", key, val);
            }
        }

        // ── 3. Decision Gate ─────────────────────────────────────────────────
        log.info("-----------------------------------------------------------------");
        if (!mailEnabled) {
            log.warn("[EmailService]  spring.mail.enabled=false — all emails will be SKIPPED.");
            log.info("=================================================================");
            return;
        }
        if (isBlank(fromAddress)) {
            log.error("[EmailService]  MAIL_USERNAME is empty — invitation emails will be SKIPPED.");
            log.error("[EmailService]  Set the MAIL_USERNAME environment variable in Railway.");
            log.info("=================================================================");
            return;   // LOG ONLY — never throw from @PostConstruct
        }
        if (isBlank(smtpPassword)) {
            log.error("[EmailService]  MAIL_PASSWORD is empty — invitation emails will be SKIPPED.");
            log.error("[EmailService]  Set the MAIL_PASSWORD env variable (Gmail App Password).");
            log.info("=================================================================");
            return;   // LOG ONLY — never throw from @PostConstruct
        }

        log.info("[EmailService]  Mail IS fully configured. Running SMTP startup self-test...");
        log.info("=================================================================");

        // ── 4. Startup Self-Test Email — non-fatal ────────────────────────────
        sendStartupTestEmail();
    }

    /**
     * Attempts a plain-text self-send SMTP test at startup.
     *
     * <p><b>NEVER THROWS.</b> Any SMTP failure is logged as ERROR.
     * The app continues starting regardless. Use {@code POST /api/test/email}
     * to manually re-verify SMTP connectivity after deployment.
     */
    private void sendStartupTestEmail() {
        String subject = "SplitWise SMTP Startup Test";
        String body    = "SMTP configuration successful. " +
                         "This message confirms that Gmail SMTP is reachable from the application.";

        log.info("[EmailService] ── STARTUP SMTP SELF-TEST ──────────────────────────────");
        log.info("[EmailService]   FROM    : {}", fromAddress);
        log.info("[EmailService]   TO      : {} (self-send)", fromAddress);
        log.info("[EmailService]   SUBJECT : {}", subject);
        log.info("[EmailService]   HOST    : {}", smtpHost);
        log.info("[EmailService]   PORT    : {}", smtpPort);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(fromAddress);   // self-send to verify delivery
            helper.setSubject(subject);
            helper.setText(body, false); // plain text — simplest possible payload

            log.info("[EmailService]   Invoking mailSender.send() for startup test...");
            mailSender.send(msg);

            log.info("=================================================================");
            log.info("[EmailService] ✓ STARTUP SELF-TEST: SMTP ACCEPTED MESSAGE");
            log.info("[EmailService]   FINAL VERDICT: ✓ Email successfully accepted by Gmail SMTP.");
            log.info("=================================================================");

        } catch (jakarta.mail.MessagingException e) {
            // LOG but DO NOT THROW — app must start regardless of SMTP state
            log.error("=================================================================");
            log.error("[EmailService] ✗ STARTUP SELF-TEST FAILED (MessagingException): {}", e.getMessage(), e);
            log.error("[EmailService]   FINAL VERDICT: ✗ Email rejected by Gmail. Reason: {}", e.getMessage());
            log.error("[EmailService]   App continues starting. Use POST /api/test/email to diagnose.");
            log.error("=================================================================");

        } catch (MailException e) {
            // LOG but DO NOT THROW — app must start regardless of SMTP state
            String rootMsg = e.getMostSpecificCause() != null
                             ? e.getMostSpecificCause().getMessage()
                             : e.getMessage();
            log.error("=================================================================");
            log.error("[EmailService] ✗ STARTUP SELF-TEST FAILED (SMTP/MailException)");
            log.error("[EmailService]   Top-level error : {}", e.getMessage());
            log.error("[EmailService]   Root cause      : {}", rootMsg, e);
            log.error("[EmailService]   535/534 → Invalid Gmail App Password. Regenerate at myaccount.google.com");
            log.error("[EmailService]   530     → TLS not negotiated / auth required.");
            log.error("[EmailService]   550     → Quota exceeded or policy block.");
            log.error("[EmailService]   CONN    → Firewall blocking port 587.");
            log.error("[EmailService]   FINAL VERDICT: ✗ Email rejected by Gmail. Reason: {}", rootMsg);
            log.error("[EmailService]   App continues starting. Use POST /api/test/email to diagnose.");
            log.error("=================================================================");

        } catch (Exception e) {
            // Catch-all safety net — no exception whatsoever should abort the Spring context
            log.error("[EmailService] ✗ STARTUP SELF-TEST FAILED (unexpected {}): {}",
                      e.getClass().getSimpleName(), e.getMessage(), e);
            log.error("[EmailService]   App continues starting. Use POST /api/test/email to diagnose.");
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Public API — Invitation Emails
    // ─────────────────────────────────────────────────────────────────────────

    /** Email for an EXISTING registered user being invited. */
    public void sendExistingUserInvitation(String toEmail, String inviterName, String groupName,
                                           String token, LocalDateTime expiresAt) {
        String subject   = inviterName + " invited you to join \"" + groupName + "\" on SplitWiseMoney";
        String acceptUrl = baseUrl + "/invite.html?token=" + token;
        String rejectUrl = baseUrl + "/api/invitations/" + token + "/reject";
        String expiry    = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildExistingUserHtml(inviterName, groupName, acceptUrl, rejectUrl, expiry);
        send(toEmail, subject, html);
    }

    /** Email for a NON-REGISTERED email being invited (includes registration link). */
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName,
                                      String token, LocalDateTime expiresAt) {
        String subject     = "You're invited to join \"" + groupName + "\" on SplitWiseMoney";
        String registerUrl = baseUrl + "/register.html?invitationToken=" + token;
        String expiry      = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildNewUserHtml(inviterName, groupName, registerUrl, expiry);
        send(toEmail, subject, html);
    }

    /** Notify inviter that their invitation was accepted. */
    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        String subject = accepterName + " joined \"" + groupName + "\"";
        String html    = buildStatusHtml("🎉 Invitation Accepted",
                accepterName + " has accepted your invitation and joined <strong>" + groupName + "</strong>.",
                "#27ae60");
        send(toEmail, subject, html);
    }

    /** Notify inviter that their invitation was rejected. */
    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        String subject = rejectorName + " declined your invitation to \"" + groupName + "\"";
        String html    = buildStatusHtml("Invitation Declined",
                rejectorName + " has declined your invitation to join <strong>" + groupName + "</strong>.",
                "#e74c3c");
        send(toEmail, subject, html);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — Direct SMTP Test (used by /api/test/email endpoint)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a bare-bones plain-text test email, bypassing all invitation logic.
     * Intended for the {@code POST /api/test/email} diagnostic endpoint.
     *
     * @param to  recipient address
     * @return    diagnostic result map with keys: success, from, to, smtpHost, smtpPort, message
     * @throws MailException if SMTP rejects the message
     */
    public Map<String, Object> sendTestEmail(String to) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from",     fromAddress);
        result.put("to",       to);
        result.put("smtpHost", smtpHost);
        result.put("smtpPort", smtpPort);
        result.put("mailEnabled", mailEnabled);
        result.put("credentialsSet", !isBlank(fromAddress) && !isBlank(smtpPassword));

        log.info("[EmailService] ── /api/test/email DIRECT SMTP TEST ──────────────────");
        log.info("[EmailService]   FROM    : {}", fromAddress);
        log.info("[EmailService]   TO      : {}", to);
        log.info("[EmailService]   SUBJECT : SMTP TEST");
        log.info("[EmailService]   HOST    : {}", smtpHost);
        log.info("[EmailService]   PORT    : {}", smtpPort);

        if (!isMailConfigured()) {
            String reason = !mailEnabled
                    ? "spring.mail.enabled=false"
                    : isBlank(fromAddress)
                      ? "MAIL_USERNAME is empty"
                      : "MAIL_PASSWORD is empty";
            log.error("[EmailService] ✗ Cannot send test email — mail not configured: {}", reason);
            result.put("success", false);
            result.put("message", "Mail not configured: " + reason);
            result.put("verdict", "✗ Spring Boot is not loading the mail configuration.");
            return result;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("SMTP TEST");
            helper.setText("This is a direct SMTP verification.\n\nSent from: " + fromAddress +
                           "\nSMTP Host: " + smtpHost + ":" + smtpPort, false);

            log.info("[EmailService]   Invoking mailSender.send()...");
            mailSender.send(msg);

            log.info("[EmailService] ✓ SMTP TEST: SMTP ACCEPTED MESSAGE");
            log.info("[EmailService]   FINAL VERDICT: ✓ Email successfully accepted by Gmail SMTP.");
            result.put("success", true);
            result.put("verdict", "✓ Email successfully accepted by Gmail SMTP.");
            result.put("message", "SMTP ACCEPTED MESSAGE — Gmail returned 250 OK.");
            return result;

        } catch (jakarta.mail.MessagingException e) {
            log.error("[EmailService] ✗ SMTP TEST: MessagingException — {}", e.getMessage(), e);
            result.put("success", false);
            result.put("verdict", "✗ Email rejected by Gmail. Reason: " + e.getMessage());
            result.put("message", e.getMessage());
            throw new MailSendException("SMTP test failed (MessagingException): " + e.getMessage(), e);

        } catch (MailException e) {
            String rootMsg = e.getMostSpecificCause() != null
                             ? e.getMostSpecificCause().getMessage()
                             : e.getMessage();
            log.error("[EmailService] ✗ SMTP TEST: MailException — top={} root={}", e.getMessage(), rootMsg, e);
            log.error("[EmailService]   FINAL VERDICT: ✗ Email rejected by Gmail. Reason: {}", rootMsg);
            result.put("success", false);
            result.put("verdict", "✗ Email rejected by Gmail. Reason: " + rootMsg);
            result.put("message", rootMsg);
            throw e;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal send helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends an HTML email.
     * <ul>
     *   <li>If mail is not configured, logs at WARN and returns — no exception.</li>
     *   <li>If configured and SMTP fails, re-throws so the caller returns 502.</li>
     * </ul>
     *
     * @throws MailException propagated on SMTP failure
     */
    private void send(String to, String subject, String html) {
        if (!isMailConfigured()) {
            log.warn("[EmailService] ✗ EmailService SKIPPING email — mail not configured.");
            log.warn("[EmailService]   FINAL VERDICT: ✗ EmailService never executed (mail disabled/not configured).");
            log.warn("[EmailService]   TO={} SUBJECT={}", to, subject);
            return;
        }

        // ── Pre-send mandatory log ────────────────────────────────────────────
        log.info("[EmailService] ── SENDING EMAIL ───────────────────────────────────");
        log.info("[EmailService]   FROM    : {}", fromAddress);
        log.info("[EmailService]   TO      : {}", to);
        log.info("[EmailService]   SUBJECT : {}", subject);
        log.info("[EmailService]   HOST    : {}", smtpHost);
        log.info("[EmailService]   PORT    : {}", smtpPort);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            log.info("[EmailService]   Invoking mailSender.send() — handing off to SMTP transport...");
            mailSender.send(msg);

            // ── Post-send confirmation ────────────────────────────────────────
            log.info("[EmailService] ✓ SMTP ACCEPTED MESSAGE");
            log.info("[EmailService]   Gmail accepted the message for delivery.");
            log.info("[EmailService]   FINAL VERDICT: ✓ Email successfully accepted by Gmail SMTP.");
            log.info("[EmailService]   to={} subject={}", to, subject);

        } catch (jakarta.mail.MessagingException e) {
            // MIME construction failure — bad address format, encoding error, etc.
            log.error("[EmailService] ✗ MessagingException building MIME message.");
            log.error("[EmailService]   SMTP error to={}: {}", to, e.getMessage(), e);
            log.error("[EmailService]   FINAL VERDICT: ✗ Email rejected by Gmail. Reason: {}", e.getMessage());
            // Wrap in MailSendException (MailException subclass) so controllers catch it
            throw new MailSendException("Failed to build MIME message: " + e.getMessage(), e);

        } catch (MailException e) {
            // Transport failure — auth rejected, 5xx response, connection refused, etc.
            String rootMsg = e.getMostSpecificCause() != null
                             ? e.getMostSpecificCause().getMessage()
                             : e.getMessage();
            log.error("[EmailService] ✗ MailException / SMTP transport failure.");
            log.error("[EmailService]   Top-level error : {}", e.getMessage());
            log.error("[EmailService]   Root cause      : {}", rootMsg, e);
            log.error("[EmailService]   FINAL VERDICT: ✗ Email rejected by Gmail. Reason: {}", rootMsg);
            throw e;  // re-throw — controller returns HTTP 502
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildExistingUserHtml(String inviterName, String groupName,
                                          String acceptUrl, String rejectUrl, String expiry) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Arial,sans-serif;background:#0f0f1a;margin:0;padding:0;}"
                + ".container{max-width:560px;margin:40px auto;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 100%);"
                + "border-radius:16px;overflow:hidden;border:1px solid rgba(99,102,241,.3);}"
                + ".header{background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:40px;text-align:center;}"
                + ".header h1{color:#fff;margin:0;font-size:26px;letter-spacing:-0.5px;}"
                + ".header p{color:rgba(255,255,255,.8);margin:8px 0 0;}"
                + ".body{padding:36px;color:#e2e8f0;}"
                + ".group-name{font-size:22px;font-weight:700;color:#a5b4fc;margin:16px 0;}"
                + ".info{background:rgba(99,102,241,.1);border:1px solid rgba(99,102,241,.2);border-radius:10px;padding:16px;margin:20px 0;}"
                + ".info p{margin:4px 0;font-size:14px;color:#94a3b8;}"
                + ".info strong{color:#e2e8f0;}"
                + ".btn-row{display:flex;gap:12px;margin:28px 0;}"
                + ".btn{display:inline-block;padding:14px 28px;border-radius:10px;font-weight:600;font-size:16px;"
                + "text-decoration:none;text-align:center;flex:1;}"
                + ".btn-accept{background:linear-gradient(135deg,#10b981,#059669);color:#fff;}"
                + ".btn-reject{background:rgba(239,68,68,.15);color:#f87171;border:1px solid rgba(239,68,68,.3);}"
                + ".footer{padding:20px 36px;text-align:center;color:#4b5563;font-size:12px;border-top:1px solid rgba(255,255,255,.05);}"
                + "</style></head><body>"
                + "<div class='container'>"
                + "<div class='header'>"
                + "<h1>💸 SplitWiseMoney</h1>"
                + "<p>You've been invited to a group!</p>"
                + "</div>"
                + "<div class='body'>"
                + "<p>Hey there! <strong>" + escHtml(inviterName) + "</strong> has invited you to join:</p>"
                + "<div class='group-name'>📁 " + escHtml(groupName) + "</div>"
                + "<div class='info'>"
                + "<p><strong>Invited by:</strong> " + escHtml(inviterName) + "</p>"
                + "<p><strong>Expires:</strong> " + expiry + "</p>"
                + "</div>"
                + "<div class='btn-row'>"
                + "<a href='" + acceptUrl + "' class='btn btn-accept'>✓ Accept Invitation</a>"
                + "<a href='" + rejectUrl + "' class='btn btn-reject'>✗ Decline</a>"
                + "</div>"
                + "<p style='color:#6b7280;font-size:13px;'>Or copy this link: <a href='" + acceptUrl + "' style='color:#a5b4fc;'>" + acceptUrl + "</a></p>"
                + "</div>"
                + "<div class='footer'>© SplitWiseMoney · This invitation expires on " + expiry + "</div>"
                + "</div></body></html>";
    }

    private String buildNewUserHtml(String inviterName, String groupName, String registerUrl, String expiry) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Arial,sans-serif;background:#0f0f1a;margin:0;padding:0;}"
                + ".container{max-width:560px;margin:40px auto;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 100%);"
                + "border-radius:16px;overflow:hidden;border:1px solid rgba(99,102,241,.3);}"
                + ".header{background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:40px;text-align:center;}"
                + ".header h1{color:#fff;margin:0;font-size:26px;}"
                + ".header p{color:rgba(255,255,255,.8);margin:8px 0 0;}"
                + ".body{padding:36px;color:#e2e8f0;}"
                + ".group-name{font-size:22px;font-weight:700;color:#a5b4fc;margin:16px 0;}"
                + ".info{background:rgba(99,102,241,.1);border:1px solid rgba(99,102,241,.2);border-radius:10px;padding:16px;margin:20px 0;}"
                + ".info p{margin:4px 0;font-size:14px;color:#94a3b8;}"
                + ".info strong{color:#e2e8f0;}"
                + ".btn{display:block;padding:16px;border-radius:10px;font-weight:700;font-size:17px;"
                + "text-decoration:none;text-align:center;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;margin:24px 0;}"
                + ".footer{padding:20px 36px;text-align:center;color:#4b5563;font-size:12px;border-top:1px solid rgba(255,255,255,.05);}"
                + "</style></head><body>"
                + "<div class='container'>"
                + "<div class='header'>"
                + "<h1>💸 SplitWiseMoney</h1>"
                + "<p>You're invited to split expenses!</p>"
                + "</div>"
                + "<div class='body'>"
                + "<p>Hey! <strong>" + escHtml(inviterName) + "</strong> invited you to join:</p>"
                + "<div class='group-name'>📁 " + escHtml(groupName) + "</div>"
                + "<div class='info'>"
                + "<p><strong>Invited by:</strong> " + escHtml(inviterName) + "</p>"
                + "<p><strong>Expires:</strong> " + expiry + "</p>"
                + "</div>"
                + "<p>Create your free account and you'll be automatically added to the group:</p>"
                + "<a href='" + registerUrl + "' class='btn'>🚀 Create Account &amp; Join Group</a>"
                + "<p style='color:#6b7280;font-size:13px;'>Or copy: <a href='" + registerUrl + "' style='color:#a5b4fc;'>" + registerUrl + "</a></p>"
                + "</div>"
                + "<div class='footer'>© SplitWiseMoney · This invitation expires on " + expiry + "</div>"
                + "</div></body></html>";
    }

    private String buildStatusHtml(String title, String body, String color) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<style>"
                + "body{font-family:'Segoe UI',Arial,sans-serif;background:#0f0f1a;margin:0;padding:0;}"
                + ".container{max-width:480px;margin:40px auto;background:#1a1a2e;border-radius:16px;"
                + "border:1px solid rgba(99,102,241,.3);overflow:hidden;}"
                + ".header{background:" + color + ";padding:36px;text-align:center;}"
                + ".header h1{color:#fff;margin:0;font-size:22px;}"
                + ".body{padding:32px;color:#e2e8f0;text-align:center;font-size:16px;}"
                + ".footer{padding:16px;text-align:center;color:#4b5563;font-size:12px;}"
                + "</style></head><body>"
                + "<div class='container'>"
                + "<div class='header'><h1>" + title + "</h1></div>"
                + "<div class='body'><p>" + body + "</p></div>"
                + "<div class='footer'>© SplitWiseMoney</div>"
                + "</div></body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
