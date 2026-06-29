package com.splitwisemoney.repository;

import com.splitwisemoney.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findByGroupId(Long groupId, Pageable pageable);
    List<Expense> findByGroupId(Long groupId);
}
