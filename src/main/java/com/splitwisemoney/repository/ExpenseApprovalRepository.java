package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ExpenseApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseApprovalRepository extends JpaRepository<ExpenseApproval, Long> {
    
    @Query("SELECT ea FROM ExpenseApproval ea JOIN FETCH ea.user WHERE ea.expense.id = :expenseId")
    List<ExpenseApproval> findByExpenseId(@Param("expenseId") Long expenseId);
    
    Optional<ExpenseApproval> findByExpenseIdAndUserId(Long expenseId, Long userId);
    
    List<ExpenseApproval> findByUserIdAndStatus(Long userId, String status);
    
    @Query("SELECT ea FROM ExpenseApproval ea JOIN FETCH ea.user JOIN FETCH ea.expense e JOIN FETCH e.paidBy JOIN FETCH e.group WHERE ea.user.id = :userId AND ea.status IN :statuses")
    List<ExpenseApproval> findByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<String> statuses);
    
    long countByExpensePaidByIdAndStatus(Long paidById, String status);
    
    @Query("SELECT ea FROM ExpenseApproval ea JOIN FETCH ea.user JOIN FETCH ea.expense e JOIN FETCH e.paidBy JOIN FETCH e.group WHERE e.paidBy.id = :paidById AND ea.status = :status")
    List<ExpenseApproval> findByExpensePaidByIdAndStatus(@Param("paidById") Long paidById, @Param("status") String status);
}
