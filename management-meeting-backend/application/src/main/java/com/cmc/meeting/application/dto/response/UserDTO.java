package com.cmc.meeting.application.dto.response;

import lombok.Data;
import java.util.Set; // Import Set

@Data
public class UserDTO {
    private Long id;
    private String fullName;
    private String username;
    
    // [BỔ SUNG] Thêm trường roles để Frontend điều hướng
    private Set<String> roles; 
}