package com.splitwisemoney.service.email;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.repository.GroupInvitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class InvitationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvitationScheduler.class);

    private final GroupInvitationRepository invitationRepository;

    public InvitationScheduler(GroupInvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    /**
     * Daily scheduled task running at 02:00 AM to sweep and mark expired pending invitations.
     */
    @Scheduled(cron = "${app.invitation.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupExpiredInvitations() {
        log.info("[InvitationScheduler] Starting daily cleanup sweep for expired invitations...");

        List<GroupInvitation> pendingInvitations = invitationRepository.findAll()
                .stream()
                .filter(inv -> "PENDING".equals(inv.getStatus()))
                .filter(GroupInvitation::isExpired)
                .toList();

        int expiredCount = 0;
        for (GroupInvitation invitation : pendingInvitations) {
            invitation.setStatus("EXPIRED");
            invitationRepository.save(invitation);
            expiredCount++;
            log.info("[InvitationScheduler] [INVITATION EXPIRED] Marked invitation id={}, token={}, inviteeEmail={} as EXPIRED",
                    invitation.getId(), invitation.getInvitationToken(), invitation.getInviteeEmail());
        }

        log.info("[InvitationScheduler] Daily invitation cleanup sweep finished. Total expired invitations updated: {}", expiredCount);
    }
}
