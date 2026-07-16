package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    @Query("SELECT ep FROM ExpenseParticipant ep JOIN FETCH ep.user WHERE ep.expense.id = :expenseId")
    List<ExpenseParticipant> findByExpenseId(@Param("expenseId") Long expenseId);
    List<ExpenseParticipant> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ExpenseParticipant ep WHERE ep.expense.id = :expenseId")
    void deleteByExpenseId(@Param("expenseId") Long expenseId);

    @Query("SELECT COALESCE(SUM(ep.shareAmount), 0) FROM ExpenseParticipant ep WHERE ep.expense.group.id = :groupId AND ep.user.id = :userId AND ep.expense.verificationStatus = 'VERIFIED'")
    BigDecimal sumShareAmountByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
