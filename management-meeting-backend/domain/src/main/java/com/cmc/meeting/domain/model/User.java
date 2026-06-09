package com.cmc.meeting.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String username; // Thường là email
    private String fullName;
    private String password;
    private Set<Role> roles = new HashSet<>();
    private boolean isActive = true; // us-18
    private String googleRefreshToken;
    private boolean isGoogleLinked = false;
    private Long authServiceId;
}