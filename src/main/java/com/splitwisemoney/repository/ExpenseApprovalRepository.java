package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ExpenseApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseApprovalRepository extends JpaRepository<ExpenseApproval, Long> {
    List<ExpenseApproval> findByExpenseId(Long expenseId);
    Optional<ExpenseApproval> findByExpenseIdAndUserId(Long expenseId, Long userId);
    List<ExpenseApproval> findByUserIdAndStatus(Long userId, String status);
    List<ExpenseApproval> findByUserIdAndStatusIn(Long userId, List<String> statuses);
    long countByExpensePaidByIdAndStatus(Long paidById, String status);
    List<ExpenseApproval> findByExpensePaidByIdAndStatus(Long paidById, String status);
}
