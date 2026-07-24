package com.splitwisemoney.dto;

import java.time.LocalDateTime;

public class InviteDetailResponse {
    private Long groupId;
    private String groupName;
    private int groupMemberCount;
    private String inviterName;
    private String inviteeEmail;
    private String status;
    private LocalDateTime expiresAt;
    private boolean isRegisteredUser;

    public InviteDetailResponse() {}

    public InviteDetailResponse(Long groupId, String groupName, int groupMemberCount, String inviterName, String inviteeEmail, String status, LocalDateTime expiresAt, boolean isRegisteredUser) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupMemberCount = groupMemberCount;
        this.inviterName = inviterName;
        this.inviteeEmail = inviteeEmail;
        this.status = status;
        this.expiresAt = expiresAt;
        this.isRegisteredUser = isRegisteredUser;
    }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public int getGroupMemberCount() { return groupMemberCount; }
    public void setGroupMemberCount(int groupMemberCount) { this.groupMemberCount = groupMemberCount; }

    public String getInviterName() { return inviterName; }
    public void setInviterName(String inviterName) { this.inviterName = inviterName; }

    public String getInviteeEmail() { return inviteeEmail; }
    public void setInviteeEmail(String inviteeEmail) { this.inviteeEmail = inviteeEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRegisteredUser() { return isRegisteredUser; }
    public void setRegisteredUser(boolean registeredUser) { isRegisteredUser = registeredUser; }
}
