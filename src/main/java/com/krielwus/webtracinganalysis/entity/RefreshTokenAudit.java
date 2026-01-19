package com.krielwus.webtracinganalysis.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "refresh_token_audit")
public class RefreshTokenAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "token_id")
    private Long tokenId;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "device_id", length = 128)
    private String deviceId;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "success")
    private Boolean success;
    @Column(name = "created_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
