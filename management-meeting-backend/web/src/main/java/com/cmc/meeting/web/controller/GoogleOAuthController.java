package com.cmc.meeting.web.controller;

import com.cmc.meeting.infrastructure.persistence.jpa.adapter.GoogleAuthService; // sửa nếu tên service khác
import com.cmc.meeting.domain.port.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;           // THÊM DÒNG NÀY
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleOAuthController {

    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;

    public GoogleOAuthController(GoogleAuthService googleAuthService, UserRepository userRepository) {
        this.googleAuthService = googleAuthService;
        this.userRepository = userRepository;
    }

    // 1. Trả về link đăng nhập Google (không cần auth)
    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> authorize() {
        return ResponseEntity.ok(Collections.singletonMap("url", googleAuthService.getAuthorizationUrl()));
    }

    // 2. Nhận code từ frontend → cần xác thực người dùng đang đăng nhập
    @PostMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal Object principal) {                    // ĐỔI THÀNH Object

        String code = payload.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Code không được để trống");
        }

        Long userId = getUserId(principal);  // dùng helper hybrid

        googleAuthService.exchangeAndSaveToken(code, userId);
        return ResponseEntity.ok("Liên kết tài khoản Google thành công!");
    }

    // HELPER ĐA NĂNG (giống hệt các controller khác)
    private Long getUserId(Object principal) {
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof Jwt) {
            username = ((Jwt) principal).getSubject();
        } else {
            throw new RuntimeException("Không hỗ trợ loại xác thực: " + 
                (principal == null ? "null" : principal.getClass().getName()));
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại"))
                .getId();
    }
}