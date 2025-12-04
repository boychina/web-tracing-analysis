package com.krielwus.webtracinganalysis.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * 基线信息表。
 * 保存每次上报的基础环境与上下文信息的原始 JSON，
 * 供查询最新基线或比对不同批次的环境差异。
 */
@Entity
@Table(name = "base_info_record")
public class BaseInfoRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 基线信息原始 JSON 载荷 */
    @Lob
    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    /** 上报入库时间 */
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
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
