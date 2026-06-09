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
        
        log.error("🔥 Unauthorized Error: {}", authException.getMessage());

        // Kiểm tra cờ từ Converter (Đây là chốt chặn cuối cùng tin cậy nhất)
        Object disabledFlag = request.getAttribute("ACCOUNT_DISABLED_FLAG");
        boolean isUserDisabled = (disabledFlag != null && (Boolean) disabledFlag);

        // Logic check Exception cũ (giữ lại để phòng hờ)
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
        body.put("error", "Unauthorized");
        body.put("path", request.getServletPath());

        if (isUserDisabled) {
            log.warn("⚠️ [EntryPoint] Phát hiện user bị khóa (qua Flag hoặc Exception). Trả về JSON đặc biệt.");
            // Message chứa từ khóa "disabled" để Frontend bắt
            body.put("message", "Tài khoản của bạn đã bị vô hiệu hóa (Account disabled). Vui lòng liên hệ Admin.");
        } else {
            body.put("message", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}