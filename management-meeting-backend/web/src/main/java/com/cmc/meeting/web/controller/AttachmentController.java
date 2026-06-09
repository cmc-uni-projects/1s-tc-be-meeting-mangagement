package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.attachment.AttachmentDTO;
import com.cmc.meeting.application.port.service.AttachmentService;
import com.cmc.meeting.domain.port.repository.UserRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "Attachment API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final UserRepository userRepository;

    public AttachmentController(AttachmentService attachmentService, UserRepository userRepository) {
        this.attachmentService = attachmentService;
        this.userRepository = userRepository;
    }

    private Long getUserId(Object principal) {
        String username;
        if (principal instanceof UserDetails) username = ((UserDetails) principal).getUsername();
        else if (principal instanceof Jwt) username = ((Jwt) principal).getSubject();
        else throw new RuntimeException("Auth error");
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    @PostMapping(value = "/upload/{meetingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @PathVariable Long meetingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Object principal) throws IOException {
        Long currentUserId = getUserId(principal);
        AttachmentDTO dto = attachmentService.uploadAttachment(meetingId, currentUserId, file);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<?> deleteAttachment(
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal Object principal) throws IOException {
        Long currentUserId = getUserId(principal);
        attachmentService.deleteAttachment(attachmentId, currentUserId);
        return ResponseEntity.ok("Đã xóa file đính kèm.");
    }
}