package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByIcalToken(String icalToken);

    boolean existsByUsername(String username);
}
