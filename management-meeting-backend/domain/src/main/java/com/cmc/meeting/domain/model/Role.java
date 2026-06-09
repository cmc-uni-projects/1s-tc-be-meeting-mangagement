package com.cmc.meeting.domain.model;

import org.springframework.security.core.GrantedAuthority; // <-- THÊM IMPORT NÀY

// [SỬA LỖI] Role phải implement GrantedAuthority để được sử dụng làm quyền trong Spring Security
public enum Role implements GrantedAuthority { 
    ROLE_USER,
    ROLE_ADMIN;
    
    // [BỔ SUNG] Implement phương thức getAuthority()
    @Override
    public String getAuthority() {
        return name(); // Trả về tên enum (ví dụ: "ROLE_ADMIN")
    }
}