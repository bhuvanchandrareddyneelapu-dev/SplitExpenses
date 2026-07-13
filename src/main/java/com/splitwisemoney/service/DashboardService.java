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
    private final GroupInvitationRepository groupInvitationRepository;
    private final ExpenseApprovalRepository expenseApprovalRepository;

    public DashboardService(GroupMemberRepository groupMemberRepository,
                            ExpenseRepository expenseRepository,
                            ExpenseParticipantRepository expenseParticipantRepository,
                            SettlementRepository settlementRepository,
                            ActivityLogRepository activityLogRepository,
                            GroupInvitationRepository groupInvitationRepository,
                            ExpenseApprovalRepository expenseApprovalRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.settlementRepository = settlementRepository;
        this.activityLogRepository = activityLogRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.expenseApprovalRepository = expenseApprovalRepository;
    }

    /** Returns value if non-null, otherwise BigDecimal.ZERO. Guards against JPQL aggregate nulls. */
    private static BigDecimal coalesce(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public DashboardData getDashboardData(User user) {
        Long userId = user.getId();

        // 1. Total Groups count
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
        int totalGroups = memberships.size();

        // 2. Compute Total Paid (sum of all expenses paid by the user)
        //    JPQL SUM returns null when no rows match; coalesce guards against NPE.
        BigDecimal totalPaid = coalesce(expenseRepository.sumAmountByPaidById(userId)).setScale(2, RoundingMode.HALF_UP);

        // 3. Compute current Net Owed and Net Receive across all groups
        BigDecimal totalOwed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal amountToReceive = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (GroupMember membership : memberships) {
            Long groupId = membership.getGroup().getId();

            BigDecimal groupPaid      = coalesce(expenseRepository.sumAmountByGroupIdAndPaidById(groupId, userId));
            BigDecimal groupOwedShare = coalesce(expenseParticipantRepository.sumShareAmountByGroupIdAndUserId(groupId, userId));
            BigDecimal settledFrom    = coalesce(settlementRepository.sumSettledAmountByGroupIdAndFromUserId(groupId, userId));
            BigDecimal settledTo      = coalesce(settlementRepository.sumSettledAmountByGroupIdAndToUserId(groupId, userId));

            BigDecimal groupBalance = groupPaid.subtract(groupOwedShare)
                    .add(settledFrom)
                    .subtract(settledTo)
                    .setScale(2, RoundingMode.HALF_UP);

            if (groupBalance.compareTo(BigDecimal.ZERO) > 0) {
                amountToReceive = amountToReceive.add(groupBalance);
            } else if (groupBalance.compareTo(BigDecimal.ZERO) < 0) {
                totalOwed = totalOwed.add(groupBalance.negate());
            }
        }

        // 4. Get recent activities (top 5 sorted by created_at DESC)
        Pageable topFive = PageRequest.of(0, 5, Sort.by("createdAt").descending());
        List<ActivityLog> recentActivities = activityLogRepository.findByUserId(userId, topFive).getContent();

        int pendingInvitations = groupInvitationRepository.findByReceiverIdAndStatus(userId, "PENDING").size();
        int pendingApprovals = expenseApprovalRepository.findByUserIdAndStatusIn(userId, List.of("PENDING", "SUBMITTED")).size();
        int rejectedExpenses = (int) expenseRepository.countByPaidByIdAndVerificationStatus(userId, "UNDER_REVIEW")
                + (int) expenseRepository.countByPaidByIdAndVerificationStatus(userId, "REJECTED");
        int verifiedExpenses = (int) expenseRepository.countByPaidByIdAndVerificationStatus(userId, "VERIFIED");
        int pendingProofRequests = (int) expenseApprovalRepository.countByExpensePaidByIdAndStatus(userId, "REQUESTED_PROOF");

        return new DashboardData(totalPaid, totalOwed, amountToReceive, totalGroups, recentActivities,
                pendingInvitations, pendingApprovals, rejectedExpenses, verifiedExpenses, pendingProofRequests);
    }

    public static class DashboardData {
        private final BigDecimal totalPaid;
        private final BigDecimal totalOwed;
        private final BigDecimal amountToReceive;
        private final int totalGroups;
        private final List<ActivityLog> recentActivities;
        private final int pendingInvitations;
        private final int pendingApprovals;
        private final int rejectedExpenses;
        private final int verifiedExpenses;
        private final int pendingProofRequests;

        public DashboardData(BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal amountToReceive, int totalGroups,
                             List<ActivityLog> recentActivities, int pendingInvitations, int pendingApprovals,
                             int rejectedExpenses, int verifiedExpenses, int pendingProofRequests) {
            this.totalPaid = totalPaid;
            this.totalOwed = totalOwed;
            this.amountToReceive = amountToReceive;
            this.totalGroups = totalGroups;
            this.recentActivities = recentActivities;
            this.pendingInvitations = pendingInvitations;
            this.pendingApprovals = pendingApprovals;
            this.rejectedExpenses = rejectedExpenses;
            this.verifiedExpenses = verifiedExpenses;
            this.pendingProofRequests = pendingProofRequests;
        }

        public BigDecimal getTotalPaid() { return totalPaid; }
        public BigDecimal getTotalOwed() { return totalOwed; }
        public BigDecimal getAmountToReceive() { return amountToReceive; }
        public int getTotalGroups() { return totalGroups; }
        public List<ActivityLog> getRecentActivities() { return recentActivities; }
        public int getPendingInvitations() { return pendingInvitations; }
        public int getPendingApprovals() { return pendingApprovals; }
        public int getRejectedExpenses() { return rejectedExpenses; }
        public int getVerifiedExpenses() { return verifiedExpenses; }
        public int getPendingProofRequests() { return pendingProofRequests; }
    }
}
