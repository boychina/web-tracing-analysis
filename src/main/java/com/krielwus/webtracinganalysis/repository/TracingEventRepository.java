package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.TracingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Date;

/**
 * 事件数据仓库。
 * 提供按时间倒序与事件类型过滤的查询方法。
 */
public interface TracingEventRepository extends JpaRepository<TracingEvent, Long> {
    /** 按创建时间倒序查询全部事件 */
    List<TracingEvent> findAllByOrderByCreatedAtDesc();
    /** 按事件类型过滤并倒序查询 */
    List<TracingEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
    /** 查询时间范围内的事件 */
    List<TracingEvent> findByCreatedAtBetween(Date start, Date end);
}
