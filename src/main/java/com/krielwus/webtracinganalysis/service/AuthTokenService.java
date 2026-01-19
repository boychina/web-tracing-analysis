package com.krielwus.webtracinganalysis.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.krielwus.webtracinganalysis.entity.RefreshToken;
import com.krielwus.webtracinganalysis.entity.RefreshTokenAudit;
import com.krielwus.webtracinganalysis.entity.UserAccount;
import com.krielwus.webtracinganalysis.repository.RefreshTokenAuditRepository;
import com.krielwus.webtracinganalysis.repository.RefreshTokenRepository;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import com.krielwus.webtracinganalysis.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthTokenService {
    private final RefreshTokenRepository rtRepo;
    private final RefreshTokenAuditRepository auditRepo;
    private final UserAccountRepository userRepo;
    private final JwtUtil jwtUtil;

    @Value("${REFRESH_TOKEN_TTL_DAYS:30}")
    private long refreshTtlDays;

    public AuthTokenService(RefreshTokenRepository rtRepo,
                            RefreshTokenAuditRepository auditRepo,
                            UserAccountRepository userRepo,
                            JwtUtil jwtUtil) {
        this.rtRepo = rtRepo;
        this.auditRepo = auditRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    public String issueAccessToken(UserAccount user, String deviceId) {
        Map<String, String> extra = new HashMap<>();
        extra.put("deviceId", deviceId);
        return jwtUtil.createAccessToken(user.getId(), user.getUsername(), user.getRole(), extra);
    }

    public static String randomToken() {
        byte[] buf = new byte[48];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public static String hash(String plaintext) {
        return DigestUtil.sha256Hex(plaintext);
    }

    @Transactional
    public com.krielwus.webtracinganalysis.info.Tokens issueTokens(UserAccount user, String deviceId, String ip, String ua) {
        String rtPlain = randomToken();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setDeviceId(deviceId);
        entity.setTokenHash(hash(rtPlain));
        entity.setIpAddress(ip);
        entity.setUserAgent(ua);
        entity.setExpiresAt(Date.from(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)));
        rtRepo.save(entity);
        String at = issueAccessToken(user, deviceId);
        return new com.krielwus.webtracinganalysis.info.Tokens(at, rtPlain, entity.getId());
    }

    public Optional<RefreshToken> findByHash(String plaintextRt) {
        String h = hash(plaintextRt);
        return rtRepo.findByTokenHash(h);
    }

    @Transactional
    public Optional<com.krielwus.webtracinganalysis.info.RefreshRotateResult> rotate(RefreshToken old, String ip, String ua) {
        if (Boolean.TRUE.equals(old.getRevoked())) return Optional.empty();
        if (old.getExpiresAt() != null && old.getExpiresAt().before(new Date())) return Optional.empty();
        Optional<UserAccount> userOpt = userRepo.findById(old.getUserId());
        if (userOpt.isEmpty()) return Optional.empty();
        RefreshToken next = new RefreshToken();
        next.setUserId(old.getUserId());
        next.setDeviceId(old.getDeviceId());
        String rtPlain = randomToken();
        next.setTokenHash(hash(rtPlain));
        next.setIpAddress(ip);
        next.setUserAgent(ua);
        next.setExpiresAt(old.getExpiresAt());
        rtRepo.save(next);
        old.setRevoked(true);
        old.setReplacedByTokenId(next.getId());
        old.setLastRefreshAt(new Date());
        rtRepo.save(old);
        return Optional.of(new com.krielwus.webtracinganalysis.info.RefreshRotateResult(next, rtPlain));
    }

    public void audit(Long tokenId, Long userId, String deviceId, String ip, String ua, boolean success) {
        RefreshTokenAudit a = new RefreshTokenAudit();
        a.setTokenId(tokenId);
        a.setUserId(userId);
        a.setDeviceId(deviceId);
        a.setIpAddress(ip);
        a.setUserAgent(ua);
        a.setSuccess(success);
        auditRepo.save(a);
    }

    public List<Map<String,Object>> listActiveDevices(Long userId) {
        List<RefreshToken> list = rtRepo.findActiveByUserId(userId, new Date());
        List<Map<String,Object>> out = new ArrayList<>();
        for (RefreshToken rt : list) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", rt.getId());
            m.put("deviceId", rt.getDeviceId());
            m.put("ip", rt.getIpAddress());
            m.put("userAgent", rt.getUserAgent());
            m.put("createdAt", rt.getCreatedAt());
            m.put("expiresAt", rt.getExpiresAt());
            m.put("lastRefreshAt", rt.getLastRefreshAt());
            m.put("revoked", rt.getRevoked());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public boolean revokeDevice(Long userId, Long tokenId) {
        RefreshToken rt = rtRepo.findById(tokenId).orElse(null);
        if (rt == null) return false;
        if (!Objects.equals(rt.getUserId(), userId)) return false;
        rt.setRevoked(true);
        rtRepo.save(rt);
        return true;
    }

    @Transactional
    public int forceLogoutUser(Long targetUserId) {
        List<RefreshToken> list = rtRepo.findByUserId(targetUserId);
        for (RefreshToken rt : list) {
            rt.setRevoked(true);
        }
        rtRepo.saveAll(list);
        return list.size();
    }
}
