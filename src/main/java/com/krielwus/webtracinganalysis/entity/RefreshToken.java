package com.krielwus.webtracinganalysis.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_id", length = 128, nullable = false)
    private String deviceId;
    @Column(name = "token_hash", length = 256, nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    @Column(name = "expires_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiresAt;
    @Column(name = "revoked")
    private Boolean revoked = false;
    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "last_refresh_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastRefreshAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = new Date();
    }

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public Long getReplacedByTokenId() { return replacedByTokenId; }
    public void setReplacedByTokenId(Long replacedByTokenId) { this.replacedByTokenId = replacedByTokenId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Date getLastRefreshAt() { return lastRefreshAt; }
    public void setLastRefreshAt(Date lastRefreshAt) { this.lastRefreshAt = lastRefreshAt; }
}
