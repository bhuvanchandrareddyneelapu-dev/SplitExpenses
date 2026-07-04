package com.splitwisemoney.dto;

import jakarta.validation.constraints.NotBlank;

public class ApprovalRequest {

    @NotBlank(message = "Status is required")
    private String status; // APPROVED, REJECTED, REQUESTED_PROOF

    private String comment;

    public ApprovalRequest() {}

    public ApprovalRequest(String status, String comment) {
        this.status = status;
        this.comment = comment;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
