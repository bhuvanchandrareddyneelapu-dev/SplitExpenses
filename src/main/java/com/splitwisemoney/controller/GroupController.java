package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Groups", description = "Endpoints for managing group configurations and group memberships")
public class GroupController {

    private final GroupService groupService;
    private final UserService userService;

    public GroupController(GroupService groupService, UserService userService) {
        this.groupService = groupService;
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    private GroupResponse mapToGroupResponse(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getCreatedBy() != null ? group.getCreatedBy().getId() : null,
                group.getCreatedBy() != null ? group.getCreatedBy().getFullName() : "System",
                group.getCreatedAt()
        );
    }

    @PostMapping
    @Operation(summary = "Create a new expense group")
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody GroupRequest request) {
        User user = getAuthenticatedUser();
        Group group = groupService.createGroup(request.getGroupName(), user);
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @GetMapping
    @Operation(summary = "List all groups associated with the current user")
    public ResponseEntity<List<GroupResponse>> getUserGroups() {
        User user = getAuthenticatedUser();
        List<Group> groups = groupService.getUserGroups(user.getId());
        List<GroupResponse> response = groups.stream()
                .map(this::mapToGroupResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific group details by ID")
    public ResponseEntity<GroupResponse> getGroupById(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (!groupService.isMember(id, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        Group group = groupService.getGroupById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update group name")
    public ResponseEntity<GroupResponse> editGroup(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        User user = getAuthenticatedUser();
        Group group = groupService.editGroup(id, request.getGroupName(), user);
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group")
    public ResponseEntity<String> deleteGroup(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        groupService.deleteGroup(id, user);
        return ResponseEntity.ok("Group deleted successfully");
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Get all member users in a group")
    public ResponseEntity<List<UserProfile>> getGroupMembers(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (!groupService.isMember(id, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        List<User> members = groupService.getGroupMembers(id);
        List<UserProfile> response = members.stream()
                .map(u -> new UserProfile(u.getId(), u.getFullName(), u.getEmail(), u.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a user member to a group by their registered email")
    public ResponseEntity<String> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        User user = getAuthenticatedUser();
        groupService.addMemberByEmail(id, request.getEmail(), user);
        return ResponseEntity.ok("Member added successfully");
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a user member from a group")
    public ResponseEntity<String> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        User user = getAuthenticatedUser();
        groupService.removeMember(id, userId, user);
        return ResponseEntity.ok("Member removed successfully");
    }

    @PostMapping("/{id}/invite")
    @Operation(summary = "Invite a user member to a group by their registered email")
    public ResponseEntity<InvitationResponse> inviteMember(@PathVariable Long id, @Valid @RequestBody InviteMemberRequest request) {
        User user = getAuthenticatedUser();
        com.splitwisemoney.entity.GroupInvitation invitation = groupService.inviteMemberByEmail(id, request.getEmail(), user);
        InvitationResponse response = new InvitationResponse(
                invitation.getId(),
                invitation.getGroup().getId(),
                invitation.getGroup().getGroupName(),
                invitation.getSender().getFullName(),
                invitation.getStatus(),
                invitation.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/invitations")
    @Operation(summary = "List all pending invitations for current user")
    public ResponseEntity<List<InvitationResponse>> getPendingInvitations() {
        User user = getAuthenticatedUser();
        List<com.splitwisemoney.entity.GroupInvitation> invitations = groupService.getPendingInvitations(user.getId());
        List<InvitationResponse> response = invitations.stream()
                .map(inv -> new InvitationResponse(
                        inv.getId(),
                        inv.getGroup().getId(),
                        inv.getGroup().getGroupName(),
                        inv.getSender().getFullName(),
                        inv.getStatus(),
                        inv.getCreatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "Accept a group invitation")
    public ResponseEntity<String> acceptInvitation(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        groupService.acceptInvitation(id, user);
        return ResponseEntity.ok("Invitation accepted successfully");
    }

    @PostMapping("/invitations/{id}/reject")
    @Operation(summary = "Reject a group invitation")
    public ResponseEntity<String> rejectInvitation(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        groupService.rejectInvitation(id, user);
        return ResponseEntity.ok("Invitation rejected successfully");
    }
}
