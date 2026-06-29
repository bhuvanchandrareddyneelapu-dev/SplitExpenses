package com.splitwisemoney.service;

import com.splitwisemoney.entity.*;
import com.splitwisemoney.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class DashboardService {

    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final SettlementRepository settlementRepository;
    private final ActivityLogRepository activityLogRepository;

    public DashboardService(GroupMemberRepository groupMemberRepository,
                            ExpenseRepository expenseRepository,
                            ExpenseParticipantRepository expenseParticipantRepository,
                            SettlementRepository settlementRepository,
                            ActivityLogRepository activityLogRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.settlementRepository = settlementRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional(readOnly = true)
    public DashboardData getDashboardData(User user) {
        Long userId = user.getId();

        // 1. Total Groups count
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
        int totalGroups = memberships.size();

        // 2. Compute Total Paid (sum of all expenses paid by the user)
        BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (GroupMember membership : memberships) {
            List<Expense> groupExpenses = expenseRepository.findByGroupId(membership.getGroup().getId());
            for (Expense expense : groupExpenses) {
                if (expense.getPaidBy().getId().equals(userId)) {
                    totalPaid = totalPaid.add(expense.getAmount());
                }
            }
        }

        // 3. Compute current Net Owed and Net Receive across all groups
        BigDecimal totalOwed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountToReceive = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (GroupMember membership : memberships) {
            Long groupId = membership.getGroup().getId();
            List<Expense> groupExpenses = expenseRepository.findByGroupId(groupId);
            List<Settlement> groupSettlements = settlementRepository.findByGroupId(groupId);

            BigDecimal groupBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            for (Expense expense : groupExpenses) {
                if (expense.getPaidBy().getId().equals(userId)) {
                    groupBalance = groupBalance.add(expense.getAmount());
                }

                List<ExpenseParticipant> participants = expenseParticipantRepository.findByExpenseId(expense.getId());
                for (ExpenseParticipant participant : participants) {
                    if (participant.getUser().getId().equals(userId)) {
                        groupBalance = groupBalance.subtract(participant.getShareAmount());
                    }
                }
            }

            for (Settlement settlement : groupSettlements) {
                if ("SETTLED".equalsIgnoreCase(settlement.getStatus())) {
                    if (settlement.getFromUser().getId().equals(userId)) {
                        groupBalance = groupBalance.add(settlement.getAmount());
                    }
                    if (settlement.getToUser().getId().equals(userId)) {
                        groupBalance = groupBalance.subtract(settlement.getAmount());
                    }
                }
            }

            if (groupBalance.compareTo(BigDecimal.ZERO) > 0) {
                amountToReceive = amountToReceive.add(groupBalance);
            } else if (groupBalance.compareTo(BigDecimal.ZERO) < 0) {
                totalOwed = totalOwed.add(groupBalance.negate());
            }
        }

        // 4. Get recent activities (top 5 sorted by created_at DESC)
        Pageable topFive = PageRequest.of(0, 5, Sort.by("createdAt").descending());
        List<ActivityLog> recentActivities = activityLogRepository.findByUserId(userId, topFive).getContent();

        return new DashboardData(totalPaid, totalOwed, amountToReceive, totalGroups, recentActivities);
    }

    public static class DashboardData {
        private final BigDecimal totalPaid;
        private final BigDecimal totalOwed;
        private final BigDecimal amountToReceive;
        private final int totalGroups;
        private final List<ActivityLog> recentActivities;

        public DashboardData(BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal amountToReceive, int totalGroups, List<ActivityLog> recentActivities) {
            this.totalPaid = totalPaid;
            this.totalOwed = totalOwed;
            this.amountToReceive = amountToReceive;
            this.totalGroups = totalGroups;
            this.recentActivities = recentActivities;
        }

        public BigDecimal getTotalPaid() { return totalPaid; }
        public BigDecimal getTotalOwed() { return totalOwed; }
        public BigDecimal getAmountToReceive() { return amountToReceive; }
        public int getTotalGroups() { return totalGroups; }
        public List<ActivityLog> getRecentActivities() { return recentActivities; }
    }
}
