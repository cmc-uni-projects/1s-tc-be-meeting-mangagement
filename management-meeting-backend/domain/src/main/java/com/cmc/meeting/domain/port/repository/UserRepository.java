package com.cmc.meeting.domain.port.repository;

import com.cmc.meeting.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {
    Optional<User> findById(Long id);

    Optional<User> findByAuthServiceId(Long authServiceId);

    Optional<User> findByUsername(String username);

    User save(User user);

    List<User> findAll();

    List<User> findAllById(Set<Long> ids);

    void delete(User user);

    List<User> searchByNameOrUsername(String query);

    List<User> findAllAdmins();
}