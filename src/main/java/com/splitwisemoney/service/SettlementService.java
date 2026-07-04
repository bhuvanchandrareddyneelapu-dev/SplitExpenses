package com.splitwisemoney.service;

import com.splitwisemoney.entity.*;
import com.splitwisemoney.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    public SettlementService(SettlementRepository settlementRepository,
                             GroupRepository groupRepository,
                             GroupMemberRepository groupMemberRepository,
                             UserRepository userRepository,
                             ExpenseRepository expenseRepository,
                             ExpenseParticipantRepository expenseParticipantRepository,
                             ActivityLogService activityLogService,
                             NotificationService notificationService) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseParticipantRepository = expenseParticipantRepository;
        this.activityLogService = activityLogService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<Settlement> calculateOwedSettlements(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        List<Settlement> recordedSettlements = settlementRepository.findByGroupId(groupId);

        // 1. Initialize balances for all group members
        Map<Long, BigDecimal> balances = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();
        for (GroupMember member : members) {
            balances.put(member.getUser().getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            userMap.put(member.getUser().getId(), member.getUser());
        }

        // 2. Add credit for expenses paid, and subtract debit for expense shares
        for (Expense expense : expenses) {
            if (!"VERIFIED".equalsIgnoreCase(expense.getVerificationStatus())) {
                continue;
            }
            Long payeeId = expense.getPaidBy().getId();
            BigDecimal amount = expense.getAmount();

            // Credit the payee
            if (balances.containsKey(payeeId)) {
                balances.put(payeeId, balances.get(payeeId).add(amount));
            }

            // Debit the participants
            List<ExpenseParticipant> participants = expenseParticipantRepository.findByExpenseId(expense.getId());
            for (ExpenseParticipant participant : participants) {
                Long pId = participant.getUser().getId();
                if (balances.containsKey(pId)) {
                    balances.put(pId, balances.get(pId).subtract(participant.getShareAmount()));
                }
            }
        }

        // 3. Adjust balances based on recorded SETTLED transactions
        for (Settlement settlement : recordedSettlements) {
            if ("SETTLED".equalsIgnoreCase(settlement.getStatus())) {
                Long fromId = settlement.getFromUser().getId();
                Long toId = settlement.getToUser().getId();
                BigDecimal amount = settlement.getAmount();

                // From user paid money, so their debt decreases (balance goes up towards 0)
                if (balances.containsKey(fromId)) {
                    balances.put(fromId, balances.get(fromId).add(amount));
                }

                // To user received money, so their credit decreases (balance goes down towards 0)
                if (balances.containsKey(toId)) {
                    balances.put(toId, balances.get(toId).subtract(amount));
                }
            }
        }

        // 4. Separate debtors and creditors
        // We use lists and sort them
        List<UserBalance> debtors = new ArrayList<>();
        List<UserBalance> creditors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            Long userId = entry.getKey();
            BigDecimal balance = entry.getValue();

            // Ignore tiny rounding remnants below 0.01
            if (balance.compareTo(new BigDecimal("0.01")) > 0) {
                creditors.add(new UserBalance(userMap.get(userId), balance));
            } else if (balance.compareTo(new BigDecimal("-0.01")) < 0) {
                debtors.add(new UserBalance(userMap.get(userId), balance.negate())); // Store absolute debt
            }
        }

        List<Settlement> owedSettlements = new ArrayList<>();

        // Greedy simplification algorithm
        // Sort creditors descending, debtors descending by absolute debt
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            // Sort to always match largest debtor and largest creditor
            debtors.sort((a, b) -> b.balance.compareTo(a.balance));
            creditors.sort((a, b) -> b.balance.compareTo(a.balance));

            UserBalance debtor = debtors.get(0);
            UserBalance creditor = creditors.get(0);

            BigDecimal settleAmount = debtor.balance.min(creditor.balance);

            // Create a calculated (unpersisted) settlement entry
            Settlement settlement = new Settlement(group, debtor.user, creditor.user, settleAmount, "PENDING");
            owedSettlements.add(settlement);

            debtor.balance = debtor.balance.subtract(settleAmount);
            creditor.balance = creditor.balance.subtract(settleAmount);

            if (debtor.balance.compareTo(new BigDecimal("0.01")) < 0) {
                debtors.remove(0);
            }
            if (creditor.balance.compareTo(new BigDecimal("0.01")) < 0) {
                creditors.remove(0);
            }
        }

        return owedSettlements;
    }

    @Transactional
    public Settlement createSettlement(Long groupId, Long fromUserId, Long toUserId, BigDecimal amount, String status, User actor) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, actor.getId())) {
            throw new IllegalArgumentException("Only group members can record settlements.");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, fromUserId) || !groupMemberRepository.existsByGroupIdAndUserId(groupId, toUserId)) {
            throw new IllegalArgumentException("Both settlement parties must be members of the group.");
        }

        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new IllegalArgumentException("Debtor user not found"));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("Creditor user not found"));

        Settlement settlement = new Settlement(group, fromUser, toUser, amount, status);
        Settlement saved = settlementRepository.save(settlement);

        if ("SETTLED".equalsIgnoreCase(status)) {
            activityLogService.log(actor, fromUser.getFullName() + " settled ₹" + amount + " to " + toUser.getFullName());
            notificationService.createNotification(toUser, "SETTLEMENT_COMPLETED", 
                    fromUser.getFullName() + " recorded a settlement of ₹" + amount + " to you.");
            notificationService.createNotification(fromUser, "SETTLEMENT_COMPLETED", 
                    "You settled ₹" + amount + " to " + toUser.getFullName() + ".");
        } else {
            activityLogService.log(actor, "Recorded pending settlement: " + fromUser.getFullName() + " owe " + toUser.getFullName() + " ₹" + amount);
            notificationService.createNotification(fromUser, "SETTLEMENT_REMINDER", 
                    "Reminder: You owe " + toUser.getFullName() + " ₹" + amount);
        }

        return saved;
    }

    @Transactional
    public Settlement markAsSettled(Long settlementId, User actor) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(settlement.getGroup().getId(), actor.getId())) {
            throw new IllegalArgumentException("Only group members can settle debts.");
        }

        settlement.setStatus("SETTLED");
        Settlement updated = settlementRepository.save(settlement);

        activityLogService.log(actor, settlement.getFromUser().getFullName() + " settled ₹" + settlement.getAmount() + " to " + settlement.getToUser().getFullName());
        notificationService.createNotification(settlement.getToUser(), "SETTLEMENT_COMPLETED", 
                settlement.getFromUser().getFullName() + " settled their debt of ₹" + settlement.getAmount() + " to you.");
        
        return updated;
    }

    @Transactional(readOnly = true)
    public List<Settlement> getGroupSettlements(Long groupId) {
        return settlementRepository.findByGroupId(groupId);
    }

    // Helper static class to track user balances during greedy matching
    private static class UserBalance {
        User user;
        BigDecimal balance;

        UserBalance(User user, BigDecimal balance) {
            this.user = user;
            this.balance = balance.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
