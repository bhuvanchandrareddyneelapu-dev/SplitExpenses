package com.splitwisemoney;

import com.splitwisemoney.entity.Expense;
import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.Settlement;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.DashboardService;
import com.splitwisemoney.service.ExpenseService;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.SettlementService;
import com.splitwisemoney.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.splitwisemoney.entity.GroupInvitation;
import com.splitwisemoney.repository.GroupInvitationRepository;
import java.time.LocalDateTime;

@SpringBootTest(classes = SplitWiseMoneyApplication.class)
@Transactional
class SplitWiseMoneyApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    void testSettlementSimplificationMath() {
        // 1. Register users Rahul, Priya, Kiran
        User rahul = userService.registerUser("Rahul", "rahul@test.com", "password123");
        User priya = userService.registerUser("Priya", "priya@test.com", "password123");
        User kiran = userService.registerUser("Kiran", "kiran@test.com", "password123");

        // 2. Rahul creates the group "Goa Trip"
        Group group = groupService.createGroup("Goa Trip", rahul);

        // 3. Add Priya and Kiran to the group
        groupService.addMemberByEmail(group.getId(), "priya@test.com", rahul);
        groupService.addMemberByEmail(group.getId(), "kiran@test.com", rahul);

        // 4. Rahul paid ₹1200 for Cab (split equally)
        // 1200 / 3 = 400.00 each
        Map<Long, BigDecimal> shares1 = new HashMap<>();
        shares1.put(rahul.getId(), new BigDecimal("400.00"));
        shares1.put(priya.getId(), new BigDecimal("400.00"));
        shares1.put(kiran.getId(), new BigDecimal("400.00"));

        Expense expense1 = expenseService.addExpense(
                group.getId(),
                rahul.getId(),
                new BigDecimal("1200.00"),
                "Cab",
                "Travel",
                LocalDate.now(),
                shares1,
                rahul
        );
        expenseService.approveOrRejectExpense(expense1.getId(), "APPROVED", "", priya);
        expenseService.approveOrRejectExpense(expense1.getId(), "APPROVED", "", kiran);

        // 5. Priya paid ₹800 for Dinner (split equally)
        // 800 / 3 = 266.67, 266.67, 266.66
        Map<Long, BigDecimal> shares2 = new HashMap<>();
        shares2.put(rahul.getId(), new BigDecimal("266.67"));
        shares2.put(priya.getId(), new BigDecimal("266.67"));
        shares2.put(kiran.getId(), new BigDecimal("266.66"));

        Expense expense2 = expenseService.addExpense(
                group.getId(),
                priya.getId(),
                new BigDecimal("800.00"),
                "Dinner",
                "Food",
                LocalDate.now(),
                shares2,
                priya
        );
        expenseService.approveOrRejectExpense(expense2.getId(), "APPROVED", "", rahul);
        expenseService.approveOrRejectExpense(expense2.getId(), "APPROVED", "", kiran);

        // 6. Calculate settlements
        List<Settlement> settlements = settlementService.calculateOwedSettlements(group.getId());

        // We expect exactly 2 settlements:
        // Kiran pays Rahul ₹533.33
        // Kiran pays Priya ₹133.33 (or 133.34 depending on rounding)
        assertEquals(2, settlements.size(), "Should simplify to exactly 2 transactions");

        boolean foundKiranToRahul = false;
        boolean foundKiranToPriya = false;

        for (Settlement s : settlements) {
            assertEquals(kiran.getId(), s.getFromUser().getId(), "Kiran must be the debtor paying both");
            
            if (s.getToUser().getId().equals(rahul.getId())) {
                foundKiranToRahul = true;
                // Assert amount is ₹533.33 (allowing tiny rounding delta)
                double amount = s.getAmount().doubleValue();
                assertTrue(Math.abs(amount - 533.33) <= 0.05, "Kiran should pay Rahul approx 533.33, got: " + amount);
            } else if (s.getToUser().getId().equals(priya.getId())) {
                foundKiranToPriya = true;
                // Assert amount is ₹133.33 (allowing tiny rounding delta)
                double amount = s.getAmount().doubleValue();
                assertTrue(Math.abs(amount - 133.33) <= 0.05, "Kiran should pay Priya approx 133.33, got: " + amount);
            }
        }

