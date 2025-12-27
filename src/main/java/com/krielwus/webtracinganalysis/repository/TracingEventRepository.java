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

    /** 按天统计事件类型（全量） */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt\n"
            + "FROM trace_event\n"
            + "WHERE event_type = :eventType AND created_at BETWEEN :start AND :end\n"
            + "GROUP BY day\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyByEventType(@Param("eventType") String eventType, @Param("start") Date start, @Param("end") Date end);

    /** 按天按应用统计事件类型（全量） */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, app_code AS code, COUNT(*) AS cnt\n"
            + "FROM trace_event\n"
            + "WHERE event_type = :eventType AND created_at BETWEEN :start AND :end\n"
            + "GROUP BY day, code\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyByEventTypeByApp(@Param("eventType") String eventType, @Param("start") Date start, @Param("end") Date end);

    /** 按天统计事件类型（限定 appCodes） */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS cnt\n"
            + "FROM trace_event\n"
            + "WHERE event_type = :eventType AND created_at BETWEEN :start AND :end AND app_code IN (:appCodes)\n"
            + "GROUP BY day\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyByEventTypeAndAppCodes(@Param("eventType") String eventType, @Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 按天按应用统计事件类型（限定 appCodes） */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, app_code AS code, COUNT(*) AS cnt\n"
            + "FROM trace_event\n"
            + "WHERE event_type = :eventType AND created_at BETWEEN :start AND :end AND app_code IN (:appCodes)\n"
            + "GROUP BY day, code\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyByEventTypeByAppAndAppCodes(@Param("eventType") String eventType, @Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合的PV数量 */
    @Query(value = "SELECT COUNT(*) FROM trace_event WHERE event_type = :eventType AND app_code IN (:appCodes)", nativeQuery = true)
    long countByEventTypeAndAppCodes(@Param("eventType") String eventType, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合在时间范围内的事件数量 */
    @Query(value = "SELECT COUNT(*) FROM trace_event WHERE event_type = :eventType AND created_at BETWEEN :start AND :end AND app_code IN (:appCodes)", nativeQuery = true)
    long countByEventTypeAndCreatedAtBetweenAndAppCodes(@Param("eventType") String eventType, @Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合在时间范围内的去重应用数 */
    @Query(value = "SELECT COUNT(DISTINCT app_code) FROM trace_event WHERE created_at BETWEEN :start AND :end AND app_code IN (:appCodes)", nativeQuery = true)
    long countDistinctAppCodeBetweenAndAppCodes(@Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合在时间范围内的去重用户数 */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.sdkUserUuid'))) FROM trace_event WHERE created_at BETWEEN :start AND :end AND app_code IN (:appCodes)", nativeQuery = true)
    long countDistinctSdkUserUuidBetweenAndAppCodes(@Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合在时间范围内的去重设备数 */
    @Query(value = "SELECT COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.deviceId'))) FROM trace_event WHERE created_at BETWEEN :start AND :end AND app_code IN (:appCodes)", nativeQuery = true)
    long countDistinctDeviceIdBetweenAndAppCodes(@Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合在时间范围内的去重会话数 */
    @Query(value = "SELECT COUNT(DISTINCT session_id) FROM trace_event WHERE created_at BETWEEN :start AND :end AND app_code IN (:appCodes)", nativeQuery = true)
    long countDistinctSessionIdBetweenAndAppCodes(@Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 统计指定应用代码集合的每日PV数 */
    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, app_code AS code, COUNT(*) AS pv\n"
            + "FROM trace_event\n"
            + "WHERE event_type = 'PV' AND created_at BETWEEN :start AND :end AND app_code IN (:appCodes)\n"
            + "GROUP BY day, code\n"
            + "ORDER BY day ASC", nativeQuery = true)
    java.util.List<Object[]> countDailyPvByAppAndAppCodes(@Param("start") Date start, @Param("end") Date end, @Param("appCodes") java.util.Set<String> appCodes);

    /** 最近事件（全量） */
    @Query(value = "SELECT id, event_type, app_code, session_id, created_at FROM trace_event ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecent(@Param("limit") int limit);

    /** 最近事件（限定 appCodes） */
    @Query(value = "SELECT id, event_type, app_code, session_id, created_at FROM trace_event WHERE app_code IN :appCodes ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentByAppCodes(@Param("appCodes") java.util.Set<String> appCodes, @Param("limit") int limit);

    @Query(value = "SELECT id, event_type, app_code, session_id, created_at, payload FROM trace_event WHERE app_code = :appCode ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentByAppCodeWithPayload(@Param("appCode") String appCode,
                    @Param("limit") int limit);

    /** 最近 ERROR 事件（全量，带 payload/app_name） */
    @Query(value = "SELECT id, event_type, app_code, app_name, session_id, payload, created_at FROM trace_event WHERE event_type = 'ERROR' ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrors(@Param("limit") int limit);

    /** 最近 ERROR 事件（限定 appCodes，带 payload/app_name） */
    @Query(value = "SELECT id, event_type, app_code, app_name, session_id, payload, created_at FROM trace_event WHERE event_type = 'ERROR' AND app_code IN (:appCodes) ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrorsByAppCodes(@Param("appCodes") java.util.Set<String> appCodes, @Param("limit") int limit);

    /** 最近 ERROR 事件（指定单个 appCode，带 payload/app_name） */
    @Query(value = "SELECT id, event_type, app_code, app_name, session_id, payload, created_at FROM trace_event WHERE event_type = 'ERROR' AND app_code = :appCode ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrorsByAppCode(@Param("appCode") String appCode, @Param("limit") int limit);

    @Query(value = "SELECT id, app_code, app_name, session_id, created_at,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) AS event_id,\n"
                    + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')) AS err_message,\n"
                    + "CASE\n"
                    + "  WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "    CASE\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "      ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "    END\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "    THEN 'FATAL'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "    THEN 'ERROR'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "    THEN 'WARN'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "    THEN 'WARN'\n"
                    + "  ELSE 'ERROR'\n"
                    + "END AS severity,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) AS request_uri\n"
                    + "FROM trace_event\n"
                    + "WHERE event_type = 'ERROR'\n"
                    + "ORDER BY created_at DESC\n"
                    + "LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrorsLite(@Param("limit") int limit);

    @Query(value = "SELECT id, app_code, app_name, session_id, created_at,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) AS event_id,\n"
                    + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')) AS err_message,\n"
                    + "CASE\n"
                    + "  WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "    CASE\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "      ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "    END\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "    THEN 'FATAL'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "    THEN 'ERROR'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "    THEN 'WARN'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "    THEN 'WARN'\n"
                    + "  ELSE 'ERROR'\n"
                    + "END AS severity,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) AS request_uri\n"
                    + "FROM trace_event\n"
                    + "WHERE event_type = 'ERROR' AND app_code IN (:appCodes)\n"
                    + "ORDER BY created_at DESC\n"
                    + "LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrorsLiteByAppCodes(@Param("appCodes") java.util.Set<String> appCodes,
                    @Param("limit") int limit);

    @Query(value = "SELECT id, app_code, app_name, session_id, created_at,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) AS event_id,\n"
                    + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')) AS err_message,\n"
                    + "CASE\n"
                    + "  WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "    CASE\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "      ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "    END\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "    THEN 'FATAL'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "    THEN 'ERROR'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "    THEN 'WARN'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "    THEN 'WARN'\n"
                    + "  ELSE 'ERROR'\n"
                    + "END AS severity,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) AS request_uri\n"
                    + "FROM trace_event FORCE INDEX (idx_appcode_created_at)\n"
                    + "WHERE app_code = :appCode AND event_type = 'ERROR'\n"
                    + "ORDER BY created_at DESC\n"
                    + "LIMIT :limit", nativeQuery = true)
    java.util.List<Object[]> findRecentErrorsLiteByAppCode(@Param("appCode") String appCode, @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM trace_event FORCE INDEX (idx_appcode_created_at) WHERE app_code = :appCode AND event_type = 'ERROR'", nativeQuery = true)
    long countErrorsByAppCode(@Param("appCode") String appCode);

    @Query(value = "SELECT COUNT(*)\n"
                    + "FROM trace_event FORCE INDEX (idx_appcode_created_at)\n"
                    + "WHERE app_code = :appCode AND event_type = 'ERROR'\n"
                    + "AND (:errorCode IS NULL OR :errorCode = '' OR JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) = :errorCode)\n"
                                    + "AND (:severity IS NULL OR :severity = '' OR (\n"
                    + "  CASE\n"
                    + "    WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "      CASE\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "        ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "      END\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "      THEN 'CRITICAL'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "      THEN 'CRITICAL'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "      THEN 'FATAL'\n"
                    + "    WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "      THEN 'ERROR'\n"
                    + "    WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "      THEN 'WARN'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "      THEN 'WARN'\n"
                    + "    ELSE 'ERROR'\n"
                    + "  END\n"
                    + ") = :severity)\n"
                    + "AND (:requestUri IS NULL OR :requestUri = '' OR JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) LIKE CONCAT('%', :requestUri, '%'))",
            nativeQuery = true)
    long countErrorsByAppCodeWithFilters(@Param("appCode") String appCode,
                    @Param("errorCode") String errorCode,
                    @Param("severity") String severity,
                    @Param("requestUri") String requestUri);

    @Query(value = "SELECT id, event_type, app_code, app_name, session_id, payload, created_at FROM trace_event WHERE event_type = 'ERROR' AND app_code = :appCode ORDER BY created_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    java.util.List<Object[]> findErrorPageByAppCode(@Param("appCode") String appCode, @Param("limit") int limit,
                    @Param("offset") int offset);

    @Query(value = "SELECT id, app_code, app_name, session_id, created_at,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) AS event_id,\n"
                    + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')) AS err_message,\n"
                    + "CASE\n"
                    + "  WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "    CASE\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "      ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "    END\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "    THEN 'FATAL'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "    THEN 'ERROR'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "    THEN 'WARN'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "    THEN 'WARN'\n"
                    + "  ELSE 'ERROR'\n"
                    + "END AS severity,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) AS request_uri\n"
                    + "FROM trace_event FORCE INDEX (idx_appcode_created_at)\n"
                    + "WHERE app_code = :appCode AND event_type = 'ERROR'\n"
                    + "ORDER BY created_at DESC\n"
                    + "LIMIT :limit OFFSET :offset", nativeQuery = true)
    java.util.List<Object[]> findErrorPageLiteByAppCode(@Param("appCode") String appCode, @Param("limit") int limit,
                    @Param("offset") int offset);

    @Query(value = "SELECT id, app_code, app_name, session_id, created_at,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) AS event_id,\n"
                    + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')) AS err_message,\n"
                    + "CASE\n"
                    + "  WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "    CASE\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "      WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "      ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "    END\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "    THEN 'CRITICAL'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "    THEN 'FATAL'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "    THEN 'ERROR'\n"
                    + "  WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "    THEN 'WARN'\n"
                    + "  WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "    OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "    THEN 'WARN'\n"
                    + "  ELSE 'ERROR'\n"
                    + "END AS severity,\n"
                    + "JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) AS request_uri\n"
                    + "FROM trace_event FORCE INDEX (idx_appcode_created_at)\n"
                    + "WHERE app_code = :appCode AND event_type = 'ERROR'\n"
                    + "AND (:errorCode IS NULL OR :errorCode = '' OR JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) = :errorCode)\n"
                    + "AND (:severity IS NULL OR :severity = '' OR (\n"
                    + "  CASE\n"
                    + "    WHEN COALESCE(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))), '') <> '' THEN\n"
                    + "      CASE\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('CRIT', 'CRITICAL') THEN 'CRITICAL'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('FATAL') THEN 'FATAL'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('ERROR', 'ERR') THEN 'ERROR'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('WARN', 'WARNING') THEN 'WARN'\n"
                    + "        WHEN UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel')))) IN ('INFO') THEN 'INFO'\n"
                    + "        ELSE UPPER(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.severity'), JSON_EXTRACT(payload, '$.level'), JSON_EXTRACT(payload, '$.errLevel'))))\n"
                    + "      END\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%out of memory%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%heap%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%stack overflow%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%maximum call stack%'\n"
                    + "      THEN 'CRITICAL'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%chunkloaderror%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%loading chunk%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch dynamically imported module%'\n"
                    + "      THEN 'CRITICAL'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%fatal%'\n"
                    + "      THEN 'FATAL'\n"
                    + "    WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(5[0-9]{2})'\n"
                    + "      THEN 'ERROR'\n"
                    + "    WHEN JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.eventId'), JSON_EXTRACT(payload, '$.errorCode'), JSON_EXTRACT(payload, '$.code'))) REGEXP '^(4[0-9]{2})'\n"
                    + "      THEN 'WARN'\n"
                    + "    WHEN LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timeout%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%timed out%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%network error%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%networkerror%'\n"
                    + "      OR LOWER(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.errMessage')), '')) LIKE '%failed to fetch%'\n"
                    + "      THEN 'WARN'\n"
                    + "    ELSE 'ERROR'\n"
                    + "  END\n"
                    + ") = :severity)\n"
                    + "AND (:requestUri IS NULL OR :requestUri = '' OR JSON_UNQUOTE(COALESCE(JSON_EXTRACT(payload, '$.triggerPageUrl'), JSON_EXTRACT(payload, '$.requestUri'), JSON_EXTRACT(payload, '$.pageUrl'), JSON_EXTRACT(payload, '$.url'))) LIKE CONCAT('%', :requestUri, '%'))\n"
                    + "ORDER BY created_at DESC\n"
                    + "LIMIT :limit OFFSET :offset", nativeQuery = true)
    java.util.List<Object[]> findErrorPageLiteByAppCodeWithFilters(@Param("appCode") String appCode,
                    @Param("errorCode") String errorCode,
                    @Param("severity") String severity,
                    @Param("requestUri") String requestUri,
                    @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT payload FROM trace_event WHERE id = :id AND event_type = 'ERROR'", nativeQuery = true)
    String findErrorPayloadById(@Param("id") long id);

    @Query(value = "SELECT payload FROM trace_event WHERE id = :id AND app_code = :appCode AND event_type = 'ERROR'", nativeQuery = true)
    String findErrorPayloadByIdAndAppCode(@Param("id") long id, @Param("appCode") String appCode);

    /** 最后一条事件时间（全量） */
    @Query(value = "SELECT MAX(created_at) FROM trace_event", nativeQuery = true)
    java.util.Date findMaxCreatedAt();

    /** 最后一条事件时间（限定 appCodes） */
    @Query(value = "SELECT MAX(created_at) FROM trace_event WHERE app_code IN (:appCodes)", nativeQuery = true)
    java.util.Date findMaxCreatedAtByAppCodes(@Param("appCodes") java.util.Set<String> appCodes);
}
