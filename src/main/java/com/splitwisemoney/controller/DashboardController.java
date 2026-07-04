package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.DashboardService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Endpoints for user financial overview metrics")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping
    @Operation(summary = "Get user summary statistics (total paid, owed, receive, group counts, recent logs)")
    public ResponseEntity<DashboardResponse> getDashboard() {
        User user = getAuthenticatedUser();
        DashboardService.DashboardData data = dashboardService.getDashboardData(user);

        List<ActivityLogDto> recentActivities = data.getRecentActivities().stream()
                .map(log -> new ActivityLogDto(
                        log.getId(),
                        log.getUser().getFullName(),
                        log.getAction(),
                        log.getCreatedAt()
                ))
                .collect(Collectors.toList());

        DashboardResponse response = new DashboardResponse(
                data.getTotalPaid(),
                data.getTotalOwed(),
                data.getAmountToReceive(),
                data.getTotalGroups(),
                recentActivities,
                data.getPendingInvitations(),
                data.getPendingApprovals(),
                data.getRejectedExpenses(),
                data.getVerifiedExpenses(),
                data.getPendingProofRequests()
        );

        return ResponseEntity.ok(response);
    }
}
