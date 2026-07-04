package com.splitwisemoney.repository;

import com.splitwisemoney.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findByGroupId(Long groupId, Pageable pageable);
    Page<Expense> findByGroupIdAndCategory(Long groupId, String category, Pageable pageable);
    List<Expense> findByGroupId(Long groupId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.paidBy.id = :userId AND e.verificationStatus = 'VERIFIED'")
    BigDecimal sumAmountByGroupIdAndPaidById(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.paidBy.id = :userId AND e.verificationStatus = 'VERIFIED'")
    BigDecimal sumAmountByPaidById(@Param("userId") Long userId);

    long countByPaidByIdAndVerificationStatus(Long paidById, String verificationStatus);
}
