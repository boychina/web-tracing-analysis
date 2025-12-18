package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.TracingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 过滤事件类型与应用，并按时间范围查询 */
    List<TracingEvent> findByEventTypeAndAppCodeAndCreatedAtBetween(String eventType, String appCode, Date start, Date end);

    /** 统计事件类型与应用在时间范围内的数量 */
    long countByEventTypeAndAppCodeAndCreatedAtBetween(String eventType, String appCode, Date start, Date end);

    /** 统计事件类型与应用的总数量 */
    long countByEventTypeAndAppCode(String eventType, String appCode);

    /** 按事件类型统计总数量 */
    long countByEventType(String eventType);

    /** 按事件类型统计在时间范围内的数量（全应用） */
    long countByEventTypeAndCreatedAtBetween(String eventType, Date start, Date end);

    /** 统计时间范围内的应用数量（按事件中的 app_code 去重） */
    @Query(value = "SELECT COUNT(DISTINCT app_code) FROM trace_event WHERE created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctAppCodeBetween(@Param("start") Date start, @Param("end") Date end);

    /** 统计时间范围内的会话数量（按事件中的 session_id 去重） */
    @Query(value = "SELECT COUNT(DISTINCT session_id) FROM trace_event WHERE created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctSessionIdBetween(@Param("start") Date start, @Param("end") Date end);

    /** 统计时间范围内的设备数量（从 payload 提取 deviceId 去重） */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.deviceId'))) FROM trace_event WHERE created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctDeviceIdBetween(@Param("start") Date start, @Param("end") Date end);

    /** 统计时间范围内的用户数量（从 payload 提取 sdkUserUuid 去重） */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sdkUserUuid'))) FROM trace_event WHERE created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctSdkUserUuidBetween(@Param("start") Date start, @Param("end") Date end);

    /** 统计应用在时间范围内的会话数量（按事件中的 session_id 去重） */
    @Query(value = "SELECT COUNT(DISTINCT session_id) FROM trace_event WHERE app_code = :appCode AND created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctSessionIdForAppBetween(@Param("appCode") String appCode, @Param("start") Date start, @Param("end") Date end);

    /** 统计应用在时间范围内的设备数量（从 payload 提取 deviceId 去重） */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.deviceId'))) FROM trace_event WHERE app_code = :appCode AND created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctDeviceIdForAppBetween(@Param("appCode") String appCode, @Param("start") Date start, @Param("end") Date end);

    /** 统计应用在时间范围内的用户数量（从 payload 提取 sdkUserUuid 去重） */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sdkUserUuid'))) FROM trace_event WHERE app_code = :appCode AND created_at BETWEEN :start AND :end", nativeQuery = true)
    long countDistinctSdkUserUuidForAppBetween(@Param("appCode") String appCode, @Param("start") Date start, @Param("end") Date end);
    /**
     * 统计日期范围内每日按应用的 PV 数。
     * 返回 [day(yyyy-MM-dd), app_code, pv_count]
     */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, app_code AS code, COUNT(*) AS pv\n"
            + "FROM trace_event\n"
            + "WHERE event_type = 'PV' AND created_at BETWEEN :start AND :end\n"
            + "GROUP BY day, code\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyPvByApp(@Param("start") Date start, @Param("end") Date end);
}
