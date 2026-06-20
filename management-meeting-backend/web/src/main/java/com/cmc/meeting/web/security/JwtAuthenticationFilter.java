package com.cmc.meeting.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper; // <-- Cần import này
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
            UserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {


        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {

                // --- THỬ XÁC THỰC NHƯ LÀ TOKEN LOCAL ---
                boolean isValidLocalToken = false;
                try {
                    if (tokenProvider.validateToken(jwt)) {
                        isValidLocalToken = true;
                        String username = tokenProvider.getUsernameFromToken(jwt);
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("✅ [Legacy Filter] Token Local hợp lệ cho user: {}", username);
                    }
                } catch (Exception e) {
                    // Nếu lỗi (do là token SSO), chỉ log trace và bỏ qua
                    log.trace("Token không phải Local Token, bỏ qua.");
                }

                // --- [QUAN TRỌNG] NẾU LÀ TOKEN LOCAL THÀNH CÔNG ---
                // Ta phải "giấu" header Authorization đi để Resource Server phía sau không check lại nữa
                if (isValidLocalToken) {
                    HttpServletRequestWrapper hiddenHeaderRequest = new HttpServletRequestWrapper(request) {
                        @Override
                        public String getHeader(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return null; // Trả về null để Resource Server tưởng không có token
                            }
                            return super.getHeader(name);
                        }

                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            if ("Authorization".equalsIgnoreCase(name)) {
                                return Collections.enumeration(List.of());
                            }
                            return super.getHeaders(name);
                        }
                    };

                    // Chuyển tiếp request đã bị giấu header
                    filterChain.doFilter(hiddenHeaderRequest, response);
                    return; // Dừng hàm tại đây, không chạy dòng filterChain ở cuối
                }
            }
        } catch (Exception ex) {
            log.error("Lỗi không xác định trong JwtAuthenticationFilter", ex);
        }

        // Nếu không phải token local (hoặc không có token), chuyển tiếp request nguyên bản
        // để Resource Server (SSO) xử lý tiếp.
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}