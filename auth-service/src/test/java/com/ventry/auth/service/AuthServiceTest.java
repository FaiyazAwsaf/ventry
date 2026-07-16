package com.ventry.auth.service;

import com.ventry.auth.dto.AuthResponse;
import com.ventry.auth.dto.LoginRequest;
import com.ventry.auth.dto.RegisterRequest;
import com.ventry.auth.entity.User;
import com.ventry.auth.exception.DuplicateEmailException;
import com.ventry.auth.exception.InvalidCredentialsException;
import com.ventry.auth.repository.UserRepository;
import com.ventry.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_savesCustomerAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("new@ventry.com", "password123");
        when(userRepository.existsByEmail("new@ventry.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.issueToken("new@ventry.com", "CUSTOMER")).thenReturn("signed-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.email()).isEqualTo("new@ventry.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("new@ventry.com")
                        && u.getPassword().equals("hashed")
                        && u.getRole() == User.Role.CUSTOMER
        ));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("dup@ventry.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@ventry.com", "password123")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsTokenForValidCredentials() {
        User user = new User("existing@ventry.com", "hashed", User.Role.CUSTOMER);
        when(userRepository.findByEmail("existing@ventry.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.issueToken("existing@ventry.com", "CUSTOMER")).thenReturn("signed-token");

        AuthResponse response = authService.login(new LoginRequest("existing@ventry.com", "password123"));

        assertThat(response.token()).isEqualTo("signed-token");
    }

    @Test
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@ventry.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_rejectsWrongPassword() {
        User user = new User("existing@ventry.com", "hashed", User.Role.CUSTOMER);
        when(userRepository.findByEmail("existing@ventry.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("existing@ventry.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
