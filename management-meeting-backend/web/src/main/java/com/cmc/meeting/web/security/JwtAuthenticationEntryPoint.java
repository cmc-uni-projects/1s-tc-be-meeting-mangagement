package com.cmc.meeting.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        
        // 1. Kiểm tra cờ từ Converter (Logic này rất tốt, giữ nguyên)
        Object disabledFlag = request.getAttribute("ACCOUNT_DISABLED_FLAG");
        boolean isUserDisabled = (disabledFlag != null && (Boolean) disabledFlag);

        // 2. Kiểm tra Exception gốc (Nếu cờ chưa bắt được)
        if (!isUserDisabled) {
            Throwable cause = authException;
            while (cause != null) {
                if (cause instanceof DisabledException || 
                   (cause.getMessage() != null && cause.getMessage().contains("disabled"))) {
                    isUserDisabled = true;
                    break;
                }
                cause = cause.getCause();
            }
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("path", request.getServletPath());

        // [SỬA ĐỔI QUAN TRỌNG TẠI ĐÂY]
        if (isUserDisabled) {
            log.warn("⚠️ [EntryPoint] User bị khóa. Trả về mã lỗi USER_DISABLED cho Frontend.");
            
            // Thêm mã lỗi đặc biệt này để Frontend bắt được và redirect sang SSO Logout
            body.put("error", "USER_DISABLED"); 
            body.put("message", "Tài khoản của bạn đã bị vô hiệu hóa. Hệ thống sẽ đăng xuất.");
        } else {
            // Lỗi 401 thông thường (Token hết hạn, sai chữ ký...)
            log.error("🔥 Unauthorized Error: {}", authException.getMessage());
            body.put("error", "UNAUTHORIZED");
            body.put("message", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}