package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.BaseInfoRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 基线信息仓库。
 * 提供查询最新一次上报的基线记录的方法。
 */
public interface BaseInfoRecordRepository extends JpaRepository<BaseInfoRecord, Long> {
    /** 按创建时间倒序取最新一条记录 */
    BaseInfoRecord findTopByOrderByCreatedAtDesc();
    java.util.List<BaseInfoRecord> findByCreatedAtBetween(java.util.Date start, java.util.Date end);
}
