package com.feastflow.auth.service;

import com.feastflow.auth.entity.RefreshTokenEntity;
import com.feastflow.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;

    private static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

    public record TokenPair(String rawToken, RefreshTokenEntity entity) {}

    @Transactional
    public TokenPair createRefreshToken(UUID userId) {
        String rawToken = tokenService.generateSecureToken();
        String tokenHash = tokenService.hashToken(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        return new TokenPair(rawToken, refreshTokenRepository.save(refreshToken));
    }

    @Transactional
    public Optional<TokenPair> validateAndRotateRefreshToken(String rawToken) {
        String tokenHash = tokenService.hashToken(rawToken);
        Optional<RefreshTokenEntity> optionalToken = refreshTokenRepository.findByTokenHash(tokenHash);

        if (optionalToken.isEmpty()) {
            return Optional.empty();
        }

        RefreshTokenEntity existingToken = optionalToken.get();

        if (!existingToken.isValid()) {
            return Optional.empty();
        }

        existingToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existingToken);

        String newRawToken = tokenService.generateSecureToken();
        String newTokenHash = tokenService.hashToken(newRawToken);
        Instant newExpiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);

        RefreshTokenEntity newToken = RefreshTokenEntity.builder()
                .userId(existingToken.getUserId())
                .tokenHash(newTokenHash)
                .expiresAt(newExpiresAt)
                .build();

        return Optional.of(new TokenPair(newRawToken, refreshTokenRepository.save(newToken)));
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        List<RefreshTokenEntity> activeTokens = refreshTokenRepository.findActiveByUserId(userId);
        Instant now = Instant.now();
        activeTokens.forEach(token -> token.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    @Transactional
    public void revokeToken(UUID tokenId) {
        refreshTokenRepository.findById(tokenId).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public int cleanupExpiredTokens() {
        List<RefreshTokenEntity> expiredTokens = refreshTokenRepository.findExpired(Instant.now());
        if (!expiredTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(expiredTokens);
        }
        return expiredTokens.size();
    }

    public long getRefreshTokenTtlSeconds() {
        return REFRESH_TOKEN_TTL_SECONDS;
    }
}