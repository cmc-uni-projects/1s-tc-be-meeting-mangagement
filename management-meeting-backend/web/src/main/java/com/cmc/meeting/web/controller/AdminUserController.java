package com.cmc.meeting.web.controller;

import com.cmc.meeting.application.dto.admin.AdminUserDTO;
import com.cmc.meeting.application.dto.admin.AdminUserUpdateRequest;
import com.cmc.meeting.application.dto.request.AdminUserCreationRequest;
import com.cmc.meeting.application.port.service.AdminUserService;
import com.cmc.meeting.domain.port.repository.UserRepository;
import com.cmc.meeting.domain.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: User Management API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserRepository userRepository;

    public AdminUserController(AdminUserService adminUserService, UserRepository userRepository) {
        this.adminUserService = adminUserService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserDTO>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Object principal) {
        
        Long currentAdminId = getUserId(principal);
        adminUserService.deleteUser(id, currentAdminId);
        return ResponseEntity.ok("Đã vô hiệu hóa user.");
    }
    
    private Long getUserId(Object principal) {
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof Jwt) {
            username = ((Jwt) principal).getSubject();
        } else {
            throw new RuntimeException("Auth type not supported");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return user.getId();
    }

    @PostMapping
    public ResponseEntity<AdminUserDTO> createUser(
            @Valid @RequestBody AdminUserCreationRequest request) {
        AdminUserDTO newUser = adminUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }
}