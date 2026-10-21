package com.feastflow.auth.repository;

import com.feastflow.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.userId = :userId AND r.revokedAt IS NULL")
    List<RefreshTokenEntity> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT r FROM RefreshTokenEntity r WHERE r.expiresAt < :now")
    List<RefreshTokenEntity> findExpired(@Param("now") Instant now);

    void deleteByUserId(UUID userId);
}