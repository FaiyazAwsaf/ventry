package com.ventry.auth.service;

import com.ventry.auth.dto.AuthResponse;
import com.ventry.auth.dto.LoginRequest;
import com.ventry.auth.dto.RegisterRequest;
import com.ventry.auth.entity.User;
import com.ventry.auth.exception.DuplicateEmailException;
import com.ventry.auth.exception.InvalidCredentialsException;
import com.ventry.auth.repository.UserRepository;
import com.ventry.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                User.Role.CUSTOMER
        );
        user = userRepository.save(user);

        String token = jwtService.issueToken(user.getId().toString(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issueToken(user.getId().toString(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }
}
