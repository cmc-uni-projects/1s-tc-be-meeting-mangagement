package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.request.ApprovalRequest;
import com.cmc.meeting.application.port.service.MeetingService;
import com.cmc.meeting.domain.port.repository.UserRepository;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/meetings")
@Tag(name = "Admin: Meeting Approval API", description = "API duyệt lịch họp")
@PreAuthorize("hasRole('ADMIN')") // Chỉ Admin được gọi
public class AdminMeetingController {

    private final MeetingService meetingService;
    private final UserRepository userRepository;

    public AdminMeetingController(MeetingService meetingService, UserRepository userRepository) {
        this.meetingService = meetingService;
        this.userRepository = userRepository;
    }

    // === HELPER LAI (HYBRID) ===
    private Long getAdminId(Object principal) {
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof Jwt) {
            username = ((Jwt) principal).getSubject();
        } else {
            throw new RuntimeException("Loại xác thực không hỗ trợ: " + principal.getClass());
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found"))
                .getId();
    }

    @PutMapping("/{id}/approval")
    @Operation(summary = "Phê duyệt (Approve) hoặc Từ chối (Reject) cuộc họp")
    public ResponseEntity<?> approveMeeting(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request,
            @AuthenticationPrincipal Object principal) { // <--- SỬA
        
        Long adminId = getAdminId(principal);

        meetingService.processMeetingApproval(id, request.isApproved(), request.getReason(), adminId);
        
        String action = request.isApproved() ? "phê duyệt" : "từ chối";
        return ResponseEntity.ok("Đã " + action + " cuộc họp thành công.");
    }
}