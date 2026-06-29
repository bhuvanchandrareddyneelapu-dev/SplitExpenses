package com.splitwisemoney.service;

import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.GroupMember;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.repository.GroupMemberRepository;
import com.splitwisemoney.repository.GroupRepository;
import com.splitwisemoney.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        ActivityLogService activityLogService,
                        NotificationService notificationService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
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

        group.setGroupName(groupName);
        Group updated = groupRepository.save(group);

        activityLogService.log(user, "Renamed group to: " + groupName);
        return updated;
    }

    @Transactional
    public void deleteGroup(Long groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        groupRepository.delete(group);
        activityLogService.log(user, "Deleted group: " + group.getGroupName());
    }

    @Transactional
    public GroupMember addMemberByEmail(Long groupId, String email, User actor) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

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

        groupMemberRepository.delete(member);

        activityLogService.log(actor, "Removed " + userToRemove.getFullName() + " from group " + group.getGroupName());
        notificationService.createNotification(userToRemove, "GROUP_INVITE", "You were removed from the group " + group.getGroupName());
    }

    @Transactional(readOnly = true)
    public List<Group> getUserGroups(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
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
        return groupRepository.findById(groupId);
    }
}
