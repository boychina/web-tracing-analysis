package com.krielwus.webtracinganalysis.entity;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "application_info")
public class ApplicationInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_name", length = 64, nullable = false)
    private String appName;

    @Column(name = "app_code", length = 32, nullable = false, unique = true)
    private String appCode;

    @Column(name = "app_code_prefix", length = 16, nullable = false)
    private String appCodePrefix;

    @Column(name = "app_desc", length = 255)
    private String appDesc;

    @Lob
    @Column(name = "app_managers", columnDefinition = "LONGTEXT")
    private String appManagers;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @PrePersist
    public void prePersist() {
        Date now = new Date();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = new Date(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getAppCodePrefix() { return appCodePrefix; }
    public void setAppCodePrefix(String appCodePrefix) { this.appCodePrefix = appCodePrefix; }
    public String getAppDesc() { return appDesc; }
    public void setAppDesc(String appDesc) { this.appDesc = appDesc; }
    public String getAppManagers() { return appManagers; }
    public void setAppManagers(String appManagers) { this.appManagers = appManagers; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
