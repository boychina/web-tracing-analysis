package com.krielwus.webtracinganalysis.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "page_view_route", indexes = {
        @Index(name = "idx_pvr_appcode_created_at", columnList = "app_code, created_at"),
        @Index(name = "idx_pvr_appcode_route", columnList = "app_code, route_path"),
        @Index(name = "idx_pvr_session_created_at", columnList = "session_id, created_at"),
        @Index(name = "idx_pvr_appcode_user", columnList = "app_code, sdk_user_uuid")
})
public class PageViewRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_code", length = 128)
    private String appCode;

    @Column(name = "app_name", length = 256)
    private String appName;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "sdk_user_uuid", length = 128)
    private String sdkUserUuid;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "route_type", length = 16)
    private String routeType;

    @Column(name = "route_path", length = 512)
    private String routePath;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "route_params", columnDefinition = "LONGTEXT")
    private String routeParams;

    @Column(name = "full_url", columnDefinition = "TEXT")
    private String fullUrl;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSdkUserUuid() { return sdkUserUuid; }
    public void setSdkUserUuid(String sdkUserUuid) { this.sdkUserUuid = sdkUserUuid; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }
    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    public String getRouteParams() { return routeParams; }
    public void setRouteParams(String routeParams) { this.routeParams = routeParams; }
    public String getFullUrl() { return fullUrl; }
    public void setFullUrl(String fullUrl) { this.fullUrl = fullUrl; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
