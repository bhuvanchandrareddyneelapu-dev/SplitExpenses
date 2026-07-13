package com.splitwisemoney.service;

import com.splitwisemoney.entity.*;
import com.splitwisemoney.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseApprovalRepository expenseApprovalRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseParticipantRepository expenseParticipantRepository,
                          GroupRepository groupRepository,
                          UserRepository userRepository,
                          GroupMemberRepository groupMemberRepository,
                          ExpenseApprovalRepository expenseApprovalRepository,
                          ActivityLogService activityLogService,
                          NotificationService notificationService) {
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseApprovalRepository = expenseApprovalRepository;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Expense addExpense(Long groupId, Long paidById, BigDecimal amount, String description,
                             String category, LocalDate expenseDate, Map<Long, BigDecimal> participantShares, User actor) {
        
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can add expenses.");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        User paidBy = userRepository.findById(paidById)
                .orElseThrow(() -> new IllegalArgumentException("Paying user not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, paidById)) {
            throw new IllegalArgumentException("Paying user is not a member of the group");
        }

        // Validate sum of shares matches total amount (allowing tiny rounding margins)
        BigDecimal totalShareSum = BigDecimal.ZERO;
        for (BigDecimal share : participantShares.values()) {
            totalShareSum = totalShareSum.add(share);
        }

        if (totalShareSum.subtract(amount).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new IllegalArgumentException("Sum of shares (" + totalShareSum + ") does not match total expense amount (" + amount + ")");
        }

        Expense expense = new Expense(group, paidBy, amount, description, category, expenseDate);
        Expense savedExpense = expenseRepository.save(expense);

        List<ExpenseParticipant> participants = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : participantShares.entrySet()) {
            Long userId = entry.getKey();
            BigDecimal shareAmount = entry.getValue();

            User participantUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Participant user " + userId + " not found"));

            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
                throw new IllegalArgumentException("Participant " + participantUser.getFullName() + " is not a member of the group");
            }

            ExpenseParticipant participant = new ExpenseParticipant(savedExpense, participantUser, shareAmount);
            participants.add(participant);

            if (!userId.equals(paidById)) {
                notificationService.createNotification(participantUser, "EXPENSE_ADDED", 
                        paidBy.getFullName() + " added an expense \"" + description + "\" in group " + group.getGroupName() + ". Your share: ₹" + shareAmount);
            }
        }
        expenseParticipantRepository.saveAll(participants);

        List<ExpenseApproval> approvals = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : participantShares.entrySet()) {
            Long userId = entry.getKey();
            User participantUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Participant user " + userId + " not found"));

            ExpenseApproval approval = new ExpenseApproval(savedExpense, participantUser);
            if (userId.equals(paidById)) {
                approval.setStatus("APPROVED");
                approval.setApprovedAt(java.time.LocalDateTime.now());
            } else {
                approval.setStatus("PENDING");
                notificationService.createNotification(participantUser, "EXPENSE_APPROVAL",
                        paidBy.getFullName() + " added an expense \"" + description + "\" of ₹" + amount + ". Please approve.");
            }
            approvals.add(approval);
        }
        expenseApprovalRepository.saveAll(approvals);

        boolean allApproved = approvals.stream().allMatch(a -> "APPROVED".equalsIgnoreCase(a.getStatus()));
        if (allApproved) {
            savedExpense.setVerificationStatus("VERIFIED");
            expenseRepository.save(savedExpense);
        }

        activityLogService.log(actor, "Added expense \"" + description + "\" of ₹" + amount + " to group " + group.getGroupName());
        
        return savedExpense;
    }

    @Transactional
    public Expense editExpense(Long expenseId, BigDecimal amount, String description,
                              String category, LocalDate expenseDate, Map<Long, BigDecimal> participantShares, User actor) {
        
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        Long groupId = expense.getGroup().getId();

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can edit expenses.");
        }

        // Validate sum of shares matches total amount
        BigDecimal totalShareSum = BigDecimal.ZERO;
        for (BigDecimal share : participantShares.values()) {
            totalShareSum = totalShareSum.add(share);
        }

        if (totalShareSum.subtract(amount).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new IllegalArgumentException("Sum of shares (" + totalShareSum + ") does not match total expense amount (" + amount + ")");
        }

        expense.setAmount(amount);
        expense.setDescription(description);
        expense.setCategory(category);
        expense.setExpenseDate(expenseDate);
        Expense updatedExpense = expenseRepository.save(expense);

        // Delete old participants
        expenseParticipantRepository.deleteByExpenseId(expenseId);

        // Create new ones
        List<ExpenseParticipant> participants = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : participantShares.entrySet()) {
            Long userId = entry.getKey();
            BigDecimal shareAmount = entry.getValue();

            User participantUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Participant user " + userId + " not found"));

            if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
                throw new IllegalArgumentException("Participant is not a member of the group");
            }

            ExpenseParticipant participant = new ExpenseParticipant(updatedExpense, participantUser, shareAmount);
            participants.add(participant);

            if (!userId.equals(expense.getPaidBy().getId())) {
                notificationService.createNotification(participantUser, "EXPENSE_ADDED", 
                        actor.getFullName() + " updated the expense \"" + description + "\" in group " + expense.getGroup().getGroupName() + ". Your new share: ₹" + shareAmount);
            }
        }
        expenseParticipantRepository.saveAll(participants);

        activityLogService.log(actor, "Updated expense \"" + description + "\" in group " + expense.getGroup().getGroupName());

        return updatedExpense;
    }

    @Transactional
    public void deleteExpense(Long expenseId, User actor) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(expense.getGroup().getId(), actor.getId())) {
            throw new IllegalArgumentException("Only group members can delete expenses.");
        }

        expenseRepository.delete(expense);
        activityLogService.log(actor, "Deleted expense \"" + expense.getDescription() + "\" from group " + expense.getGroup().getGroupName());
    }

    @Transactional(readOnly = true)
    public Page<Expense> getGroupExpenses(Long groupId, String category, Pageable pageable) {
        if (category != null && !category.trim().isEmpty()) {
            return expenseRepository.findByGroupIdAndCategory(groupId, category, pageable);
        }
        return expenseRepository.findByGroupId(groupId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Expense> getGroupExpensesList(Long groupId) {
        return expenseRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseApproval> getPendingApprovalsForUser(Long userId) {
        return expenseApprovalRepository.findByUserIdAndStatusIn(userId, List.of("PENDING", "SUBMITTED"));
    }

    @Transactional(readOnly = true)
    public List<ExpenseApproval> getPendingProofRequestsForUser(Long userId) {
        return expenseApprovalRepository.findByExpensePaidByIdAndStatus(userId, "REQUESTED_PROOF");
    }

    @Transactional(readOnly = true)
    public List<Expense> getRejectedExpensesForUser(Long userId) {
        return expenseRepository.findByPaidByIdAndVerificationStatusIn(userId, List.of("UNDER_REVIEW", "REJECTED"));
    }

    @Transactional(readOnly = true)
    public List<Expense> getVerifiedExpensesForUser(Long userId) {
        return expenseRepository.findByPaidByIdAndVerificationStatus(userId, "VERIFIED");
    }

    @Transactional(readOnly = true)
    public List<ExpenseParticipant> getExpenseParticipants(Long expenseId) {
        return expenseParticipantRepository.findByExpenseId(expenseId);
    }

    @Transactional(readOnly = true)
    public Optional<Expense> getExpenseById(Long expenseId) {
        return expenseRepository.findById(expenseId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseApproval> getExpenseApprovals(Long expenseId) {
        return expenseApprovalRepository.findByExpenseId(expenseId);
    }

    @Transactional
    public void approveOrRejectExpense(Long expenseId, String status, String comment, User user) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        ExpenseApproval approval = expenseApprovalRepository.findByExpenseIdAndUserId(expenseId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("You are not a participant in this expense"));

        approval.setStatus(status);
        approval.setComment(comment);
        approval.setApprovedAt(java.time.LocalDateTime.now());
        expenseApprovalRepository.save(approval);

        if ("APPROVED".equalsIgnoreCase(status)) {
            activityLogService.log(user, user.getFullName() + " approved expense \"" + expense.getDescription() + "\"");
            
            // Check if ALL participants approved
            List<ExpenseApproval> approvals = expenseApprovalRepository.findByExpenseId(expenseId);
            boolean allApproved = approvals.stream().allMatch(a -> "APPROVED".equalsIgnoreCase(a.getStatus()));
            if (allApproved) {
                expense.setVerificationStatus("VERIFIED");
                expenseRepository.save(expense);
                activityLogService.log(expense.getPaidBy(), "Expense \"" + expense.getDescription() + "\" became VERIFIED");
                notificationService.createNotification(expense.getPaidBy(), "EXPENSE_CREATED", "Your expense \"" + expense.getDescription() + "\" is now VERIFIED.");
            }
        } else if ("REJECTED".equalsIgnoreCase(status)) {
            expense.setVerificationStatus("UNDER_REVIEW");
            expenseRepository.save(expense);
            activityLogService.log(user, user.getFullName() + " rejected expense \"" + expense.getDescription() + "\"");
            notificationService.createNotification(expense.getPaidBy(), "EXPENSE_REJECTED", user.getFullName() + " rejected expense \"" + expense.getDescription() + "\". Reason: " + comment);
        } else if ("REQUESTED_PROOF".equalsIgnoreCase(status)) {
            activityLogService.log(user, user.getFullName() + " requested proof for expense \"" + expense.getDescription() + "\"");
            notificationService.createNotification(expense.getPaidBy(), "PROOF_REQUESTED", user.getFullName() + " requested receipt proof for expense \"" + expense.getDescription() + "\"");
        }
    }

    @Transactional
    public void uploadReceipt(Long expenseId, String receiptUrl, User actor) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));

        if (!expense.getPaidBy().getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Only the payer can upload proof.");
        }

        expense.setReceiptUrl(receiptUrl);
        expenseRepository.save(expense);
        activityLogService.log(actor, actor.getFullName() + " uploaded receipt proof for expense \"" + expense.getDescription() + "\"");

        // Update REQUESTED_PROOF approvals to SUBMITTED and notify requesters
        List<ExpenseApproval> approvals = expenseApprovalRepository.findByExpenseId(expenseId);
        for (ExpenseApproval approval : approvals) {
            if ("REQUESTED_PROOF".equalsIgnoreCase(approval.getStatus())) {
                approval.setStatus("SUBMITTED");
                expenseApprovalRepository.save(approval);
                notificationService.createNotification(approval.getUser(), "PROOF_SUBMITTED",
                        actor.getFullName() + " uploaded receipt proof for expense \"" + expense.getDescription() + "\". Please review/approve.");
            }
        }
    }
}
