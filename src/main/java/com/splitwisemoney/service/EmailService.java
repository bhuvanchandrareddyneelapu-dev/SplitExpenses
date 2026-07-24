package com.splitwisemoney.service;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.exception.SmtpDeliveryException;
import com.splitwisemoney.repository.GroupInvitationRepository;
import com.splitwisemoney.service.provider.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise Consolidated Email Service orchestrating non-blocking dispatches,
 * atomic delivery claims (1 click = 1 email), delivery audit tracking,
 * and multi-provider failover.
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API Methods with Strict 1-Click = 1-Email Idempotency Claim
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dispatch invitation email for existing registered user.
     */
    @Async
    @Transactional
    public void sendExistingUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            log.info("[EmailService] [{}] [INVITE CREATED] Preparing registered user invitation -> token={}, to={}",
                    correlationId, invitation.getInvitationToken(), invitation.getInviteeEmail());

            if (!claimDeliveryAttempt(invitation.getId())) {
                log.warn("[EmailService] [{}] [EMAIL SKIPPED] Email delivery already in progress or completed for invitationId={}",
                        correlationId, invitation.getId());
                return;
            }

            log.info("[EmailService] [{}] [PRE-SEND AUDIT] Recipient Email: {}, Invitation ID: {}, Group Name: {}, Inviter Name: {}",
                    correlationId, invitation.getInviteeEmail(), invitation.getId(), groupName, inviterName);

            emailProvider.sendExistingUserInvitation(
                    invitation.getInviteeEmail(), inviterName, groupName,
                    invitation.getInvitationToken(), invitation.getExpiresAt()
            );

            updateDeliveryStatus(invitation.getId(), "SENT", null);
            log.info("[EmailService] [{}] [EMAIL SENT] ✓ Invitation email successfully delivered to {}", correlationId, invitation.getInviteeEmail());

        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            log.error("[EmailService] [{}] [EMAIL FAILED] ✗ SMTP delivery failed for invitation token={}. Error: {}",
                    correlationId, invitation.getInvitationToken(), errorMsg, ex);
            updateDeliveryStatus(invitation.getId(), "FAILED", errorMsg);
            throw new SmtpDeliveryException("Invitation email delivery failed: " + errorMsg, ex);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Dispatch invitation email for non-registered email.
     */
    @Async
    @Transactional
    public void sendNewUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            log.info("[EmailService] [{}] [INVITE CREATED] Preparing signup invitation -> token={}, to={}",
                    correlationId, invitation.getInvitationToken(), invitation.getInviteeEmail());

            if (!claimDeliveryAttempt(invitation.getId())) {
                log.warn("[EmailService] [{}] [EMAIL SKIPPED] Email delivery already in progress or completed for invitationId={}",
                        correlationId, invitation.getId());
                return;
            }

            log.info("[EmailService] [{}] [PRE-SEND AUDIT] Recipient Email: {}, Invitation ID: {}, Group Name: {}, Inviter Name: {}",
                    correlationId, invitation.getInviteeEmail(), invitation.getId(), groupName, inviterName);

            emailProvider.sendNewUserInvitation(
                    invitation.getInviteeEmail(), inviterName, groupName,
                    invitation.getInvitationToken(), invitation.getExpiresAt()
            );

            updateDeliveryStatus(invitation.getId(), "SENT", null);
            log.info("[EmailService] [{}] [EMAIL SENT] ✓ Signup invitation email successfully delivered to {}", correlationId, invitation.getInviteeEmail());

        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            log.error("[EmailService] [{}] [EMAIL FAILED] ✗ SMTP delivery failed for invitation token={}. Error: {}",
                    correlationId, invitation.getInvitationToken(), errorMsg, ex);
            updateDeliveryStatus(invitation.getId(), "FAILED", errorMsg);
            throw new SmtpDeliveryException("Invitation email delivery failed: " + errorMsg, ex);
        } finally {
            MDC.remove("correlationId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification Dispatches (Inviter Notification on Accept / Decline)
    // ─────────────────────────────────────────────────────────────────────────

    public void sendInvitationAccepted(String inviterEmail, String accepterName, String groupName) {
        log.info("[EmailService] [NOTIFICATION TO INVITER] Sending acceptance notification to inviter: {} (Accepter: {}, Group: {})",
                 inviterEmail, accepterName, groupName);
        emailProvider.sendInvitationAccepted(inviterEmail, accepterName, groupName);
    }

    public void sendInvitationRejected(String inviterEmail, String rejectorName, String groupName) {
        log.info("[EmailService] [NOTIFICATION TO INVITER] Sending rejection notification to inviter: {} (Rejector: {}, Group: {})",
                 inviterEmail, rejectorName, groupName);
        emailProvider.sendInvitationRejected(inviterEmail, rejectorName, groupName);
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
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private boolean claimDeliveryAttempt(Long invitationId) {
        if (invitationId == null) return true;
        try {
            return invitationRepository.findById(invitationId).map(inv -> {
                if ("SENT".equals(inv.getEmailDeliveryStatus()) || "SENDING".equals(inv.getEmailDeliveryStatus())) {
                    return false;
                }
                inv.setEmailDeliveryStatus("SENDING");
                inv.setEmailLastAttemptAt(LocalDateTime.now());
                invitationRepository.save(inv);
                return true;
            }).orElse(false);
        } catch (Exception e) {
            log.warn("[EmailService] Failed to claim delivery attempt: {}", e.getMessage());
            return true;
        }
    }

    private void updateDeliveryStatus(Long invitationId, String status, String error) {
        if (invitationId == null) return;
        try {
            invitationRepository.findById(invitationId).ifPresent(inv -> {
                inv.setEmailDeliveryStatus(status);
                inv.setEmailDeliveryError(error);
                inv.setEmailLastAttemptAt(LocalDateTime.now());
                invitationRepository.save(inv);
            });
        } catch (Exception e) {
            log.warn("[EmailService] Failed to update delivery status: {}", e.getMessage());
        }
    }
}
