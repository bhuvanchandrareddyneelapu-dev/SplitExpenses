package com.splitwisemoney.service;

import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.GroupMember;
import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.repository.GroupMemberRepository;
import com.splitwisemoney.repository.GroupRepository;
import com.splitwisemoney.repository.GroupInvitationRepository;
import com.splitwisemoney.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupService {



    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        GroupInvitationRepository groupInvitationRepository,
                        ActivityLogService activityLogService,
                        NotificationService notificationService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
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
        Group group = groupRepository.findById(groupId)
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
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can add members to the group.");
        }

        User userToAdd = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User with email " + email + " does not exist"));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userToAdd.getId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember groupMember = new GroupMember(group, userToAdd);
        GroupMember saved = groupMemberRepository.save(groupMember);

        activityLogService.log(actor, "Added " + userToAdd.getFullName() + " to group " + group.getGroupName());
        notificationService.createNotification(userToAdd, "GROUP_INVITE", actor.getFullName() + " added you to the group " + group.getGroupName());

        return saved;
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

    /**
     * Returns all groups for the given user with their createdBy eagerly loaded,
     * preventing LazyInitializationException when the controller maps the response
     * outside this transaction boundary.
     */
    @Transactional(readOnly = true)
    public List<Group> getUserGroups(Long userId) {
        // Use the JOIN FETCH query so group.createdBy is loaded within this transaction
        List<GroupMember> memberships = groupMemberRepository.findByUserIdWithGroupAndCreator(userId);
        return memberships.stream()
                .map(GroupMember::getGroup)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<User> getGroupMembers(Long groupId) {
        return groupMemberRepository.findByGroupId(groupId).stream()
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

    @Transactional
    public GroupInvitation inviteMemberByEmail(Long groupId, String email, User actor) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can invite members to the group.");
        }

        User userToInvite = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User with email " + email + " does not exist"));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userToInvite.getId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        Optional<GroupInvitation> existing = groupInvitationRepository.findByGroupIdAndReceiverId(groupId, userToInvite.getId());
        if (existing.isPresent()) {
            GroupInvitation invitation = existing.get();
            if ("PENDING".equals(invitation.getStatus())) {
                throw new IllegalArgumentException("An invitation is already pending for this user");
            }
            invitation.setStatus("PENDING");
            invitation.setSender(actor);
            invitation.setRespondedAt(null);
            return groupInvitationRepository.save(invitation);
        }

        GroupInvitation invitation = new GroupInvitation(group, actor, userToInvite);
        GroupInvitation saved = groupInvitationRepository.save(invitation);

        activityLogService.log(actor, "Invited " + userToInvite.getFullName() + " to join " + group.getGroupName());
        notificationService.createNotification(userToInvite, "GROUP_INVITATION", actor.getFullName() + " invited you to join the group " + group.getGroupName());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<GroupInvitation> getPendingInvitations(Long userId) {
        return groupInvitationRepository.findByReceiverIdAndStatus(userId, "PENDING");
    }

    @Transactional
    public void acceptInvitation(Long invitationId, User user) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getReceiver().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new IllegalArgumentException("Invitation is not pending");
        }

        invitation.setStatus("ACCEPTED");
        invitation.setRespondedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);

        // Add user as a member of the group
        GroupMember groupMember = new GroupMember(invitation.getGroup(), user);
        groupMemberRepository.save(groupMember);

        activityLogService.log(user, "Accepted invitation to join " + invitation.getGroup().getGroupName());
        notificationService.createNotification(invitation.getSender(), "GROUP_ACCEPTED", user.getFullName() + " accepted your invitation to join " + invitation.getGroup().getGroupName());
    }

    @Transactional
    public void rejectInvitation(Long invitationId, User user) {
        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (!invitation.getReceiver().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not the recipient of this invitation");
        }

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new IllegalArgumentException("Invitation is not pending");
        }

        invitation.setStatus("REJECTED");
        invitation.setRespondedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);

        activityLogService.log(user, "Rejected invitation to join " + invitation.getGroup().getGroupName());
        notificationService.createNotification(invitation.getSender(), "GROUP_REJECTED", user.getFullName() + " rejected your invitation to join " + invitation.getGroup().getGroupName());
    }
}
