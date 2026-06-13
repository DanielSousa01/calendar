package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(userRepository, passwordEncoder);

    @Test
    @Tag("bug")
    void registerRejectsBlankRequiredFieldsServerSide() {
        when(userRepository.existsByUsername("")).thenReturn(false);
        when(passwordEncoder.encode("")).thenReturn("hash");

        assertThatThrownBy(() -> service.register("", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username, email, and password are required");
        verify(userRepository, never()).save(any(User.class));
    }
}
