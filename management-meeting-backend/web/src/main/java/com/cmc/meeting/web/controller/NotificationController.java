package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.notification.NotificationDTO;
import com.cmc.meeting.application.port.service.NotificationService;
import com.cmc.meeting.domain.port.repository.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    // Helper
    private Long getUserId(Object principal) {
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof Jwt) {
            username = ((Jwt) principal).getSubject();
        } else {
            throw new RuntimeException("Unknown Principal");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found")).getId();
    }

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getMyNotifications(
            @AuthenticationPrincipal Object principal,
            @PageableDefault(size = 10) Pageable pageable) {
        Long currentUserId = getUserId(principal);
        Page<NotificationDTO> page = notificationService.getMyNotifications(currentUserId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal Object principal) {
        Long currentUserId = getUserId(principal);
        return ResponseEntity.ok(notificationService.getUnreadCount(currentUserId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        Long currentUserId = getUserId(principal);
        return ResponseEntity.ok(notificationService.markAsRead(id, currentUserId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal Object principal) {
        Long currentUserId = getUserId(principal);
        notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.ok("Đã đánh dấu tất cả là đã đọc.");
    }
}