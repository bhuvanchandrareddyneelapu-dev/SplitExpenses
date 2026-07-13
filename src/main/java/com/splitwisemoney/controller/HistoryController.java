package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.ActivityLog;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.ActivityLogService;
import com.splitwisemoney.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/history", "/api/activity"})
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "History", description = "Endpoints for user activity logs history")
public class HistoryController {

    private final ActivityLogService activityLogService;
    private final UserService userService;

    public HistoryController(ActivityLogService activityLogService, UserService userService) {
        this.activityLogService = activityLogService;
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping
    @Operation(summary = "Get paginated user activity logs history")
    public ResponseEntity<Page<ActivityLogDto>> getUserLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        User user = getAuthenticatedUser();
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction sortDirection = Sort.Direction.DESC;
        if (sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])) {
            sortDirection = Sort.Direction.ASC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));
        Page<ActivityLog> logsPage = activityLogService.getUserLogs(user, pageable);
        Page<ActivityLogDto> responsePage = logsPage.map(log -> new ActivityLogDto(
                log.getId(),
                log.getUser().getFullName(),
                log.getAction(),
                log.getCreatedAt()
        ));

        return ResponseEntity.ok(responsePage);
    }
}
