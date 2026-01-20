package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserId(Long userId);
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
    List<RefreshToken> findByUserIdAndDeviceIdAndRevokedFalse(Long userId, String deviceId);
    @Query("select rt from RefreshToken rt where rt.userId = ?1 and rt.revoked = false and rt.expiresAt > ?2")
    List<RefreshToken> findActiveByUserId(Long userId, Date now);
    @Query("select rt from RefreshToken rt where rt.revoked = false and rt.expiresAt > ?1")
    List<RefreshToken> findActive(Date now);
    void deleteByUserId(Long userId);
}
