package com.splitwisemoney;

import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.exception.InvalidTokenException;
import com.splitwisemoney.exception.InvitationExpiredException;
import com.splitwisemoney.exception.ResourceConflictException;
import com.splitwisemoney.repository.GroupInvitationRepository;
import com.splitwisemoney.repository.GroupMemberRepository;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.UserService;
import com.splitwisemoney.service.email.InvitationScheduler;
import com.splitwisemoney.service.email.InvitationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SplitWiseMoneyApplication.class)
@Transactional
class InvitationSystemTests {

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private InvitationTokenService invitationTokenService;

    @Autowired
    private InvitationScheduler invitationScheduler;

    private User owner;
    private User registeredUser;
    private Group group;

    @BeforeEach
    void setUp() {
        owner = userService.registerUser("Bhuvan", "bhuvan" + System.currentTimeMillis() + "@test.com", "Password@123");
        registeredUser = userService.registerUser("Ramsita", "ramsita" + System.currentTimeMillis() + "@gmail.com", "Password@123");
        group = groupService.createGroup("Observation", owner);
    }

    @Test
    @DisplayName("Scenario 1 — Registered User Invitation flow")
    void testRegisteredUserInvitation() {
        GroupInvitation inv = groupService.inviteMemberByEmail(group.getId(), registeredUser.getEmail(), owner);

        assertNotNull(inv);
        assertNotNull(inv.getInvitationToken());
        assertEquals("PENDING", inv.getStatus());
        assertEquals(registeredUser.getEmail(), inv.getInviteeEmail());
        assertNotNull(inv.getReceiver());
        assertEquals(registeredUser.getId(), inv.getReceiver().getId());

        // Accept invitation using token
        groupService.acceptInvitationByToken(inv.getInvitationToken(), registeredUser);

        GroupInvitation updated = groupService.getInvitationByToken(inv.getInvitationToken());
        assertEquals("ACCEPTED", updated.getStatus());
        assertNotNull(updated.getAcceptedAt());
        assertTrue(groupService.isMember(group.getId(), registeredUser.getId()));
    }

    @Test
    @DisplayName("Scenario 2 — Unregistered User Invitation & Auto-Accept on Signup")
    void testUnregisteredUserInvitationAndAutoAccept() {
        String unregEmail = "unregistered" + System.currentTimeMillis() + "@gmail.com";
        GroupInvitation inv = groupService.inviteMemberByEmail(group.getId(), unregEmail, owner);

        assertNotNull(inv);
        assertEquals("PENDING", inv.getStatus());
        assertEquals(unregEmail, inv.getInviteeEmail());
        assertNull(inv.getReceiver());

        // Now user registers with this email
        User newSignupUser = userService.registerUser("New Friend", unregEmail, "Password@123");

        // Verify auto-acceptance
        Optional<GroupInvitation> check = groupInvitationRepository.findByInvitationToken(inv.getInvitationToken());
        assertTrue(check.isPresent());
        assertEquals("ACCEPTED", check.get().getStatus());
        assertTrue(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), newSignupUser.getId()));
    }

    @Test
    @DisplayName("Reject Invitation flow")
    void testRejectInvitation() {
        GroupInvitation inv = groupService.inviteMemberByEmail(group.getId(), registeredUser.getEmail(), owner);
        assertEquals("PENDING", inv.getStatus());

        groupService.rejectInvitationByToken(inv.getInvitationToken(), registeredUser);

        Optional<GroupInvitation> check = groupInvitationRepository.findByInvitationToken(inv.getInvitationToken());
        assertTrue(check.isPresent());
        assertEquals("REJECTED", check.get().getStatus());
        assertFalse(groupService.isMember(group.getId(), registeredUser.getId()));
    }

    @Test
    @DisplayName("Expired Invitation throws InvitationExpiredException (HTTP 410)")
    void testExpiredInvitationThrows410() {
        GroupInvitation inv = groupService.inviteMemberByEmail(group.getId(), registeredUser.getEmail(), owner);
        // Force expire the invitation
        inv.setExpiresAt(LocalDateTime.now().minusHours(1));
        groupInvitationRepository.save(inv);

        assertThrows(InvitationExpiredException.class, () -> {
            groupService.acceptInvitationByToken(inv.getInvitationToken(), registeredUser);
        });

        assertThrows(InvitationExpiredException.class, () -> {
            groupService.rejectInvitationByToken(inv.getInvitationToken(), registeredUser);
        });
    }

    @Test
    @DisplayName("Duplicate Pending Invitation throws ResourceConflictException (HTTP 409)")
    void testDuplicatePendingInvitationThrows409() {
        String testEmail = "unreg_dup" + System.currentTimeMillis() + "@test.com";
        groupService.addMemberByEmail(group.getId(), testEmail, owner);

        assertThrows(ResourceConflictException.class, () -> {
            groupService.addMemberByEmail(group.getId(), testEmail, owner);
        });
    }

    @Test
    @DisplayName("User Already In Group throws ResourceConflictException (HTTP 409)")
    void testUserAlreadyInGroupThrows409() {
        groupService.addMemberByEmail(group.getId(), registeredUser.getEmail(), owner);
        assertTrue(groupService.isMember(group.getId(), registeredUser.getId()));

        assertThrows(ResourceConflictException.class, () -> {
            groupService.addMemberByEmail(group.getId(), registeredUser.getEmail(), owner);
        });
    }

    @Test
    @DisplayName("Invalid Email Format throws IllegalArgumentException (HTTP 400)")
    void testInvalidEmailFormatThrows400() {
        assertThrows(IllegalArgumentException.class, () -> {
            groupService.inviteMemberByEmail(group.getId(), "invalidemailformat", owner);
        });
    }

    @Test
    @DisplayName("Token Tampering / Invalid Token throws InvalidTokenException (HTTP 410)")
    void testInvalidTokenThrows410() {
        assertThrows(InvalidTokenException.class, () -> {
            groupService.getInvitationByToken("tampered-token-12345");
        });
    }

    @Test
    @DisplayName("Scheduled daily cleanup marks expired invitations as EXPIRED")
    void testScheduledCleanupExpiredInvitations() {
        GroupInvitation inv = groupService.inviteMemberByEmail(group.getId(), registeredUser.getEmail(), owner);
        inv.setExpiresAt(LocalDateTime.now().minusHours(1));
        groupInvitationRepository.save(inv);

        invitationScheduler.cleanupExpiredInvitations();

        Optional<GroupInvitation> updated = groupInvitationRepository.findById(inv.getId());
        assertTrue(updated.isPresent());
        assertEquals("EXPIRED", updated.get().getStatus());
    }

    @Test
    @DisplayName("Secure Token Generation uses UUID and SecureRandom with 48h expiry")
    void testTokenGenerationAndExpiry() {
        String token = invitationTokenService.generateToken();
        assertNotNull(token);
        assertEquals(36, token.length());

        LocalDateTime expiry = invitationTokenService.calculateExpiryTime();
        assertTrue(expiry.isAfter(LocalDateTime.now().plusHours(47)));
        assertTrue(expiry.isBefore(LocalDateTime.now().plusHours(49)));
    }
}
