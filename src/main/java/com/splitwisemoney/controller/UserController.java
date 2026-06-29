package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Endpoints for managing user profile and settings")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current logged in user details")
    public ResponseEntity<UserProfile> getCurrentUser() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(new UserProfile(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile name")
    public ResponseEntity<UserProfile> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        User user = getAuthenticatedUser();
        User updated = userService.updateProfile(user, request.getFullName());
        return ResponseEntity.ok(new UserProfile(updated.getId(), updated.getFullName(), updated.getEmail(), updated.getCreatedAt()));
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = getAuthenticatedUser();
        userService.changePassword(user, request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok("Password updated successfully");
    }
}
