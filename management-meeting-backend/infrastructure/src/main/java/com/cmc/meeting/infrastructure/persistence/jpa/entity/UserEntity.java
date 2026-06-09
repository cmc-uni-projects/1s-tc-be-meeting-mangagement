package com.cmc.meeting.infrastructure.persistence.jpa.entity;

import com.cmc.meeting.domain.model.Role; // Bổ sung
import jakarta.persistence.*;
import lombok.Data;
import java.util.Set; // Bổ sung
import java.util.HashSet; // Bổ sung

@Data
@Entity
@Table(name = "users") // Đặt tên bảng là "users"
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // [BỔ SUNG QUAN TRỌNG] Lưu ID của User từ Auth Service (DB 9000)
    // Trường này sẽ giúp Resource Server tìm kiếm User thông qua JWT
    @Column(name = "auth_service_id", unique = true)
    private Long authServiceId;

    @Column(unique = true, nullable = false)
    private String username; // email

    @Column(nullable = false)
    private String fullName;
    
    // Lưu ý: Password chỉ cần thiết cho User local, có thể cho phép NULL nếu dùng SSO hoàn toàn
    @Column(nullable = false)
    private String password;
    
    @ElementCollection(fetch = FetchType.EAGER) // Tải quyền ngay lập tức
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false)
    @Enumerated(EnumType.STRING) // Lưu tên (vd: "ROLE_ADMIN")
    private Set<Role> roles = new HashSet<>();
    
    @Column(nullable = false, columnDefinition = "BIT(1) DEFAULT 1")
    private boolean isActive = true;
    
    @Column(name = "google_refresh_token", columnDefinition = "TEXT")
    private String googleRefreshToken; 

    @Column(name = "is_google_linked")
    private boolean isGoogleLinked = false;

}