package com.krielwus.webtracinganalysis.repository;

import com.krielwus.webtracinganalysis.entity.PageViewRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface PageViewRouteRepository extends JpaRepository<PageViewRoute, Long> {
    @Query(value = "SELECT route_path AS path, COUNT(*) AS pv " +
            "FROM page_view_route " +
            "WHERE app_code = :appCode AND created_at BETWEEN :start AND :end " +
            "GROUP BY route_path " +
            "ORDER BY pv DESC", nativeQuery = true)
    List<Object[]> countRoutePvForAppBetween(@Param("appCode") String appCode,
                                             @Param("start") Date start,
                                             @Param("end") Date end);
}
