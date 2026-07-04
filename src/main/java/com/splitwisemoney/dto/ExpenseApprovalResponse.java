package com.splitwisemoney.dto;

import java.time.LocalDateTime;

public class ExpenseApprovalResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String status;
    private String comment;
    private LocalDateTime approvedAt;

    public ExpenseApprovalResponse() {}

    public ExpenseApprovalResponse(Long id, Long userId, String userName, String status, String comment, LocalDateTime approvedAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.status = status;
        this.comment = comment;
        this.approvedAt = approvedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
