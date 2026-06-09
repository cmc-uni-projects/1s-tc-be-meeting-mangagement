package com.cmc.meeting.application.port.service;

import com.cmc.meeting.application.dto.request.UserProfileUpdateRequest;
import com.cmc.meeting.application.dto.response.UserDTO;
import java.util.List;

public interface UserService {
    List<UserDTO> searchUsers(String query);
    UserDTO updateUserProfile(String username, UserProfileUpdateRequest request);
    UserDTO getUserProfile(String username);
}