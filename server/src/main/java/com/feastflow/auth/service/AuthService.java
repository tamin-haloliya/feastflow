package com.feastflow.auth.service;

import com.feastflow.auth.dto.AuthResponse;
import com.feastflow.auth.dto.LoginRequest;
import com.feastflow.auth.dto.RefreshTokenRequest;
import com.feastflow.auth.dto.RegisterRequest;
import com.feastflow.auth.entity.RefreshTokenEntity;
import com.feastflow.auth.entity.UserEntity;
import com.feastflow.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .fullName(request.getName())
                .passwordHash(passwordService.hashPassword(request.getPassword()))
                .build();

        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        var tokenPair = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.of(
                accessToken,
                tokenPair.rawToken(),
                jwtService.getAccessTokenTtlSeconds()
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());
        var tokenPair = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.of(
                accessToken,
                tokenPair.rawToken(),
                jwtService.getAccessTokenTtlSeconds()
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        var optionalTokenPair = refreshTokenService.validateAndRotateRefreshToken(request.getRefreshToken());

        if (optionalTokenPair.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        var tokenPair = optionalTokenPair.get();
        UserEntity user = userRepository.findById(tokenPair.entity().getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account disabled");
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());

        return AuthResponse.of(
                accessToken,
                tokenPair.rawToken(),
                jwtService.getAccessTokenTtlSeconds()
        );
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenService.revokeAllUserTokens(userId);
    }
}