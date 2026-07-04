package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    List<ExpenseParticipant> findByExpenseId(Long expenseId);
    List<ExpenseParticipant> findByUserId(Long userId);
    void deleteByExpenseId(Long expenseId);

    @Query("SELECT COALESCE(SUM(ep.shareAmount), 0) FROM ExpenseParticipant ep WHERE ep.expense.group.id = :groupId AND ep.user.id = :userId AND ep.expense.verificationStatus = 'VERIFIED'")
    BigDecimal sumShareAmountByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
