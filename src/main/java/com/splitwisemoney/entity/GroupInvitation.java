package com.splitwisemoney.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invitations")
public class GroupInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Secure random UUID token for token-based accept/reject links.
     * Also used in email invite links.
     */
    @Column(name = "invitation_token", unique = true, length = 64)
    private String invitationToken;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /**
     * The registered user being invited. May be null if the invitee email
     * does not belong to a registered account yet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    /**
     * Email address being invited. Always set regardless of whether the
     * invitee has a registered account.
     */
    @Column(name = "invitee_email", length = 100)
    private String inviteeEmail;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, ACCEPTED, REJECTED, EXPIRED

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "email_delivery_status", length = 20)
    private String emailDeliveryStatus; // PENDING, SENT, FAILED, SKIPPED

    @Column(name = "email_delivery_error", columnDefinition = "TEXT")
    private String emailDeliveryError;

    @Column(name = "email_last_attempt_at")
    private LocalDateTime emailLastAttemptAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusHours(48);
        }
        if (emailDeliveryStatus == null) {
            emailDeliveryStatus = "PENDING";
        }
    }


    public GroupInvitation() {}

    /** Constructor for inviting an existing registered user. */
    public GroupInvitation(Group group, User sender, User receiver) {
        this.group = group;
        this.sender = sender;
        this.receiver = receiver;
        this.inviteeEmail = receiver.getEmail();
        this.status = "PENDING";
    }

    /** Constructor for inviting a non-registered email address. */
    public GroupInvitation(Group group, User sender, String inviteeEmail) {
        this.group = group;
        this.sender = sender;
        this.inviteeEmail = inviteeEmail;
        this.status = "PENDING";
    }

    // --- Helpers ---

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInvitationToken() { return invitationToken; }
    public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public String getInviteeEmail() { return inviteeEmail; }
    public void setInviteeEmail(String inviteeEmail) { this.inviteeEmail = inviteeEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getEmailDeliveryStatus() { return emailDeliveryStatus; }
    public void setEmailDeliveryStatus(String emailDeliveryStatus) { this.emailDeliveryStatus = emailDeliveryStatus; }

    public String getEmailDeliveryError() { return emailDeliveryError; }
    public void setEmailDeliveryError(String emailDeliveryError) { this.emailDeliveryError = emailDeliveryError; }

    public LocalDateTime getEmailLastAttemptAt() { return emailLastAttemptAt; }
    public void setEmailLastAttemptAt(LocalDateTime emailLastAttemptAt) { this.emailLastAttemptAt = emailLastAttemptAt; }

    // --- Invitation Field Compatibility Aliases ---
    public String getToken() { return invitationToken; }
    public void setToken(String token) { this.invitationToken = token; }

    public String getEmail() { return inviteeEmail; }
    public void setEmail(String email) { this.inviteeEmail = email; }

    public User getInvitedBy() { return sender; }
    public void setInvitedBy(User invitedBy) { this.sender = invitedBy; }

    public User getRegisteredUser() { return receiver; }
    public void setRegisteredUser(User registeredUser) { this.receiver = registeredUser; }

    public LocalDateTime getExpiryTime() { return expiresAt; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiresAt = expiryTime; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
}

