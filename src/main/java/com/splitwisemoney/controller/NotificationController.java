package com.splitwisemoney.controller;

import com.splitwisemoney.dto.*;
import com.splitwisemoney.entity.Notification;
import com.splitwisemoney.entity.User;
import com.splitwisemoney.service.NotificationService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Endpoints for managing user alerts and notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    @GetMapping
    @Operation(summary = "Get paginated user notifications")
    public ResponseEntity<Page<NotificationDto>> getUserNotifications(
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
        Page<Notification> notificationsPage = notificationService.getUserNotifications(user, pageable);
        Page<NotificationDto> responsePage = notificationsPage.map(n -> new NotificationDto(
                n.getId(),
                n.getMessage(),
                n.getType(),
                n.getIsRead(),
                n.getCreatedAt()
        ));

        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications for current user")
    public ResponseEntity<Long> getUnreadCount() {
        User user = getAuthenticatedUser();
        long count = notificationService.getUnreadCount(user);
        return ResponseEntity.ok(count);
    }

    @PostMapping("/mark-read")
    @Operation(summary = "Mark all notifications for the current user as read")
    public ResponseEntity<String> markAllAsRead() {
        User user = getAuthenticatedUser();
        notificationService.markAllAsRead(user);
        return ResponseEntity.ok("All notifications marked as read");
    }
}
