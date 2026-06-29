package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.Expense;
import com.splitwisemoney.entity.ExpenseParticipant;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.ExpenseService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/expenses")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Expenses", description = "Endpoints for managing group expenses and participant splits")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserService userService;

    public ExpenseController(ExpenseService expenseService, UserService userService) {
        this.expenseService = expenseService;
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        List<ExpenseParticipant> participants = expenseService.getExpenseParticipants(expense.getId());
        List<ParticipantShareDto> participantDtos = participants.stream()
                .map(p -> new ParticipantShareDto(
                        p.getUser().getId(),
                        p.getUser().getFullName(),
                        p.getUser().getEmail(),
                        p.getShareAmount()
                ))
                .collect(Collectors.toList());

        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getCategory(),
                expense.getExpenseDate(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getFullName(),
                participantDtos
        );
    }

    @PostMapping("/group/{groupId}")
    @Operation(summary = "Add a new expense to a group")
    public ResponseEntity<ExpenseResponse> addExpense(@PathVariable Long groupId, @Valid @RequestBody ExpenseRequest request) {
        User actor = getAuthenticatedUser();
        Expense expense = expenseService.addExpense(
                groupId,
                request.getPaidById(),
                request.getAmount(),
                request.getDescription(),
                request.getCategory(),
                request.getExpenseDate(),
                request.getParticipantShares(),
                actor
        );
        return ResponseEntity.ok(mapToExpenseResponse(expense));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an existing expense")
    public ResponseEntity<ExpenseResponse> editExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        User actor = getAuthenticatedUser();
        Expense expense = expenseService.editExpense(
                id,
                request.getAmount(),
                request.getDescription(),
                request.getCategory(),
                request.getExpenseDate(),
                request.getParticipantShares(),
                actor
        );
        return ResponseEntity.ok(mapToExpenseResponse(expense));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an expense")
    public ResponseEntity<String> deleteExpense(@PathVariable Long id) {
        User actor = getAuthenticatedUser();
        expenseService.deleteExpense(id, actor);
        return ResponseEntity.ok("Expense deleted successfully");
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "Get paginated list of group expenses")
    public ResponseEntity<Page<ExpenseResponse>> getGroupExpenses(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate,desc") String sort) {
        
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])) {
            sortDirection = Sort.Direction.ASC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));
        Page<Expense> expensePage = expenseService.getGroupExpenses(groupId, pageable);
        Page<ExpenseResponse> responsePage = expensePage.map(this::mapToExpenseResponse);
        return ResponseEntity.ok(responsePage);
    }
}
