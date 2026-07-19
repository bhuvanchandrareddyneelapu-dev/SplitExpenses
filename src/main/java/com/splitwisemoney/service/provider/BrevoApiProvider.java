package com.splitwisemoney.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Email Provider using Brevo (formerly Sendinblue) Transactional HTTP REST API over HTTPS (Port 443).
 *
 * <p><b>Why HTTPS REST API?</b> Cloud container hosts (Railway, AWS, GCP, Heroku) block outbound
 * SMTP ports 25 and 587. HTTP REST API calls over Port 443 are NEVER blocked by cloud platforms.
 */
@Service("brevoApiProvider")
public class BrevoApiProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(BrevoApiProvider.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final RestTemplate restTemplate;

    @Value("${brevo.api-key:${BREVO_API_KEY:}}")
    private String apiKey;

    @Value("${spring.mail.username:${MAIL_USERNAME:}}")
    private String senderEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public BrevoApiProvider() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "brevo";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderEmail != null && !senderEmail.isBlank();
    }

    @Override
    public void sendExistingUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        String subject = inviterName + " invited you to join \"" + groupName + "\" on SplitWiseMoney";
        String acceptUrl = baseUrl + "/invite.html?token=" + token;
        String rejectUrl = baseUrl + "/api/invitations/" + token + "/reject";
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildExistingUserHtml(inviterName, groupName, acceptUrl, rejectUrl, expiry);
        sendViaHttpApi(toEmail, subject, html);
    }

    @Override
    public void sendNewUserInvitation(String toEmail, String inviterName, String groupName, String token, LocalDateTime expiresAt) {
        String subject = "You're invited to join \"" + groupName + "\" on SplitWiseMoney";
        String registerUrl = baseUrl + "/register.html?invitationToken=" + token;
        String expiry = expiresAt != null ? expiresAt.format(FMT) : "7 days from now";

        String html = buildNewUserHtml(inviterName, groupName, registerUrl, expiry);
        sendViaHttpApi(toEmail, subject, html);
    }

    @Override
    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        String subject = accepterName + " joined \"" + groupName + "\"";
        String html = buildStatusHtml("🎉 Invitation Accepted",
                accepterName + " accepted your invitation and joined <strong>" + groupName + "</strong>.", "#27ae60");
        sendViaHttpApi(toEmail, subject, html);
    }

    @Override
    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        String subject = rejectorName + " declined your invitation to \"" + groupName + "\"";
        String html = buildStatusHtml("Invitation Declined",
                rejectorName + " declined your invitation to join <strong>" + groupName + "</strong>.", "#e74c3c");
        sendViaHttpApi(toEmail, subject, html);
    }

    @Override
    public void sendTestEmail(String toEmail) {
        String subject = "SplitWise Brevo HTTPS API Test";
        String html = "<p>This is a test email sent via <strong>Brevo HTTPS REST API</strong> over Port 443.</p>";
        sendViaHttpApi(toEmail, subject, html);
    }

    private void sendViaHttpApi(String toEmail, String subject, String htmlContent) {
        if (!isConfigured()) {
            throw new MailSendException("Brevo API is not configured (BREVO_API_KEY or MAIL_USERNAME missing)");
        }

        log.info("[BrevoApiProvider] Sending email via HTTPS API (Port 443) -> to={}", toEmail);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("email", senderEmail, "name", "SplitWiseMoney"));
        body.put("to", List.of(Map.of("email", toEmail)));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[BrevoApiProvider] ✓ Delivered via Brevo HTTP REST API (Status 200/201)");
            } else {
                throw new MailSendException("Brevo API returned HTTP " + response.getStatusCodeValue() + ": " + response.getBody());
            }
        } catch (Exception e) {
            log.error("[BrevoApiProvider] ✗ Brevo HTTP API dispatch failed: {}", e.getMessage(), e);
            throw new MailSendException("Brevo HTTP API delivery failed: " + e.getMessage(), e);
        }
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
