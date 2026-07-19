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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
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

    /**
     * Resolves the authenticated user from the security context.
     * Returns null (instead of throwing) when the principal is anonymous or missing,
     * so each endpoint can return a clean 401 response.
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userService.findByEmail(email).orElse(null);
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(java.util.Map.of(
                        "status", 401,
                        "error", "Unauthorized",
                        "message", "Authentication required. Please log in again."
                ));
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
    public ResponseEntity<?> createGroup(@Valid @RequestBody GroupRequest request) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        Group group = groupService.createGroup(request.getGroupName(), user);
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @GetMapping
    @Operation(summary = "List all groups associated with the current user")
    public ResponseEntity<?> getUserGroups() {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        List<Group> groups = groupService.getUserGroups(user.getId());
        if (groups.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<GroupResponse> response = groups.stream()
                .map(this::mapToGroupResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific group details by ID")
    public ResponseEntity<?> getGroupById(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        if (!groupService.isMember(id, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        Group group = groupService.getGroupById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update group name")
    public ResponseEntity<?> editGroup(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        Group group = groupService.editGroup(id, request.getGroupName(), user);
        return ResponseEntity.ok(mapToGroupResponse(group));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group")
    public ResponseEntity<?> deleteGroup(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        groupService.deleteGroup(id, user);
        return ResponseEntity.ok("Group deleted successfully");
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Get all member users in a group")
    public ResponseEntity<?> getGroupMembers(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

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
    public ResponseEntity<?> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        groupService.addMemberByEmail(id, request.getEmail(), user);
        return ResponseEntity.ok("Member added successfully");
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a user member from a group")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        groupService.removeMember(id, userId, user);
        return ResponseEntity.ok("Member removed successfully");
    }

    @PostMapping("/{id}/invite")
    @Operation(summary = "Invite a user member to a group by email. Resends automatically if a pending invitation already exists.")
    public ResponseEntity<?> inviteMember(@PathVariable Long id, @Valid @RequestBody InviteMemberRequest request) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        try {
            com.splitwisemoney.entity.GroupInvitation invitation = groupService.inviteMemberByEmail(id, request.getEmail(), user);
            InvitationResponse response = new InvitationResponse(
                    invitation.getId(),
                    invitation.getGroup().getId(),
                    invitation.getGroup().getGroupName(),
                    invitation.getSender().getFullName(),
                    invitation.getStatus(),
                    invitation.getCreatedAt(),
                    invitation.getInvitationToken(),
                    invitation.getInviteeEmail(),
                    invitation.getExpiresAt(),
                    invitation.getAcceptedAt(),
                    invitation.getReceiver() == null
            );
            return ResponseEntity.ok(response);
        } catch (MailException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of(
                            "message", "Invitation saved but email delivery failed: " + e.getMessage(),
                            "smtpError", true
                    ));
        } catch (RuntimeException e) {
            // Safety net: catches any unexpected exception from the mail pipeline
            // (e.g. a wrapped MessagingException that escaped as RuntimeException).
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("mail") || msg.toLowerCase().contains("smtp")
                    || msg.toLowerCase().contains("message")) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(java.util.Map.of(
                                "message", "Invitation saved but email delivery failed: " + msg,
                                "smtpError", true
                        ));
            }
            throw e;  // re-throw non-mail exceptions
        }
    }

    @PostMapping("/{id}/invite/resend")
    @Operation(summary = "Resend an existing pending invitation email")
    public ResponseEntity<?> resendInvitation(@PathVariable Long id, @Valid @RequestBody InviteMemberRequest request) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        try {
            com.splitwisemoney.entity.GroupInvitation invitation = groupService.resendInvitation(id, request.getEmail(), user);
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Invitation email resent successfully to " + invitation.getInviteeEmail(),
                    "token", invitation.getInvitationToken()
            ));
        } catch (MailException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(java.util.Map.of(
                            "message", "Invitation found but email delivery failed: " + e.getMessage(),
                            "smtpError", true
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            // Safety net: catches any unexpected exception from the mail pipeline.
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("mail") || msg.toLowerCase().contains("smtp")
                    || msg.toLowerCase().contains("message")) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(java.util.Map.of(
                                "message", "Invitation found but email delivery failed: " + msg,
                                "smtpError", true
                        ));
            }
            throw e;  // re-throw non-mail exceptions
        }
    }

    @GetMapping("/invitations")
    @Operation(summary = "List all pending invitations for current user")
    public ResponseEntity<?> getPendingInvitations() {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        List<com.splitwisemoney.entity.GroupInvitation> invitations = groupService.getPendingInvitations(user.getId());
        List<InvitationResponse> response = invitations.stream()
                .map(inv -> new InvitationResponse(
                        inv.getId(),
                        inv.getGroup().getId(),
                        inv.getGroup().getGroupName(),
                        inv.getSender().getFullName(),
                        inv.getStatus(),
                        inv.getCreatedAt(),
                        inv.getInvitationToken(),
                        inv.getInviteeEmail(),
                        inv.getExpiresAt(),
                        inv.getAcceptedAt(),
                        inv.getReceiver() == null
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "Accept a group invitation")
    public ResponseEntity<?> acceptInvitation(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        groupService.acceptInvitation(id, user);
        return ResponseEntity.ok("Invitation accepted successfully");
    }

    @PostMapping("/invitations/{id}/reject")
    @Operation(summary = "Reject a group invitation")
    public ResponseEntity<?> rejectInvitation(@PathVariable Long id) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        groupService.rejectInvitation(id, user);
        return ResponseEntity.ok("Invitation rejected successfully");
    }
}
