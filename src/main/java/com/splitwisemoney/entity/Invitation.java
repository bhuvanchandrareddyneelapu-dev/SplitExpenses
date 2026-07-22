package com.splitwisemoney.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_invitations")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "invitation_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("GENERAL")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invitation_token", unique = true, length = 36)
    private String token;

    @Column(name = "invitee_email", length = 100)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User invitedBy;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, ACCEPTED, REJECTED, EXPIRED

    @Column(name = "expires_at")
    private LocalDateTime expiryTime;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User registeredUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (expiryTime == null) {
            expiryTime = LocalDateTime.now().plusHours(48);
        }
    }

    public Invitation() {}

    public Invitation(Group group, User invitedBy, User registeredUser) {
        this.group = group;
        this.invitedBy = invitedBy;
        this.registeredUser = registeredUser;
        if (registeredUser != null) {
            this.email = registeredUser.getEmail();
        }
        this.status = "PENDING";
    }

    public Invitation(Group group, User invitedBy, String email) {
        this.group = group;
        this.invitedBy = invitedBy;
        this.email = email;
        this.status = "PENDING";
    }

    public boolean isExpired() {
        return expiryTime != null && LocalDateTime.now().isAfter(expiryTime);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Group getGroup() { return group; }
    public void setGroup(Group group) { this.group = group; }

    public User getInvitedBy() { return invitedBy; }
    public void setInvitedBy(User invitedBy) { this.invitedBy = invitedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getRegisteredUser() { return registeredUser; }
    public void setRegisteredUser(User registeredUser) { this.registeredUser = registeredUser; }
}
