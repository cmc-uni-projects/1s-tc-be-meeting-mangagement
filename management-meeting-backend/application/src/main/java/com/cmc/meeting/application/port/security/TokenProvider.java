package com.cmc.meeting.application.port.security;

import java.util.Set;

public interface TokenProvider {

    // Hàm tạo token (Đã có)
    String generateToken(Long userId, String username, Set<String> roles);

    // [BỔ SUNG 1] Hàm lấy username từ token
    String getUsernameFromToken(String token);

    // [BỔ SUNG 2] Hàm kiểm tra token hợp lệ
    boolean validateToken(String authToken);
}