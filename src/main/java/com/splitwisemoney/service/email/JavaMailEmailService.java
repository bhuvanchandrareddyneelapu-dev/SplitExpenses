package com.splitwisemoney.service.email;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.exception.SmtpDeliveryException;
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

import java.time.LocalDateTime;

@Service
public class JavaMailEmailService {

    private static final Logger log = LoggerFactory.getLogger(JavaMailEmailService.class);

    private final EmailProvider emailProvider;
    private final GroupInvitationRepository invitationRepository;

    public JavaMailEmailService(@Qualifier("compositeFallbackEmailProvider") EmailProvider emailProvider,
                                GroupInvitationRepository invitationRepository) {
        this.emailProvider = emailProvider;
        this.invitationRepository = invitationRepository;
    }

    /**
     * Dispatch registered user invitation email with up to 3 retries.
     */
    @Async
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    public void sendRegisteredUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        log.info("[JavaMailEmailService] [INVITE CREATED] Preparing registered user invitation -> token={}, to={}",
                invitation.getInvitationToken(), invitation.getInviteeEmail());

        updateDeliveryStatus(invitation.getId(), "SENDING", null);

        emailProvider.sendExistingUserInvitation(
                invitation.getInviteeEmail(), inviterName, groupName,
                invitation.getInvitationToken(), invitation.getExpiresAt()
        );

        updateDeliveryStatus(invitation.getId(), "SENT", null);
        log.info("[JavaMailEmailService] [EMAIL SENT] ✓ Invitation email successfully sent to {}", invitation.getInviteeEmail());
    }

    /**
     * Dispatch new unregistered user invitation email with up to 3 retries.
     */
    @Async
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @Transactional
    public void sendUnregisteredUserInvitation(GroupInvitation invitation, String inviterName, String groupName) {
        log.info("[JavaMailEmailService] [INVITE CREATED] Preparing signup invitation -> token={}, to={}",
                invitation.getInvitationToken(), invitation.getInviteeEmail());

        updateDeliveryStatus(invitation.getId(), "SENDING", null);

        emailProvider.sendNewUserInvitation(
                invitation.getInviteeEmail(), inviterName, groupName,
                invitation.getInvitationToken(), invitation.getExpiresAt()
        );

        updateDeliveryStatus(invitation.getId(), "SENT", null);
        log.info("[JavaMailEmailService] [EMAIL SENT] ✓ Signup invitation email successfully sent to {}", invitation.getInviteeEmail());
    }

    /**
     * Recovery logic executed if all 3 retry attempts fail.
     */
    @Recover
    @Transactional
    public void recoverEmailFailure(Exception ex, GroupInvitation invitation, String inviterName, String groupName) {
        String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        log.error("[JavaMailEmailService] [EMAIL FAILED] ✗ All 3 attempts failed for invitation token={}. Error: {}",
                invitation.getInvitationToken(), errorMsg);

        updateDeliveryStatus(invitation.getId(), "FAILED", errorMsg);
        throw new SmtpDeliveryException("Unable to send invitation email after 3 retry attempts: " + errorMsg, ex);
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
            log.warn("[JavaMailEmailService] Failed to update delivery status: {}", e.getMessage());
        }
    }
}
