package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Transactional
    public User register(String username, String email, String rawPassword) {
        if (isBlank(username) || isBlank(email) || isBlank(rawPassword)) {
            throw new IllegalArgumentException("Username, email, and password are required");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        User user = new User(username, email, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public User requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username));
    }
}
