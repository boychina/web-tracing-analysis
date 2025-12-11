package com.krielwus.webtracinganalysis.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * 埋点事件表。
 * 持久化保存前端上报的每条事件，包括事件类型与原始 JSON 载荷，
 * 便于后续检索、分析与聚合统计。
 */
@Entity
@Table(name = "trace_event", indexes = {
        @Index(name = "idx_trace_event_type", columnList = "event_type"),
        @Index(name = "idx_trace_app_code", columnList = "app_code"),
        @Index(name = "idx_trace_created_at", columnList = "created_at")
})
public class TracingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件类型（如 PV、CLICK、ERROR 等） */
    @Column(name = "event_type", length = 64)
    private String eventType;

    /** 应用标识 appCode（冗余存储，便于聚合） */
    @Column(name = "app_code", length = 128)
    private String appCode;

    /** 应用名称 appName（冗余存储，便于展示） */
    @Column(name = "app_name", length = 256)
    private String appName;

    /** 会话ID（冗余存储，便于关联） */
    @Column(name = "session_id", length = 128)
    private String sessionId;

    /** 事件原始 JSON 载荷，保留完整结构用于分析 */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    /** 事件入库时间（服务端接收时间） */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    /** 在持久化前自动记录入库时间 */
    @PrePersist
    public void prePersist() {
        this.createdAt = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
