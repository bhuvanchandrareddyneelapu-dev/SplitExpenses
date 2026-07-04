package com.splitwisemoney.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {
    private BigDecimal totalPaid;
    private BigDecimal totalOwed;
    private BigDecimal amountToReceive;
    private int totalGroups;
    private List<ActivityLogDto> recentActivities;

    private int pendingInvitationsCount;
    private int pendingApprovalsCount;
    private int rejectedExpensesCount;
    private int verifiedExpensesCount;
    private int pendingProofRequestsCount;

    public DashboardResponse(BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal amountToReceive, int totalGroups, List<ActivityLogDto> recentActivities) {
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.amountToReceive = amountToReceive;
        this.totalGroups = totalGroups;
        this.recentActivities = recentActivities;
    }

    public DashboardResponse(BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal amountToReceive, int totalGroups,
                             List<ActivityLogDto> recentActivities, int pendingInvitationsCount, int pendingApprovalsCount,
                             int rejectedExpensesCount, int verifiedExpensesCount, int pendingProofRequestsCount) {
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.amountToReceive = amountToReceive;
        this.totalGroups = totalGroups;
        this.recentActivities = recentActivities;
        this.pendingInvitationsCount = pendingInvitationsCount;
        this.pendingApprovalsCount = pendingApprovalsCount;
        this.rejectedExpensesCount = rejectedExpensesCount;
        this.verifiedExpensesCount = verifiedExpensesCount;
        this.pendingProofRequestsCount = pendingProofRequestsCount;
    }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
    public BigDecimal getTotalOwed() { return totalOwed; }
    public void setTotalOwed(BigDecimal totalOwed) { this.totalOwed = totalOwed; }
    public BigDecimal getAmountToReceive() { return amountToReceive; }
    public void setAmountToReceive(BigDecimal amountToReceive) { this.amountToReceive = amountToReceive; }
    public int getTotalGroups() { return totalGroups; }
    public void setTotalGroups(int totalGroups) { this.totalGroups = totalGroups; }
    public List<ActivityLogDto> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<ActivityLogDto> recentActivities) { this.recentActivities = recentActivities; }

    public int getPendingInvitationsCount() { return pendingInvitationsCount; }
    public void setPendingInvitationsCount(int pendingInvitationsCount) { this.pendingInvitationsCount = pendingInvitationsCount; }
    public int getPendingApprovalsCount() { return pendingApprovalsCount; }
    public void setPendingApprovalsCount(int pendingApprovalsCount) { this.pendingApprovalsCount = pendingApprovalsCount; }
    public int getRejectedExpensesCount() { return rejectedExpensesCount; }
    public void setRejectedExpensesCount(int rejectedExpensesCount) { this.rejectedExpensesCount = rejectedExpensesCount; }
    public int getVerifiedExpensesCount() { return verifiedExpensesCount; }
    public void setVerifiedExpensesCount(int verifiedExpensesCount) { this.verifiedExpensesCount = verifiedExpensesCount; }
    public int getPendingProofRequestsCount() { return pendingProofRequestsCount; }
    public void setPendingProofRequestsCount(int pendingProofRequestsCount) { this.pendingProofRequestsCount = pendingProofRequestsCount; }
}
