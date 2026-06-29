package com.splitwisemoney.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "expense_participants", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"expense_id", "user_id"})
})
public class ExpenseParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "share_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal shareAmount;

    public ExpenseParticipant() {}

    public ExpenseParticipant(Expense expense, User user, BigDecimal shareAmount) {
        this.expense = expense;
        this.user = user;
        this.shareAmount = shareAmount;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Expense getExpense() { return expense; }
    public void setExpense(Expense expense) { this.expense = expense; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public BigDecimal getShareAmount() { return shareAmount; }
    public void setShareAmount(BigDecimal shareAmount) { this.shareAmount = shareAmount; }
}
