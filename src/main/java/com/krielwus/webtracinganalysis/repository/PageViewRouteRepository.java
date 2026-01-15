package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.PageViewRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;

public interface PageViewRouteRepository extends JpaRepository<PageViewRoute, Long> {
    @Query(value = "SELECT route_path AS path, COUNT(*) AS pv, " +
            "COUNT(DISTINCT session_id) AS session_num, " +
            "COUNT(DISTINCT sdk_user_uuid) AS user_num " +
            "FROM page_view_route " +
            "WHERE app_code = :appCode AND created_at BETWEEN :start AND :end " +
            "GROUP BY route_path " +
            "ORDER BY pv DESC", nativeQuery = true)
    List<Object[]> countRoutePvForAppBetween(@Param("appCode") String appCode,
                                             @Param("start") Date start,
                                             @Param("end") Date end);

    Page<PageViewRoute> findByAppCodeAndRoutePathAndCreatedAtBetweenOrderByCreatedAtDesc(String appCode, String routePath, Date start, Date end, Pageable pageable);

    List<PageViewRoute> findByAppCodeAndSessionIdAndCreatedAtBetweenOrderByCreatedAtAsc(String appCode, String sessionId, Date start, Date end);

    @Query(value = "SELECT session_id FROM page_view_route " +
            "WHERE app_code = :appCode AND created_at BETWEEN :start AND :end " +
            "GROUP BY session_id " +
            "ORDER BY MAX(created_at) DESC", nativeQuery = true)
    List<String> findRecentSessionIdsBetween(@Param("appCode") String appCode,
                                            @Param("start") Date start,
                                            @Param("end") Date end,
                                            Pageable pageable);

    @Query("SELECT p FROM PageViewRoute p WHERE p.appCode = :appCode AND p.sessionId IN (:sessionIds) AND p.createdAt BETWEEN :start AND :end ORDER BY p.sessionId ASC, p.createdAt ASC")
    List<PageViewRoute> findByAppCodeAndSessionIdsBetweenOrdered(@Param("appCode") String appCode,
                                                                 @Param("sessionIds") List<String> sessionIds,
                                                                 @Param("start") Date start,
                                                                 @Param("end") Date end);
}
