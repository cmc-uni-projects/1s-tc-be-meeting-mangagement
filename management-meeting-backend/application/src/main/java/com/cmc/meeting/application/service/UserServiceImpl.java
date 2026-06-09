package com.cmc.meeting.application.service;

import com.cmc.meeting.application.dto.request.UserProfileUpdateRequest;
import com.cmc.meeting.application.dto.response.UserDTO;
import com.cmc.meeting.application.port.service.UserService;
import com.cmc.meeting.domain.model.User;
import com.cmc.meeting.domain.port.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    public UserServiceImpl(UserRepository userRepository, ModelMapper modelMapper) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String query) {
        // Gọi hàm tìm kiếm từ Repository
        List<User> users = userRepository.searchByNameOrUsername(query);

        // Ánh xạ sang DTO
        return users.stream()
                .map(this::convertToDTO) // Sử dụng hàm convert chung để đảm bảo map đủ roles
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        
        // Map sang DTO và trả về (bao gồm cả Roles mới nhất từ DB)
        return convertToDTO(user);
    }

    @Override
    @Transactional // Write transaction
    public UserDTO updateUserProfile(String username, UserProfileUpdateRequest request) {
        
        // 1. Tìm người dùng
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng: " + username));

        // 2. Cập nhật thông tin (chỉ cập nhật các trường được phép)
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }

        // 3. Lưu vào DB
        User updatedUser = userRepository.save(user);

        // 4. Trả về dữ liệu mới nhất
        return convertToDTO(updatedUser);
    }

    /**
     * Hàm hỗ trợ chuyển đổi từ Entity sang DTO.
     * Tách ra để tái sử dụng và đảm bảo logic mapping Roles luôn nhất quán.
     */
    private UserDTO convertToDTO(User user) {
        // 1. Dùng ModelMapper để map các trường cơ bản (id, fullName, username)
        UserDTO dto = modelMapper.map(user, UserDTO.class);

        // 2. [QUAN TRỌNG] Map thủ công trường Roles để đảm bảo chính xác tuyệt đối
        // User.getRoles() trả về Set<Role> (Enum), còn UserDTO.getRoles() là Set<String>
        if (user.getRoles() != null) {
            Set<String> roleNames = user.getRoles().stream()
                    .map(Enum::name) // Chuyển Enum thành String (VD: "ROLE_ADMIN")
                    .collect(Collectors.toSet());
            dto.setRoles(roleNames);
        }

        return dto;
    }
}