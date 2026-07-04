package com.splitwisemoney.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpenseResponse {
    private Long id;
    private BigDecimal amount;
    private String description;
    private String category;
    private LocalDate expenseDate;
    private Long paidById;
    private String paidByName;
    private List<ParticipantShareDto> participants;

    private String verificationStatus;
    private String receiptUrl;
    private List<ExpenseApprovalResponse> approvals;

    public ExpenseResponse(Long id, BigDecimal amount, String description, String category,
                           LocalDate expenseDate, Long paidById, String paidByName,
                           List<ParticipantShareDto> participants, String verificationStatus, String receiptUrl,
                           List<ExpenseApprovalResponse> approvals) {
        this.id = id;
        this.amount = amount;
        this.description = description;
        this.category = category;
        this.expenseDate = expenseDate;
        this.paidById = paidById;
        this.paidByName = paidByName;
        this.participants = participants;
        this.verificationStatus = verificationStatus;
        this.receiptUrl = receiptUrl;
        this.approvals = approvals;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public Long getPaidById() { return paidById; }
    public void setPaidById(Long paidById) { this.paidById = paidById; }
    public String getPaidByName() { return paidByName; }
    public void setPaidByName(String paidByName) { this.paidByName = paidByName; }
    public List<ParticipantShareDto> getParticipants() { return participants; }
    public void setParticipants(List<ParticipantShareDto> participants) { this.participants = participants; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }
    public List<ExpenseApprovalResponse> getApprovals() { return approvals; }
    public void setApprovals(List<ExpenseApprovalResponse> approvals) { this.approvals = approvals; }
}
