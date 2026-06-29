package com.splitwisemoney;

import com.splitwisemoney.entity.Expense;
import com.splitwisemoney.entity.Group;
import com.splitwisemoney.entity.Settlement;
import com.splitwisemoney.entity.User;
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

@SpringBootTest(classes = SplitWiseMoneyApplication.class)
@Transactional
class SplitWiseMoneyApplicationTests {

    @Autowired
    private UserService userService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

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

        expenseService.addExpense(
                group.getId(),
                rahul.getId(),
                new BigDecimal("1200.00"),
                "Cab",
                "Travel",
                LocalDate.now(),
                shares1,
                rahul
        );

        // 5. Priya paid ₹800 for Dinner (split equally)
        // 800 / 3 = 266.67, 266.67, 266.66
        Map<Long, BigDecimal> shares2 = new HashMap<>();
        shares2.put(rahul.getId(), new BigDecimal("266.67"));
        shares2.put(priya.getId(), new BigDecimal("266.67"));
        shares2.put(kiran.getId(), new BigDecimal("266.66"));

        expenseService.addExpense(
                group.getId(),
                priya.getId(),
                new BigDecimal("800.00"),
                "Dinner",
                "Food",
                LocalDate.now(),
                shares2,
                priya
        );

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
}
