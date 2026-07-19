package com.splitwisemoney.controller;

import com.splitwisemoney.service.EmailService;
import com.splitwisemoney.service.SmtpDiagnosticService;
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
 * Diagnostic endpoint for verifying SMTP connectivity, DNS, TCP probes, and
 * sending test emails independently of the invitation flow.
 */
@RestController
@RequestMapping("/api/test")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Diagnostics", description = "SMTP and configuration diagnostic endpoints")
public class EmailTestController {

    private static final Logger log = LoggerFactory.getLogger(EmailTestController.class);

    private final EmailService emailService;
    private final SmtpDiagnosticService smtpDiagnosticService;

    public EmailTestController(EmailService emailService, SmtpDiagnosticService smtpDiagnosticService) {
        this.emailService = emailService;
        this.smtpDiagnosticService = smtpDiagnosticService;
    }

    /**
     * Executes a comprehensive audit of DNS resolution, TCP socket connectivity (587 & 465),
     * SMTP handshake banner, environment details, and resolved mail properties.
     *
     * <p>Usage: {@code GET /api/test/smtp}
     */
    @GetMapping("/smtp")
    @Operation(summary = "Perform deep diagnostic audit of DNS, TCP socket connectivity, and SMTP banner")
    public ResponseEntity<Map<String, Object>> getSmtpDiagnostic() {
        log.info("[EmailTestController] GET /api/test/smtp — executing deep SMTP audit");
        Map<String, Object> report = smtpDiagnosticService.performDiagnostic();
        boolean healthy = Boolean.TRUE.equals(report.get("healthy"));
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(report);
    }

    /**
     * Sends a bare plain-text email directly via SMTP, bypassing all invitation logic.
     *
     * <p>Usage: {@code POST /api/test/email?to=your@gmail.com}
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
                    "verdict",   "✗ Email rejected by mail server. Reason: " + rootMsg,
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
