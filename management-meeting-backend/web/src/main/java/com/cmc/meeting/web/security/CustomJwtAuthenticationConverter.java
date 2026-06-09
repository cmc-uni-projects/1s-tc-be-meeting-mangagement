package com.cmc.meeting.web.security;

import com.cmc.meeting.domain.model.User;
import com.cmc.meeting.domain.port.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public CustomJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long determinedId; // Biến tạm để tính toán

        // 1. Logic xác định ID (SSO hoặc Local)
        if (jwt.hasClaim("auth_id")) {
            Object authIdObj = jwt.getClaim("auth_id");
            if (authIdObj instanceof Number) {
                determinedId = ((Number) authIdObj).longValue();
            } else {
                throw new EntityNotFoundException("Token SSO thiếu claim 'auth_id' hợp lệ.");
            }
        } else if (jwt.hasClaim("userId")) {
            Object userIdObj = jwt.getClaim("userId");
            if (userIdObj instanceof Number) {
                determinedId = ((Number) userIdObj).longValue();
            } else {
                throw new EntityNotFoundException("Token Local thiếu claim 'userId' hợp lệ.");
            }
        } else {
            throw new EntityNotFoundException("Token không hợp lệ: Thiếu claim định danh (auth_id hoặc userId)");
        }

        // [FIX LỖI FINAL]: Gán giá trị vào một biến final để dùng trong Lambda bên dưới
        final Long userId = determinedId;

        // 2. Tìm User trong DB
        User user;
        // Nếu có auth_id -> tìm theo authServiceId
        if (jwt.hasClaim("auth_id")) {
             user = userRepository.findByAuthServiceId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy User SSO trong DB với Auth ID: " + userId));
        } else {
            // Nếu là Local -> tìm theo ID
            user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy User Local trong DB với ID: " + userId));
        }

        // 3. Kiểm tra trạng thái Active
        if (!user.isActive()) {
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    attrs.getRequest().setAttribute("ACCOUNT_DISABLED_FLAG", true);
                }
            } catch (Exception e) {
                // Ignore
            }
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa.");
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }
}