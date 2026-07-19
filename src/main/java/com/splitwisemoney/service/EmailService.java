package com.splitwisemoney.service;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.repository.GroupInvitationRepository;
import com.splitwisemoney.service.provider.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Higher-level Email Service orchestrating non-blocking dispatches, automatic retries,
 * delivery audit tracking in PostgreSQL, and multi-provider failover.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProvider emailProvider;
    private final GroupInvitationRepository invitationRepository;

    public EmailService(@Qualifier("compositeFallbackEmailProvider") EmailProvider emailProvider,
                        GroupInvitationRepository invitationRepository) {
        this.emailProvider = emailProvider;
        this.invitationRepository = invitationRepository;
    }

    @PostConstruct
    public void startupLog() {
        log.info("[EmailService] Initialized with primary provider router: {}", emailProvider.getProviderName());
        log.info("[EmailService] Email dispatches will use multi-provider fallback & exponential backoff retries.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API Methods with Retry & Async Tracking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dispatch invitation email for existing registered user.
     * Retries up to 3 times with exponential backoff (1s, 2s, 4s).
     */
    @Async
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    public void sendExistingUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        log.info("[EmailService] Dispatching existing user invite -> id={}, to={}", invitation.getId(), invitation.getInviteeEmail());
        updateDeliveryStatus(invitation, "SENDING", null);

        emailProvider.sendExistingUserInvitation(
                invitation.getInviteeEmail(), inviterName, groupName,
                invitation.getInvitationToken(), invitation.getExpiresAt()
        );

        updateDeliveryStatus(invitation, "SENT", null);
        log.info("[EmailService] ✓ Invitation email id={} delivered successfully", invitation.getId());
    }

    /**
     * Dispatch invitation email for non-registered email.
     * Retries up to 3 times with exponential backoff (1s, 2s, 4s).
     */
    @Async
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    public void sendNewUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        log.info("[EmailService] Dispatching new user invite -> id={}, to={}", invitation.getId(), invitation.getInviteeEmail());
        updateDeliveryStatus(invitation, "SENDING", null);

        emailProvider.sendNewUserInvitation(
                invitation.getInviteeEmail(), inviterName, groupName,
                invitation.getInvitationToken(), invitation.getExpiresAt()
        );

        updateDeliveryStatus(invitation, "SENT", null);
        log.info("[EmailService] ✓ New user invitation email id={} delivered successfully", invitation.getId());
    }

    /**
     * Recover handler invoked when all 3 retry attempts fail.
     * Updates PostgreSQL record status to FAILED and stores exact error message.
     */
    @Recover
    @Transactional
    public void recoverInvitationEmailFailure(Exception ex, GroupInvitation invitation, String inviterName, String groupName) {
        String rootMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        log.error("[EmailService] ✗ All 3 retry attempts failed for invitation id={}. Error: {}",
                invitation.getId(), rootMsg);
        updateDeliveryStatus(invitation, "FAILED", rootMsg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synchronous Overloads (bypasses invitation record, e.g. for notifications/test)
    // ─────────────────────────────────────────────────────────────────────────

    public void sendInvitationAccepted(String toEmail, String accepterName, String groupName) {
        emailProvider.sendInvitationAccepted(toEmail, accepterName, groupName);
    }

    public void sendInvitationRejected(String toEmail, String rejectorName, String groupName) {
        emailProvider.sendInvitationRejected(toEmail, rejectorName, groupName);
    }

    public Map<String, Object> sendTestEmail(String toEmail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("to", toEmail);
        result.put("activeProvider", emailProvider.getProviderName());
        try {
            emailProvider.sendTestEmail(toEmail);
            result.put("success", true);
            result.put("verdict", "✓ Test email accepted by active provider (" + emailProvider.getProviderName() + ")");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("verdict", "✗ Test email failed: " + e.getMessage());
            throw e;
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updateDeliveryStatus(GroupInvitation inv, String status, String error) {
        try {
            inv.setEmailDeliveryStatus(status);
            inv.setEmailDeliveryError(error);
            inv.setEmailLastAttemptAt(LocalDateTime.now());
            invitationRepository.save(inv);
        } catch (Exception e) {
            log.warn("[EmailService] Failed to update delivery status for invitation id={}: {}", inv.getId(), e.getMessage());
        }
    }
}
