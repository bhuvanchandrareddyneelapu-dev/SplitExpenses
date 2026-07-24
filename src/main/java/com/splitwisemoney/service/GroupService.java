package com.splitwisemoney.service;

import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.GroupMember;
import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.repository.GroupMemberRepository;
import com.splitwisemoney.repository.GroupRepository;
import com.splitwisemoney.repository.GroupInvitationRepository;
import com.splitwisemoney.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.splitwisemoney.exception.InvalidTokenException;
import com.splitwisemoney.exception.InvitationExpiredException;
import com.splitwisemoney.exception.ResourceConflictException;
import com.splitwisemoney.service.email.InvitationTokenService;
import com.splitwisemoney.service.email.JavaMailEmailService;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final InvitationTokenService invitationTokenService;
    private final JavaMailEmailService javaMailEmailService;

    @PersistenceContext
    private EntityManager entityManager;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        GroupInvitationRepository groupInvitationRepository,
                        ActivityLogService activityLogService,
                        NotificationService notificationService,
                        EmailService emailService,
                        InvitationTokenService invitationTokenService,
                        JavaMailEmailService javaMailEmailService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.invitationTokenService = invitationTokenService;
        this.javaMailEmailService = javaMailEmailService;
    }

    @Transactional
    public Group createGroup(String groupName, User creator) {
        Group group = new Group(groupName, creator);
        Group savedGroup = groupRepository.save(group);

        // Add creator as member
        GroupMember member = new GroupMember(savedGroup, creator);
        groupMemberRepository.save(member);

        activityLogService.log(creator, "Created group: " + groupName);
        notificationService.createNotification(creator, "GROUP_INVITE", "You created the group " + groupName);

        return savedGroup;
    }

    @Transactional
    public Group editGroup(Long groupId, String groupName, User user) {
        Group group = groupRepository.findByIdWithCreator(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new IllegalArgumentException("Only group members can edit the group.");
        }

        group.setGroupName(groupName);
        Group updated = groupRepository.save(group);

        activityLogService.log(user, "Renamed group to: " + groupName);
        return updated;
    }

    @Transactional
    public void deleteGroup(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (group.getCreatedBy() == null || !group.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Only the group creator can delete the group.");
        }

        groupRepository.delete(group);
        activityLogService.log(user, "Deleted group: " + group.getGroupName());
    }

    @Transactional
    public GroupMember addMemberByEmail(Long groupId, String email, User actor) {
        validateEmailFormat(email);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can add members to the group.");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            User userToAdd = existingUser.get();
            if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userToAdd.getId())) {
                throw new ResourceConflictException("User is already a member of this group");
            }

            Optional<GroupInvitation> existingInv = groupInvitationRepository.findByGroupIdAndInviteeEmail(groupId, email);
            if (existingInv.isPresent() && "PENDING".equals(existingInv.get().getStatus()) && !existingInv.get().isExpired()) {
                throw new ResourceConflictException("An invitation is already pending for this email");
            }

            // Create GroupMember record & GroupInvitation
            GroupMember groupMember = new GroupMember(group, userToAdd);
            GroupMember saved = groupMemberRepository.save(groupMember);

            GroupInvitation invitation = createAndSaveInvitation(group, actor, userToAdd, email);
            javaMailEmailService.sendRegisteredUserInvitation(invitation, actor.getFullName(), group.getGroupName());

            activityLogService.log(actor, "Added " + userToAdd.getFullName() + " to group " + group.getGroupName());
            notificationService.createNotification(userToAdd, "GROUP_INVITE", actor.getFullName() + " added you to the group " + group.getGroupName());

            return saved;
        } else {
            Optional<GroupInvitation> existingInv = groupInvitationRepository.findByGroupIdAndInviteeEmail(groupId, email);
            if (existingInv.isPresent() && "PENDING".equals(existingInv.get().getStatus()) && !existingInv.get().isExpired()) {
                throw new ResourceConflictException("An invitation is already pending for this email");
            }

            GroupInvitation invitation = createAndSaveInvitation(group, actor, null, email);
            javaMailEmailService.sendUnregisteredUserInvitation(invitation, actor.getFullName(), group.getGroupName());
            return null;
        }
    }

    @Transactional
    public void removeMember(Long groupId, Long userId, User actor) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this group"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(actor.getId());
        boolean isSelf = userId.equals(actor.getId());

        if (!isCreator && !isSelf) {
            throw new IllegalArgumentException("Only the group creator can remove other members.");
        }

        groupMemberRepository.delete(member);

        activityLogService.log(actor, "Removed " + userToRemove.getFullName() + " from group " + group.getGroupName());
        notificationService.createNotification(userToRemove, "GROUP_INVITE", "You were removed from the group " + group.getGroupName());
    }

    @Transactional(readOnly = true)
    public List<Group> getUserGroups(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUserIdWithGroupAndCreator(userId);
        return memberships.stream()
                .map(GroupMember::getGroup)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<User> getGroupMembers(Long groupId) {
        return groupMemberRepository.findByGroupIdWithUser(groupId).stream()
                .map(GroupMember::getUser)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<Group> getGroupById(Long groupId) {
        return groupRepository.findByIdWithCreator(groupId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long groupId, Long userId) {
        return groupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Invitation Flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invite a user (registered or not) to a group by email.
     * <ul>
     *   <li>If the email belongs to an existing user: creates invitation, sends email, sends in-app notification.</li>
     *   <li>If the email is unknown: creates invitation without a receiver, sends a registration-link email.</li>
     * </ul>
     *
     * @return the saved {@link GroupInvitation}
     */
    @Transactional
    public GroupInvitation inviteMemberByEmail(Long groupId, String email, User actor) {
        validateEmailFormat(email);

        log.info("[GroupService] Entering inviteMemberByEmail() — groupId={}, inviteeEmail={}, actor={}",
                 groupId, email, actor.getEmail());

        Group group = groupRepository.findByIdWithCreator(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can invite others to the group.");
        }

        if (actor.getEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("You cannot invite yourself.");
        }

        Optional<User> existingUser = userRepository.findByEmail(email);

        existingUser.ifPresent(u -> {
            if (groupMemberRepository.existsByGroupIdAndUserId(groupId, u.getId())) {
                throw new ResourceConflictException("User is already a member of this group");
            }
        });

        Optional<GroupInvitation> existing = groupInvitationRepository.findByGroupIdAndInviteeEmail(groupId, email);
        if (existing.isPresent()) {
            GroupInvitation inv = existing.get();
            if ("PENDING".equals(inv.getStatus()) && !inv.isExpired()) {
                throw new ResourceConflictException("An invitation is already pending for this email");
            }

            String newToken = invitationTokenService.generateToken();
            inv.setStatus("PENDING");
            inv.setSender(actor);
            inv.setRespondedAt(null);
            inv.setInvitationToken(newToken);
            inv.setTokenHash(invitationTokenService.hashToken(newToken));
            inv.setExpiresAt(invitationTokenService.calculateExpiryTime());
            existingUser.ifPresent(inv::setReceiver);
            GroupInvitation saved = groupInvitationRepository.save(inv);
            entityManager.flush();
            sendInvitationEmails(saved, actor, group, existingUser);
            return saved;
        }

        String token = invitationTokenService.generateToken();
        GroupInvitation invitation;
        if (existingUser.isPresent()) {
            invitation = new GroupInvitation(group, actor, existingUser.get());
        } else {
            invitation = new GroupInvitation(group, actor, email);
        }
        invitation.setInvitationToken(token);
        invitation.setTokenHash(invitationTokenService.hashToken(token));
        invitation.setExpiresAt(invitationTokenService.calculateExpiryTime());

        GroupInvitation saved = groupInvitationRepository.save(invitation);
        entityManager.flush();
        sendInvitationEmails(saved, actor, group, existingUser);
        return saved;
    }

    public GroupInvitation createAndSaveInvitation(Group group, User actor, User targetUser, String email) {
        String token = invitationTokenService.generateToken();
        GroupInvitation invitation;
        if (targetUser != null) {
            invitation = new GroupInvitation(group, actor, targetUser);
        } else {
            invitation = new GroupInvitation(group, actor, email);
        }
        invitation.setInvitationToken(token);
        invitation.setTokenHash(invitationTokenService.hashToken(token));
        invitation.setExpiresAt(invitationTokenService.calculateExpiryTime());
        GroupInvitation saved = groupInvitationRepository.save(invitation);
        entityManager.flush();
        return saved;
    }

    private static final Set<String> DISALLOWED_BOUNCE_DOMAINS = Set.of(
            "invalid.com", "fake.com", "tempmail.com", "dispostable.com", "mailinator.com", "10minutemail.com"
    );

    private void validateEmailFormat(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        String cleanedEmail = email.trim().toLowerCase();

        if (!org.apache.commons.validator.routines.EmailValidator.getInstance().isValid(cleanedEmail)) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        String domain = cleanedEmail.substring(cleanedEmail.indexOf('@') + 1);
        if (DISALLOWED_BOUNCE_DOMAINS.contains(domain)) {
            throw new IllegalArgumentException("Cannot send invitation to invalid or bounce-prone domain: " + domain);
        }
    }

    private void sendInvitationEmails(GroupInvitation inv, User actor, Group group, Optional<User> existingUser) {
        log.info("[GroupService] Dispatching invitation email via EmailService — inviteeEmail={} existingUser={}",
                 inv.getInviteeEmail(), existingUser.isPresent());

        if (existingUser.isPresent()) {
            emailService.sendExistingUserInvitation(inv, actor.getFullName(), group.getGroupName());
            notificationService.createNotification(
                    existingUser.get(),
                    "GROUP_INVITE",
                    actor.getFullName() + " invited you to join the group \"" + group.getGroupName() + "\". Click to view.");
        } else {
            emailService.sendNewUserInvitation(inv, actor.getFullName(), group.getGroupName());
        }
        activityLogService.log(actor, "Invited " + inv.getInviteeEmail() + " to join " + group.getGroupName());
    }


    /**
     * Explicitly resend the invitation email for an existing PENDING invitation.
     * Useful when the original email was not delivered.
     */
    @Transactional
    public GroupInvitation resendInvitation(Long groupId, String email, User actor) {
        Group group = groupRepository.findByIdWithCreator(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can resend invitations.");
        }

        GroupInvitation inv = groupInvitationRepository.findByGroupIdAndInviteeEmail(groupId, email)
                .orElseThrow(() -> new IllegalArgumentException("No invitation found for " + email + " in this group."));

        if (!"PENDING".equals(inv.getStatus())) {
            throw new IllegalArgumentException("Cannot resend: invitation status is " + inv.getStatus() + ".");
        }

        if (inv.isExpired()) {
            // Extend expiry and generate a fresh token
            inv.setInvitationToken(UUID.randomUUID().toString());
            inv.setExpiresAt(LocalDateTime.now().plusDays(7));
            groupInvitationRepository.save(inv);
            entityManager.flush();
        }

        Optional<User> existingUser = userRepository.findByEmail(email);
        sendInvitationEmails(inv, actor, group, existingUser);
        return inv;
    }

    @Transactional(readOnly = true)
    public List<GroupInvitation> getPendingInvitations(Long userId) {
        return groupInvitationRepository.findByReceiverIdAndStatus(userId, "PENDING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token-based Accept / Reject
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns invitation details for a given token (used by the frontend invite page).
     * Does not require the user to be the receiver — they just need to be logged in.
     */
    @Transactional
    public GroupInvitation getInvitationByToken(String token) {
        GroupInvitation invitation = groupInvitationRepository.findByInvitationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invitation not found or invalid token."));
        if (invitation.isExpired() && "PENDING".equals(invitation.getStatus())) {
            invitation.setStatus("EXPIRED");
            groupInvitationRepository.save(invitation);
        }
        return invitation;
    }

    @Transactional
    public GroupInvitation acceptInvitationByToken(String token, User user) {
        GroupInvitation invitation = groupInvitationRepository.findByInvitationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invitation not found or invalid token."));

        if ("ACCEPTED".equals(invitation.getStatus())) {
            if (!groupMemberRepository.existsByGroupIdAndUserId(invitation.getGroup().getId(), user.getId())) {
                groupMemberRepository.save(new GroupMember(invitation.getGroup(), user));
            }
            return invitation;
        }

        validatePendingInvitation(invitation);
        validateInviteeEmail(invitation, user);

        invitation.setStatus("ACCEPTED");
        invitation.setReceiver(user);
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setAcceptedAt(LocalDateTime.now());
        GroupInvitation saved = groupInvitationRepository.save(invitation);

        // Add as group member if not already
        if (!groupMemberRepository.existsByGroupIdAndUserId(invitation.getGroup().getId(), user.getId())) {
            groupMemberRepository.save(new GroupMember(invitation.getGroup(), user));
        }

        activityLogService.log(user, "Accepted invitation to join " + invitation.getGroup().getGroupName());
        notificationService.createNotification(invitation.getSender(), "INVITE_ACCEPTED",
                user.getFullName() + " accepted your invitation to join \"" + invitation.getGroup().getGroupName() + "\".");
        emailService.sendInvitationAccepted(
                invitation.getSender().getEmail(), user.getFullName(), invitation.getGroup().getGroupName());

        return saved;
    }

    @Transactional
    public GroupInvitation rejectInvitationByToken(String token, User user) {
        GroupInvitation invitation = groupInvitationRepository.findByInvitationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invitation not found or invalid token."));

        if ("REJECTED".equals(invitation.getStatus())) {
            return invitation;
        }

        validatePendingInvitation(invitation);
        if (user != null) {
            validateInviteeEmail(invitation, user);
            invitation.setReceiver(user);
        }

        invitation.setStatus("REJECTED");
        invitation.setRespondedAt(LocalDateTime.now());
        GroupInvitation saved = groupInvitationRepository.save(invitation);

        String responderName = (user != null) ? user.getFullName() : invitation.getInviteeEmail();
        if (user != null) {
            activityLogService.log(user, "Declined invitation to join " + invitation.getGroup().getGroupName());
        }
        notificationService.createNotification(invitation.getSender(), "INVITE_REJECTED",
                responderName + " declined your invitation to join \"" + invitation.getGroup().getGroupName() + "\".");
        emailService.sendInvitationRejected(
                invitation.getSender().getEmail(), responderName, invitation.getGroup().getGroupName());

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy ID-based Accept / Reject (kept for backward compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void acceptInvitation(Long invitationId, User user) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvalidTokenException("Invitation not found"));

        if (invitation.getReceiver() != null && !invitation.getReceiver().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }
        validatePendingInvitation(invitation);

        invitation.setStatus("ACCEPTED");
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setAcceptedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);

        if (!groupMemberRepository.existsByGroupIdAndUserId(invitation.getGroup().getId(), user.getId())) {
            groupMemberRepository.save(new GroupMember(invitation.getGroup(), user));
        }

        activityLogService.log(user, "Accepted invitation to join " + invitation.getGroup().getGroupName());
        notificationService.createNotification(invitation.getSender(), "INVITE_ACCEPTED",
                user.getFullName() + " accepted your invitation to join " + invitation.getGroup().getGroupName());
    }

    @Transactional
    public void rejectInvitation(Long invitationId, User user) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvalidTokenException("Invitation not found"));

        if (invitation.getReceiver() != null && !invitation.getReceiver().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }
        validatePendingInvitation(invitation);

        invitation.setStatus("REJECTED");
        invitation.setRespondedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);

        activityLogService.log(user, "Rejected invitation to join " + invitation.getGroup().getGroupName());
        notificationService.createNotification(invitation.getSender(), "INVITE_REJECTED",
                user.getFullName() + " rejected your invitation to join " + invitation.getGroup().getGroupName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validatePendingInvitation(GroupInvitation inv) {
        if (!"PENDING".equals(inv.getStatus())) {
            throw new InvitationExpiredException("This invitation has already been " + inv.getStatus().toLowerCase() + ".");
        }
        if (inv.isExpired()) {
            inv.setStatus("EXPIRED");
            groupInvitationRepository.save(inv);
            throw new InvitationExpiredException("This invitation has expired.");
        }
    }

    /** Verifies that the acting user's email matches the invitation's invitee email. */
    private void validateInviteeEmail(GroupInvitation inv, User user) {
        if (inv.getInviteeEmail() != null && !inv.getInviteeEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("This invitation was sent to a different email address.");
        }
    }

    /**
     * Called after a new user registers: finds pending invitations for their email
     * and auto-accepts them (adds them to the group).
     */
    @Transactional
    public Long autoAcceptPendingInvitations(User newUser) {
        List<GroupInvitation> pending = groupInvitationRepository
                .findByInviteeEmailAndStatus(newUser.getEmail(), "PENDING");

        Long firstAcceptedGroupId = null;

        for (GroupInvitation inv : pending) {
            if (inv.isExpired()) {
                inv.setStatus("EXPIRED");
                groupInvitationRepository.save(inv);
                continue;
            }
            if (!groupMemberRepository.existsByGroupIdAndUserId(inv.getGroup().getId(), newUser.getId())) {
                groupMemberRepository.save(new GroupMember(inv.getGroup(), newUser));
            }
            inv.setStatus("ACCEPTED");
            inv.setReceiver(newUser);
            inv.setRespondedAt(LocalDateTime.now());
            inv.setAcceptedAt(LocalDateTime.now());
            groupInvitationRepository.save(inv);

            if (firstAcceptedGroupId == null) {
                firstAcceptedGroupId = inv.getGroup().getId();
            }

            notificationService.createNotification(newUser, "GROUP_INVITE",
                    "You were automatically added to \"" + inv.getGroup().getGroupName() + "\" based on an invitation.");
            notificationService.createNotification(inv.getSender(), "INVITE_ACCEPTED",
                    newUser.getFullName() + " registered and joined \"" + inv.getGroup().getGroupName() + "\".");
        }

        return firstAcceptedGroupId;
    }
}
