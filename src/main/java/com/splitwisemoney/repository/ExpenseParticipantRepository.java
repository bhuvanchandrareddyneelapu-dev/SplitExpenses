package com.splitwisemoney.repository;

import com.splitwisemoney.entity.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    List<ExpenseParticipant> findByExpenseId(Long expenseId);
    List<ExpenseParticipant> findByUserId(Long userId);
    void deleteByExpenseId(Long expenseId);
}
