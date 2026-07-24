package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.security.JwtTokenProvider;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final GroupService groupService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          GroupService groupService,
                          JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.groupService = groupService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account and auto-accept pending invitations")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        User user = userService.registerUser(
                registerRequest.getFullName(),
                registerRequest.getEmail(),
                registerRequest.getPassword()
        );

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(registerRequest.getEmail(), registerRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        Long acceptedGroupId = null;
        if (registerRequest.getInvitationToken() != null && !registerRequest.getInvitationToken().isBlank()) {
            try {
                GroupInvitation inv = groupService.acceptInvitationByToken(registerRequest.getInvitationToken().trim(), user);
                acceptedGroupId = inv.getGroup().getId();
            } catch (Exception ignored) {}
        }

        if (acceptedGroupId == null) {
            acceptedGroupId = groupService.autoAcceptPendingInvitations(user);
        }

        return ResponseEntity.ok(new AuthResponse(jwt, acceptedGroupId));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT bearer token with accepted invitation group redirect")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = userService.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long acceptedGroupId = null;
        if (loginRequest.getInvitationToken() != null && !loginRequest.getInvitationToken().isBlank()) {
            try {
                GroupInvitation inv = groupService.acceptInvitationByToken(loginRequest.getInvitationToken().trim(), user);
                acceptedGroupId = inv.getGroup().getId();
            } catch (Exception ignored) {}
        }

        if (acceptedGroupId == null) {
            acceptedGroupId = groupService.autoAcceptPendingInvitations(user);
        }

        return ResponseEntity.ok(new AuthResponse(jwt, acceptedGroupId));
    }
}
