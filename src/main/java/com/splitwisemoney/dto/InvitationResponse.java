package com.splitwisemoney.dto;

import java.time.LocalDateTime;

public class InvitationResponse {
    private Long id;
    private Long groupId;
    private String groupName;
    private String senderName;
    private String status;
    private LocalDateTime createdAt;
    private String invitationToken;
    private String inviteeEmail;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private boolean newUser;

    public InvitationResponse() {}

    public InvitationResponse(Long id, Long groupId, String groupName, String senderName, String status, LocalDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.senderName = senderName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public InvitationResponse(Long id, Long groupId, String groupName, String senderName, String status, LocalDateTime createdAt,
                              String invitationToken, String inviteeEmail, LocalDateTime expiresAt, LocalDateTime acceptedAt, boolean newUser) {
        this.id = id;
        this.groupId = groupId;
        this.groupName = groupName;
        this.senderName = senderName;
        this.status = status;
        this.createdAt = createdAt;
        this.invitationToken = invitationToken;
        this.inviteeEmail = inviteeEmail;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.newUser = newUser;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getInvitationToken() { return invitationToken; }
    public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }

    public String getInviteeEmail() { return inviteeEmail; }
    public void setInviteeEmail(String inviteeEmail) { this.inviteeEmail = inviteeEmail; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public boolean isNewUser() { return newUser; }
    public void setNewUser(boolean newUser) { this.newUser = newUser; }
}
