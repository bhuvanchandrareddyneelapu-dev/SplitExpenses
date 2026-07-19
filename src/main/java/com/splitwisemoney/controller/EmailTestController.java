package com.splitwisemoney.controller;

import com.splitwisemoney.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Diagnostic endpoint for verifying SMTP connectivity independently
 * of the invitation flow.
 *
 * <p>Usage:
 * <pre>POST /api/test/email?to=your@gmail.com</pre>
 *
 * <p>This endpoint bypasses all group/invitation logic. If it fails,
 * the problem is SMTP configuration — not the invitation feature.
 * If it succeeds, the problem is in the invitation flow.
 *
 * <p>Requires authentication (Bearer token). Remove once debugging is done.
 */
@RestController
@RequestMapping("/api/test")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Diagnostics", description = "SMTP and configuration diagnostic endpoints")
public class EmailTestController {

    private static final Logger log = LoggerFactory.getLogger(EmailTestController.class);

    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Sends a bare plain-text email directly via SMTP, bypassing all invitation logic.
     *
     * <p>Success response (200):
     * <pre>{ "success": true, "verdict": "✓ Email successfully accepted by Gmail SMTP.", ... }</pre>
     *
     * <p>Failure response (502):
     * <pre>{ "success": false, "verdict": "✗ Email rejected by Gmail. Reason: ...", ... }</pre>
     *
     * @param to  recipient email address (query param)
     */
    @PostMapping("/email")
    @Operation(summary = "Send a direct SMTP test email, bypassing all invitation logic")
    public ResponseEntity<?> sendTestEmail(@RequestParam String to) {
        log.info("[EmailTestController] POST /api/test/email — to={}", to);

        try {
            Map<String, Object> result = emailService.sendTestEmail(to);
            boolean success = Boolean.TRUE.equals(result.get("success"));

            if (success) {
                log.info("[EmailTestController] Test email accepted by SMTP. to={}", to);
                return ResponseEntity.ok(result);
            } else {
                // Mail not configured (returned without throwing)
                log.warn("[EmailTestController] Mail not configured — test skipped. to={}", to);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
            }

        } catch (MailException e) {
            String rootMsg = e.getMostSpecificCause() != null
                             ? e.getMostSpecificCause().getMessage()
                             : e.getMessage();
            log.error("[EmailTestController] ✗ SMTP TEST FAILED for to={}: {}", to, rootMsg, e);

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success",   false,
                    "to",        to,
                    "verdict",   "✗ Email rejected by Gmail. Reason: " + rootMsg,
                    "error",     e.getMessage(),
                    "rootCause", rootMsg,
                    "smtpError", true
            ));

        } catch (RuntimeException e) {
            log.error("[EmailTestController] ✗ Unexpected error during SMTP test for to={}: {}", to, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "to",      to,
                    "verdict", "✗ EmailService threw an unexpected exception: " + e.getMessage(),
                    "error",   e.getMessage()
            ));
        }
    }
}
