package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.Expense;
import com.splitwisemoney.entity.ExpenseParticipant;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.ExpenseService;
import com.splitwisemoney.service.GroupService;
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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

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
    private final GroupService groupService;

    public ExpenseController(ExpenseService expenseService, UserService userService, GroupService groupService) {
        this.expenseService = expenseService;
        this.userService = userService;
        this.groupService = groupService;
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

        List<com.splitwisemoney.entity.ExpenseApproval> approvals = expenseService.getExpenseApprovals(expense.getId());
        List<ExpenseApprovalResponse> approvalDtos = approvals.stream()
                .map(a -> new ExpenseApprovalResponse(
                        a.getId(),
                        a.getUser().getId(),
                        a.getUser().getFullName(),
                        a.getStatus(),
                        a.getComment(),
                        a.getApprovedAt()
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
                participantDtos,
                expense.getVerificationStatus(),
                expense.getReceiptUrl(),
                approvalDtos
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
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expenseDate,desc") String sort) {
        
        User user = getAuthenticatedUser();
        if (!groupService.isMember(groupId, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])) {
            sortDirection = Sort.Direction.ASC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));
        Page<Expense> expensePage = expenseService.getGroupExpenses(groupId, category, pageable);
        Page<ExpenseResponse> responsePage = expensePage.map(this::mapToExpenseResponse);
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/pending-approvals")
    @Operation(summary = "List all pending expense approvals for current user")
    public ResponseEntity<List<ExpenseApprovalResponse>> getMyPendingApprovals() {
        User user = getAuthenticatedUser();
        List<com.splitwisemoney.entity.ExpenseApproval> approvals = expenseService.getPendingApprovalsForUser(user.getId());
        List<ExpenseApprovalResponse> response = approvals.stream()
                .map(a -> new ExpenseApprovalResponse(
                        a.getExpense().getId(),
                        a.getUser().getId(),
                        a.getExpense().getDescription() + " (Group: " + a.getExpense().getGroup().getGroupName() + ", Paid By: " + a.getExpense().getPaidBy().getFullName() + ", Amount: ₹" + a.getExpense().getAmount() + ")",
                        a.getStatus(),
                        a.getComment(),
                        a.getApprovedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approval")
    @Operation(summary = "Submit approval choice for an expense")
    public ResponseEntity<String> approveExpense(@PathVariable Long id, @Valid @RequestBody ApprovalRequest request) {
        User user = getAuthenticatedUser();
        expenseService.approveOrRejectExpense(id, request.getStatus(), request.getComment(), user);
        return ResponseEntity.ok("Expense decision updated successfully");
    }

    @GetMapping("/{id}/approvals")
    @Operation(summary = "Get list of approvals for an expense")
    public ResponseEntity<List<ExpenseApprovalResponse>> getExpenseApprovals(@PathVariable Long id) {
        List<com.splitwisemoney.entity.ExpenseApproval> approvals = expenseService.getExpenseApprovals(id);
        List<ExpenseApprovalResponse> response = approvals.stream()
                .map(a -> new ExpenseApprovalResponse(
                        a.getId(),
                        a.getUser().getId(),
                        a.getUser().getFullName(),
                        a.getStatus(),
                        a.getComment(),
                        a.getApprovedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/receipt")
    @Operation(summary = "Upload expense receipt proof")
    public ResponseEntity<String> uploadReceipt(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        User user = getAuthenticatedUser();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        Path uploadPath = Paths.get("uploads");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filepath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filepath);

        String receiptUrl = "/uploads/" + filename;
        expenseService.uploadReceipt(id, receiptUrl, user);

        return ResponseEntity.ok("Receipt uploaded successfully");
    }
}