        assertTrue(foundKiranToRahul, "Should have settlement from Kiran to Rahul");
        assertTrue(foundKiranToPriya, "Should have settlement from Kiran to Priya");
    }

    @Test
    void testDetailedExpenseSplittingAndSettlementAndDashboard() {
        // 1. Register users Alice, Bob, Charlie
        User alice = userService.registerUser("Alice", "alice@test.com", "Password@123");
        User bob = userService.registerUser("Bob", "bob@test.com", "Password@123");
        User charlie = userService.registerUser("Charlie", "charlie@test.com", "Password@123");

        // 2. Alice creates the group "Shared Apartment"
        Group group = groupService.createGroup("Shared Apartment", alice);

        // 3. Add Bob and Charlie to the group
        groupService.addMemberByEmail(group.getId(), "bob@test.com", alice);
        groupService.addMemberByEmail(group.getId(), "charlie@test.com", alice);

        // 4. Alice pays an Equal Split expense of ₹1500 (Alice, Bob, Charlie are participants)
        // ₹500 each
        Map<Long, BigDecimal> shares1 = new HashMap<>();
        shares1.put(alice.getId(), new BigDecimal("500.00"));
        shares1.put(bob.getId(), new BigDecimal("500.00"));
        shares1.put(charlie.getId(), new BigDecimal("500.00"));

        Expense expense1 = expenseService.addExpense(
                group.getId(),
                alice.getId(),
                new BigDecimal("1500.00"),
                "Gas Bill",
                "Utilities",
                LocalDate.now(),
                shares1,
                alice
        );
        expenseService.approveOrRejectExpense(expense1.getId(), "APPROVED", "", bob);
        expenseService.approveOrRejectExpense(expense1.getId(), "APPROVED", "", charlie);

        // 5. Bob pays a Percentage Split expense of ₹1000
        // Alice 50% (₹500), Bob 30% (₹300), Charlie 20% (₹200)
        Map<Long, BigDecimal> shares2 = new HashMap<>();
        shares2.put(alice.getId(), new BigDecimal("500.00"));
        shares2.put(bob.getId(), new BigDecimal("300.00"));
        shares2.put(charlie.getId(), new BigDecimal("200.00"));

        Expense expense2 = expenseService.addExpense(
                group.getId(),
                bob.getId(),
                new BigDecimal("1000.00"),
                "Groceries",
                "Food",
                LocalDate.now(),
                shares2,
                bob
        );
        expenseService.approveOrRejectExpense(expense2.getId(), "APPROVED", "", alice);
        expenseService.approveOrRejectExpense(expense2.getId(), "APPROVED", "", charlie);

        // 6. Charlie pays a Custom Split expense of ₹3000
        // Alice: ₹1500, Bob: ₹1000, Charlie: ₹500
        Map<Long, BigDecimal> shares3 = new HashMap<>();
        shares3.put(alice.getId(), new BigDecimal("1500.00"));
        shares3.put(bob.getId(), new BigDecimal("1000.00"));
        shares3.put(charlie.getId(), new BigDecimal("500.00"));

        Expense expense3 = expenseService.addExpense(
                group.getId(),
                charlie.getId(),
                new BigDecimal("3000.00"),
                "Rent",
                "Rent",
                LocalDate.now(),
                shares3,
                charlie
        );
        expenseService.approveOrRejectExpense(expense3.getId(), "APPROVED", "", alice);
        expenseService.approveOrRejectExpense(expense3.getId(), "APPROVED", "", bob);

        // 7. Verify Dashboard data for Alice before settlements
        // Alice paid ₹1500 total.
        // Balance in group:
        // Ex1: Alice paid 1500, share 500 => +1000
        // Ex2: Alice share 500 => -500
        // Ex3: Alice share 1500 => -1500
        // Net: +1000 - 500 - 1500 = -1000 (Owes ₹1000)
        DashboardService.DashboardData aliceData = dashboardService.getDashboardData(alice);
        assertEquals(new BigDecimal("1500.00"), aliceData.getTotalPaid());
        assertEquals(new BigDecimal("1000.00"), aliceData.getTotalOwed());
        assertEquals(BigDecimal.ZERO.setScale(2), aliceData.getAmountToReceive());

        // 8. Calculate settlements for the group
        // Net Balances:
        // Alice: -1000
        // Bob: Paid 1000, Share 1: 500, Share 2: 300, Share 3: 1000 => Net: 1000 - 1800 = -800 (Owes ₹800)
        // Charlie: Paid 3000, Share 1: 500, Share 2: 200, Share 3: 500 => Net: 3000 - 1200 = +1800 (Receives ₹1800)
        // Expected Settlements:
        // Alice owes Charlie ₹1000
        // Bob owes Charlie ₹800
        List<Settlement> settlements = settlementService.calculateOwedSettlements(group.getId());
        assertEquals(2, settlements.size());

        Settlement bobToCharlie = null;
        for (Settlement s : settlements) {
            if (s.getFromUser().getId().equals(bob.getId())) {
                bobToCharlie = s;
            }
        }
        assertTrue(bobToCharlie != null, "Should find settlement from Bob to Charlie");
        assertEquals(new BigDecimal("800.00"), bobToCharlie.getAmount());

        // 9. Record and settle Bob's debt to Charlie
        Settlement recorded = settlementService.createSettlement(
                group.getId(),
                bob.getId(),
                charlie.getId(),
                new BigDecimal("800.00"),
                "PENDING",
                bob
        );
        assertEquals("PENDING", recorded.getStatus());

        Settlement settled = settlementService.markAsSettled(recorded.getId(), charlie);
        assertEquals("SETTLED", settled.getStatus());

        // 10. Verify Bob's Dashboard Data post-settlement
        // Bob paid: 1000 (expense) + 800 (settlement is NOT counted in totalPaid, but reduces totalOwed to 0)
        DashboardService.DashboardData bobData = dashboardService.getDashboardData(bob);
        assertEquals(new BigDecimal("1000.00"), bobData.getTotalPaid());
        assertEquals(BigDecimal.ZERO.setScale(2), bobData.getTotalOwed());
        assertEquals(BigDecimal.ZERO.setScale(2), bobData.getAmountToReceive());
    }

    @Test
    void testGroupInvitationAndRejectionWorkflows() {
        // 1. Register users Dave and Eve
        User dave = userService.registerUser("Dave", "dave@test.com", "Password@123");
        User eve = userService.registerUser("Eve", "eve@test.com", "Password@123");

        // 2. Dave creates a group "Road Trip"
        Group group = groupService.createGroup("Road Trip", dave);

        // 3. Dave invites Eve
        com.splitwisemoney.entity.GroupInvitation invitation = groupService.inviteMemberByEmail(group.getId(), "eve@test.com", dave);
        assertEquals("PENDING", invitation.getStatus());

        // 4. Verify Eve has a pending invitation
        List<com.splitwisemoney.entity.GroupInvitation> pending = groupService.getPendingInvitations(eve.getId());
        assertEquals(1, pending.size());
        assertEquals(group.getId(), pending.get(0).getGroup().getId());

        // 5. Eve accepts the invitation
        groupService.acceptInvitation(invitation.getId(), eve);
        assertTrue(groupService.isMember(group.getId(), eve.getId()), "Eve should now be a member");

        // 6. Dave adds an expense split between Dave and Eve
        Map<Long, BigDecimal> shares = new HashMap<>();
        shares.put(dave.getId(), new BigDecimal("100.00"));
        shares.put(eve.getId(), new BigDecimal("100.00"));

        Expense expense = expenseService.addExpense(
                group.getId(),
                dave.getId(),
                new BigDecimal("200.00"),
                "Tolls",
                "Travel",
                LocalDate.now(),
                shares,
                dave
        );
        assertEquals("PENDING", expense.getVerificationStatus());

        // 7. Eve rejects the expense
        expenseService.approveOrRejectExpense(expense.getId(), "REJECTED", "Wrong amount", eve);
        
        // Reload expense to check status
        Expense reloaded = expenseService.getExpenseById(expense.getId()).orElseThrow();
        assertEquals("UNDER_REVIEW", reloaded.getVerificationStatus());
    }

    @Test
    void testCompleteGroupInvitationSystem() {
        // 1. Setup users
        User inviter = userService.registerUser("Inviter User", "inviter@test.com", "Password123");
        User existingUser = userService.registerUser("Existing User", "existing@test.com", "Password123");
        User outsider = userService.registerUser("Outsider User", "outsider@test.com", "Password123");
        
        Group group = groupService.createGroup("Invitation Test Group", inviter);
        
        // 2. Existing user invitation
        GroupInvitation existingInv = groupService.inviteMemberByEmail(group.getId(), "existing@test.com", inviter);
        org.junit.jupiter.api.Assertions.assertNotNull(existingInv.getInvitationToken());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", existingInv.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(existingUser.getId(), existingInv.getReceiver().getId());
        
        // 3. Duplicate invitation — GroupService resends the email instead of throwing.
        // The same invitation record is returned with its status still PENDING.
        GroupInvitation resent = groupService.inviteMemberByEmail(group.getId(), "existing@test.com", inviter);
        org.junit.jupiter.api.Assertions.assertEquals(existingInv.getId(), resent.getId(),
                "Duplicate invite should return the same invitation record (resend path)");
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", resent.getStatus());

        // 4. Accept invitation via token
        groupService.acceptInvitationByToken(existingInv.getInvitationToken(), existingUser);
        org.junit.jupiter.api.Assertions.assertEquals("ACCEPTED", existingInv.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(groupService.isMember(group.getId(), existingUser.getId()));
        
        // 5. Already member invitation prevention
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            groupService.inviteMemberByEmail(group.getId(), "existing@test.com", inviter);
        });

        // 6. Unauthorized invitation (inviter must be member)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            groupService.inviteMemberByEmail(group.getId(), "outsider@test.com", outsider);
        });

        // 7. New user invitation
        GroupInvitation newInv = groupService.inviteMemberByEmail(group.getId(), "newuser@test.com", inviter);
        org.junit.jupiter.api.Assertions.assertNull(newInv.getReceiver());
        org.junit.jupiter.api.Assertions.assertEquals("newuser@test.com", newInv.getInviteeEmail());
        
        // 8. Auto-accept invitation upon new user registration
        User newUser = userService.registerUser("New Registered User", "newuser@test.com", "Password123");
        org.junit.jupiter.api.Assertions.assertTrue(groupService.isMember(group.getId(), newUser.getId()));
        GroupInvitation updatedNewInv = groupService.getInvitationByToken(newInv.getInvitationToken());
        org.junit.jupiter.api.Assertions.assertEquals("ACCEPTED", updatedNewInv.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(newUser.getId(), updatedNewInv.getReceiver().getId());

        // 9. Reject invitation via token
        User rejectee = userService.registerUser("Rejectee User", "rejectee@test.com", "Password123");
        GroupInvitation rejectInv = groupService.inviteMemberByEmail(group.getId(), "rejectee@test.com", inviter);
        groupService.rejectInvitationByToken(rejectInv.getInvitationToken(), rejectee);
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED", groupService.getInvitationByToken(rejectInv.getInvitationToken()).getStatus());

        // 10. Invalid token acceptance
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            groupService.acceptInvitationByToken("invalid-uuid-token", rejectee);
        });

        // 11. Expired invitation test
        GroupInvitation expiredInv = groupService.inviteMemberByEmail(group.getId(), "expired@test.com", inviter);
        expiredInv.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        groupInvitationRepository.saveAndFlush(expiredInv);
        // We trigger it
        User expiredUser = userService.registerUser("Expired User", "expired@test.com", "Password123");
        GroupInvitation updatedExpiredInv = groupService.getInvitationByToken(expiredInv.getInvitationToken());
        org.junit.jupiter.api.Assertions.assertEquals("EXPIRED", updatedExpiredInv.getStatus());
        org.junit.jupiter.api.Assertions.assertFalse(groupService.isMember(group.getId(), expiredUser.getId()));
    }
}
