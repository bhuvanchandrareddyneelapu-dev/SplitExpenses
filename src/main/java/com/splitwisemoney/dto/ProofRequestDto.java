package com.splitwisemoney.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProofRequestDto {
    private Long id; // approval ID
    private Long expenseId;
    private String expenseDescription;
    private String requestedByName;
    private String requestedToName;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String status;
    private String comment;

    public ProofRequestDto() {}

    public ProofRequestDto(Long id, Long expenseId, String expenseDescription, String requestedByName,
                           String requestedToName, BigDecimal amount, LocalDate expenseDate,
                           String status, String comment) {
        this.id = id;
        this.expenseId = expenseId;
        this.expenseDescription = expenseDescription;
        this.requestedByName = requestedByName;
        this.requestedToName = requestedToName;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.status = status;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExpenseId() { return expenseId; }
    public void setExpenseId(Long expenseId) { this.expenseId = expenseId; }

    public String getExpenseDescription() { return expenseDescription; }
    public void setExpenseDescription(String expenseDescription) { this.expenseDescription = expenseDescription; }

    public String getRequestedByName() { return requestedByName; }
    public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }

    public String getRequestedToName() { return requestedToName; }
    public void setRequestedToName(String requestedToName) { this.requestedToName = requestedToName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
