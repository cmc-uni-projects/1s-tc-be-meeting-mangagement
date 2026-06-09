package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.meeting.*;
import com.cmc.meeting.application.dto.request.MeetingCreationRequest;
import com.cmc.meeting.application.dto.request.MeetingUpdateRequest;
import com.cmc.meeting.application.dto.response.MeetingDTO;
import com.cmc.meeting.application.dto.timeslot.TimeSlotDTO;
import com.cmc.meeting.application.dto.timeslot.TimeSuggestionRequest;
import com.cmc.meeting.application.port.service.MeetingService;
import com.cmc.meeting.application.port.service.TimeSuggestionService;
import com.cmc.meeting.domain.model.Meeting;
import com.cmc.meeting.domain.model.ParticipantStatus;
import com.cmc.meeting.domain.model.User;
import com.cmc.meeting.domain.port.repository.MeetingRepository;
import com.cmc.meeting.domain.port.repository.UserRepository;
import com.cmc.meeting.infrastructure.persistence.jpa.adapter.GoogleCalendarAdapter;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Cũ
import org.springframework.security.oauth2.jwt.Jwt; // Mới
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meeting API", description = "API quản lý lịch họp")
@SecurityRequirement(name = "bearerAuth")
public class MeetingController {

    private final MeetingService meetingService;
    private final UserRepository userRepository;
    private final TimeSuggestionService timeSuggestionService;
    private final MeetingRepository meetingRepository;
    private final GoogleCalendarAdapter googleCalendarAdapter;

    public MeetingController(MeetingService meetingService,
                             UserRepository userRepository,
                             TimeSuggestionService timeSuggestionService,
                             MeetingRepository meetingRepository,
                             GoogleCalendarAdapter googleCalendarAdapter) {
        this.meetingService = meetingService;
        this.userRepository = userRepository;
        this.timeSuggestionService = timeSuggestionService;
        this.meetingRepository = meetingRepository;
        this.googleCalendarAdapter = googleCalendarAdapter;
    }

    // === HÀM HELPER ĐA NĂNG (HYBRID) ===
    private Long getUserId(Object principal) {
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername(); // Token cũ
        } else if (principal instanceof Jwt) {
            username = ((Jwt) principal).getSubject(); // Token OAuth2
        } else {
            throw new RuntimeException("Loại xác thực không hỗ trợ: " + principal.getClass());
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại: " + username));
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<MeetingDTO> createMeeting(
            @Valid @RequestBody MeetingCreationRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        MeetingDTO createdMeeting = meetingService.createMeeting(request, userId);
        return new ResponseEntity<>(createdMeeting, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelMeeting(
            @PathVariable Long id,
            @Valid @RequestBody MeetingCancelRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        meetingService.cancelMeeting(id, request, userId);
        return ResponseEntity.ok("Đã hủy cuộc họp thành công.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingDTO> getMeetingById(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        MeetingDTO meetingDTO = meetingService.getMeetingById(id, userId);
        return ResponseEntity.ok(meetingDTO);
    }

    @GetMapping("/my-meetings")
    public ResponseEntity<Page<MeetingDTO>> getMyMeetings(
            @AuthenticationPrincipal Object principal,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = getUserId(principal);
        Page<MeetingDTO> meetings = meetingService.getMyMeetings(userId, pageable);
        return ResponseEntity.ok(meetings);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingDTO> updateMeeting(
            @PathVariable Long id,
            @Valid @RequestBody MeetingUpdateRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        MeetingDTO updatedMeeting = meetingService.updateMeeting(id, request, userId);
        return ResponseEntity.ok(updatedMeeting);
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respondToInvitation(
            @PathVariable Long id,
            @Valid @RequestBody MeetingResponseRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        meetingService.respondToInvitation(id, request, userId);
        return ResponseEntity.ok("Đã phản hồi lời mời thành công.");
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkInToMeeting(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        String message = meetingService.checkIn(request, userId);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/suggest-time")
    public ResponseEntity<List<TimeSlotDTO>> suggestTime(
            @Valid @RequestBody TimeSuggestionRequest request) {
        List<TimeSlotDTO> suggestions = timeSuggestionService.suggestTime(request);
        return ResponseEntity.ok(suggestions);
    }

    @DeleteMapping("/series/{seriesId}")
    public ResponseEntity<?> cancelMeetingSeries(
            @PathVariable String seriesId,
            @Valid @RequestBody MeetingCancelRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        meetingService.cancelMeetingSeries(seriesId, request, userId);
        return ResponseEntity.ok("Đã hủy chuỗi thành công.");
    }

    @PutMapping("/series/{seriesId}")
    public ResponseEntity<MeetingDTO> updateMeetingSeries(
            @PathVariable String seriesId,
            @Valid @RequestBody MeetingCreationRequest request,
            @AuthenticationPrincipal Object principal) {
        Long userId = getUserId(principal);
        if (request.getRecurrenceRule() == null) throw new IllegalArgumentException("Thiếu rule lặp lại");
        MeetingDTO firstMeeting = meetingService.updateMeetingSeries(seriesId, request, userId);
        return ResponseEntity.ok(firstMeeting);
    }

    @PostMapping("/check-in/qr")
    public ResponseEntity<String> checkInByQr(
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal Object principal) {
        String qrCode = payload.get("qrCode");
        if (qrCode == null || qrCode.isBlank()) return ResponseEntity.badRequest().body("Mã QR trống");
        Long userId = getUserId(principal);
        meetingService.checkInByQrCode(qrCode, userId);
        return ResponseEntity.ok("Check-in thành công!");
    }

    @GetMapping(value = "/respond-by-link", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> respondByLink(@RequestParam String token, @RequestParam ParticipantStatus status) {
        try {
            String message = meetingService.respondByLink(token, status);
            return ResponseEntity.ok("<html><body><h1>" + message + "</h1></body></html>");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lỗi: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/sync/google")
    public ResponseEntity<Map<String, String>> getGoogleCalendarLink(@PathVariable Long id) {
        String googleLink = meetingService.generateGoogleCalendarLink(id);
        return ResponseEntity.ok(Collections.singletonMap("url", googleLink));
    }

    @GetMapping("/{id}/test-sync-google")
    public ResponseEntity<?> testSyncGoogle(@PathVariable Long id, @AuthenticationPrincipal Object principal) {
        try {
            Long userId = getUserId(principal);
            Meeting meeting = meetingRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Meeting not found"));
            googleCalendarAdapter.pushMeetingToGoogle(userId, meeting);
            return ResponseEntity.ok("Sync OK");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}