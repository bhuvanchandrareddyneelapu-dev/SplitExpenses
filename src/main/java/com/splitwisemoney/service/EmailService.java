package com.splitwisemoney.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends HTML invitation emails via Spring Mail.
 * If {@code mail.enabled=false} or no SMTP credentials are configured,
 * all send attempts are silently logged and skipped (dev-friendly).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public send methods
    // ─────────────────────────────────────────────────────────────────────────

    /** Email for an EXISTING registered user being invited. */
    public void sendExistingUserInvitation(String toEmail, String inviterName, String groupName,
                                           String token, LocalDateTime expiresAt) {
        String subject = inviterName + " invited you to join \"" + groupName + "\" on SplitWiseMoney";
        String acceptUrl = baseUrl + "/invite.html?token=" + token;
        String rejectUrl = baseUrl + "/api/invitations/" + token + "/reject";
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildExistingUserHtml(inviterName, groupName, acceptUrl, rejectUrl, expiry);
        send(toEmail, subject, html);
    }

    /** Email for a NON-REGISTERED email being invited (includes registration link). */
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName,
                                      String token, LocalDateTime expiresAt) {
        String subject = "You're invited to join \"" + groupName + "\" on SplitWiseMoney";
        String registerUrl = baseUrl + "/register.html?invitationToken=" + token;
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildNewUserHtml(inviterName, groupName, registerUrl, expiry);
        send(toEmail, subject, html);
    }

    /** Notify inviter that their invitation was accepted. */
    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        String subject = accepterName + " joined \"" + groupName + "\"";
        String html = buildStatusHtml("🎉 Invitation Accepted",
                accepterName + " has accepted your invitation and joined <strong>" + groupName + "</strong>.",
                "#27ae60");
        send(toEmail, subject, html);
    }

    /** Notify inviter that their invitation was rejected. */
    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        String subject = rejectorName + " declined your invitation to \"" + groupName + "\"";
        String html = buildStatusHtml("Invitation Declined",
                rejectorName + " has declined your invitation to join <strong>" + groupName + "</strong>.",
                "#e74c3c");
        send(toEmail, subject, html);
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
    // Internal send helper
    // ─────────────────────────────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        if (!mailEnabled || fromAddress == null || fromAddress.isBlank()) {
            log.info("[EmailService] Mail disabled — would have sent to={} subject={}", to, subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("[EmailService] Sent email to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send email to={}: {}", to, e.getMessage(), e);
        }
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
