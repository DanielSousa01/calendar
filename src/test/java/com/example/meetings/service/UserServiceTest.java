package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(userRepository, passwordEncoder);

    @Test
    void registerRejectsBlankRequiredFieldsServerSide() {
        when(userRepository.existsByUsername("")).thenReturn(false);
        when(passwordEncoder.encode("")).thenReturn("hash");

        assertThatThrownBy(() -> service.register("", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username, email, and password are required");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice", "alice@example.com", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already taken");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerEncodesPasswordAndSavesUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.register("alice", "alice@example.com", "secret");

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }
}
