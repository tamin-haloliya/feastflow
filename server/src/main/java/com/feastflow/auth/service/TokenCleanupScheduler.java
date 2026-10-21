package com.feastflow.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @PostConstruct
    public void init() {
        log.info("TokenCleanupScheduler initialized - scheduled cleanup enabled");
    }

    @Scheduled(fixedRateString = "${token.cleanup.interval-ms:60000}")
    public void cleanupExpiredTokens() {
        log.debug("Running scheduled cleanup of expired refresh tokens");
        int deleted = refreshTokenService.cleanupExpiredTokens();
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
    }
}