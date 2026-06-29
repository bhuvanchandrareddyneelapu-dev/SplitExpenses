package com.splitwisemoney.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {
    private BigDecimal totalPaid;
    private BigDecimal totalOwed;
    private BigDecimal amountToReceive;
    private int totalGroups;
    private List<ActivityLogDto> recentActivities;

    public DashboardResponse(BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal amountToReceive, int totalGroups, List<ActivityLogDto> recentActivities) {
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.amountToReceive = amountToReceive;
        this.totalGroups = totalGroups;
        this.recentActivities = recentActivities;
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
}
