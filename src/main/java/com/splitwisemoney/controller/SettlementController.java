package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.Settlement;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.GroupService;
import com.splitwisemoney.service.SettlementService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settlements")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Settlements", description = "Endpoints for debt simplification calculations and payment tracking")
public class SettlementController {

    private final SettlementService settlementService;
    private final UserService userService;
    private final GroupService groupService;

    public SettlementController(SettlementService settlementService, UserService userService, GroupService groupService) {
        this.settlementService = settlementService;
        this.userService = userService;
        this.groupService = groupService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    private SettlementResponse mapToSettlementResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getFromUser().getId(),
                settlement.getFromUser().getFullName(),
                settlement.getToUser().getId(),
                settlement.getToUser().getFullName(),
                settlement.getAmount(),
                settlement.getStatus(),
                settlement.getCreatedAt()
        );
    }

    @GetMapping("/group/{groupId}/owed")
    @Operation(summary = "Calculate simplified owed debts for members of a group (minimum transactions)")
    public ResponseEntity<List<SettlementResponse>> getOwedSettlements(@PathVariable Long groupId) {
        User user = getAuthenticatedUser();
        if (!groupService.isMember(groupId, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        List<Settlement> settlements = settlementService.calculateOwedSettlements(groupId);
        List<SettlementResponse> response = settlements.stream()
                .map(this::mapToSettlementResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/group/{groupId}")
    @Operation(summary = "Record a settlement payment between group members")
    public ResponseEntity<SettlementResponse> createSettlement(@PathVariable Long groupId, @Valid @RequestBody SettlementRequest request) {
        User actor = getAuthenticatedUser();
        Settlement settlement = settlementService.createSettlement(
                groupId,
                request.getFromUserId(),
                request.getToUserId(),
                request.getAmount(),
                request.getStatus(),
                actor
        );
        return ResponseEntity.ok(mapToSettlementResponse(settlement));
    }

    @PutMapping("/{settlementId}/settle")
    @Operation(summary = "Mark a pending settlement payment as SETTLED")
    public ResponseEntity<SettlementResponse> markAsSettled(@PathVariable Long settlementId) {
        User actor = getAuthenticatedUser();
        Settlement settlement = settlementService.markAsSettled(settlementId, actor);
        return ResponseEntity.ok(mapToSettlementResponse(settlement));
    }

    @GetMapping("/group/{groupId}")
    @Operation(summary = "List all recorded settlement transactions in a group")
    public ResponseEntity<List<SettlementResponse>> getGroupSettlements(@PathVariable Long groupId) {
        User user = getAuthenticatedUser();
        if (!groupService.isMember(groupId, user.getId())) {
            throw new IllegalArgumentException("You are not a member of this group.");
        }
        List<Settlement> settlements = settlementService.getGroupSettlements(groupId);
        List<SettlementResponse> response = settlements.stream()
                .map(this::mapToSettlementResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
