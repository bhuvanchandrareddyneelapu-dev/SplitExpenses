package com.splitwisemoney.controller;

import com.splitwisemoney.exception.InvitationExpiredException;

import com.splitwisemoney.dto.InviteDetailResponse;
import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Group Invitations", description = "Public and protected endpoints for group invitations")
public class InvitationController {

    private final GroupService groupService;
    private final UserService userService;

    public InvitationController(GroupService groupService, UserService userService) {
        this.groupService = groupService;
        this.userService = userService;
    }

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

    @GetMapping("/{token}")
    @Operation(summary = "Get public details of a group invitation by token")
    public ResponseEntity<?> getInvitationDetails(@PathVariable String token) {
        GroupInvitation invitation = groupService.getInvitationByToken(token);
        if (invitation.isExpired() || "EXPIRED".equals(invitation.getStatus())) {
            throw new InvitationExpiredException("This invitation link has expired.");
        }

        int memberCount = groupService.getGroupMembers(invitation.getGroup().getId()).size();
        boolean isRegistered = userService.findByEmail(invitation.getInviteeEmail()).isPresent();

        InviteDetailResponse response = new InviteDetailResponse(
                invitation.getGroup().getId(),
                invitation.getGroup().getGroupName(),
                memberCount,
                invitation.getSender().getFullName(),
                invitation.getInviteeEmail(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                isRegistered
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{token}/accept")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Accept a group invitation using token")
    public ResponseEntity<?> acceptInvitation(@PathVariable String token) {
        User user = getAuthenticatedUser();
        if (user == null) return unauthorized();

        GroupInvitation inv = groupService.acceptInvitationByToken(token, user);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Invitation accepted successfully",
                "groupId", inv.getGroup().getId()
        ));
    }

    @PostMapping("/{token}/reject")
    @Operation(summary = "Reject/decline a group invitation using token")
    public ResponseEntity<?> rejectInvitation(@PathVariable String token) {
        User user = getAuthenticatedUser();
        GroupInvitation inv = groupService.rejectInvitationByToken(token, user);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Invitation declined successfully",
                "groupId", inv.getGroup().getId()
        ));
    }
}
