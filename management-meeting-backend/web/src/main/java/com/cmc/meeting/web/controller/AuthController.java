package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.auth.*;
import com.cmc.meeting.application.port.service.AuthService;
import com.cmc.meeting.domain.port.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;           // THÊM DÒNG NÀY
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication API", description = "API Đăng ký và Đăng nhập")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    // ====================== CÁC API KHÔNG CẦN ĐĂNG NHẬP → GIỮ NGUYÊN ======================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.login(loginRequest);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            String message = authService.register(registerRequest);
            return ResponseEntity.ok(message);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Yêu cầu link đặt lại mật khẩu qua email")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Hoàn tất đặt lại mật khẩu (dùng token từ email)")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            String message = authService.resetPassword(request);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ====================== API CẦN ĐĂNG NHẬP → ĐÃ CHUYỂN SANG HYBRID ======================
    @PostMapping("/change-password")
    @Operation(summary = "Tự đổi mật khẩu (khi đã đăng nhập)")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal Object principal,                 // ĐỔI THÀNH Object
            @Valid @RequestBody ChangePasswordRequest request) {

        Long currentUserId = getUserId(principal);

        authService.changePassword(currentUserId, request);
        return ResponseEntity.ok("Đổi mật khẩu thành công.");
    }

    // HELPER ĐA NĂNG (dùng chung cho toàn controller và các controller khác)
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
                .orElseThrow(() -> new EntityNotFoundException("User không tồn tại từ token"))
                .getId();
    }
}