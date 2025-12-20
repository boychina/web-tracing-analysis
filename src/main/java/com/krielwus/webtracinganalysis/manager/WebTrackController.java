package com.krielwus.webtracinganalysis.manager;

import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.TracingService;
import com.krielwus.webtracinganalysis.service.ApplicationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 监控大屏数据接口。
 * 为前端分析页提供基础与累计指标，以及指定日期范围的日维度曲线数据。
 * 当前实现为演示用的 mock 数据，后续可替换为数据库聚合结果。
 */
@RestController
@RequestMapping("/api/webTrack")
public class WebTrackController {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final TracingService tracingService;
    private final ApplicationService applicationService;

    public WebTrackController(TracingService tracingService, ApplicationService applicationService) {
        this.tracingService = tracingService;
        this.applicationService = applicationService;
    }

    /**
     * 当天基础指标：应用数、用户数、设备数、会话数、点击量、PV。
     */
    @GetMapping("/queryDailyBaseInfo")
    public ResultInfo queryDailyBaseInfo(javax.servlet.http.HttpSession session) {
        // 获取当前用户信息
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String userRole = roleObj != null ? String.valueOf(roleObj) : null;
        
        // 如果用户未登录，返回空数据
        if (userId == null || userRole == null) {
            Map<String, Object> emptyItem = new LinkedHashMap<>();
            emptyItem.put("DAY_TIME", DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now()));
            emptyItem.put("APPLICATION_NUM", 0);
            emptyItem.put("USER_COUNT", 0);
            emptyItem.put("DEVICE_NUM", 0);
            emptyItem.put("SESSION_UNM", 0);
            emptyItem.put("CLICK_NUM", 0);
            emptyItem.put("PV_NUM", 0);
            emptyItem.put("ERROR_NUM", 0);
            List<Map<String, Object>> data = Collections.singletonList(emptyItem);
            return new ResultInfo(1000, "success", data);
        }
        
        Map<String, Object> item;
        if ("SUPER_ADMIN".equals(userRole)) {
            // 超级管理员可以看到所有应用的统计
            item = tracingService.aggregateDailyBase(LocalDate.now());
        } else {
            // 普通用户只能看到有权限应用的统计
            item = tracingService.aggregateDailyBaseForUser(LocalDate.now(), userId, username);
        }
        List<Map<String, Object>> data = Collections.singletonList(item);
        return new ResultInfo(1000, "success", data);
    }

    /**
     * 历史累计指标：累计应用数、用户数、设备数、会话数、点击量、PV。
     */
    @GetMapping("/queryAllBaseInfo")
    public ResultInfo queryAllBaseInfo(javax.servlet.http.HttpSession session) {
        // 获取当前用户信息
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String userRole = roleObj != null ? String.valueOf(roleObj) : null;
        
        // 如果用户未登录，返回空数据
        if (userId == null || userRole == null) {
            Map<String, Object> emptyItem = new LinkedHashMap<>();
            emptyItem.put("APPLICATION_NUM", 0);
            emptyItem.put("USER_COUNT", 0);
            emptyItem.put("DEVICE_NUM", 0);
            emptyItem.put("SESSION_UNM", 0);
            emptyItem.put("CLICK_NUM", 0);
            emptyItem.put("PV_NUM", 0);
            emptyItem.put("ERROR_NUM", 0);
            List<Map<String, Object>> data = Collections.singletonList(emptyItem);
            return new ResultInfo(1000, "success", data);
        }
        
        Map<String, Object> item;
        if ("SUPER_ADMIN".equals(userRole)) {
            // 超级管理员可以看到所有应用的统计
            item = tracingService.aggregateAllBase();
        } else {
            // 普通用户只能看到有权限应用的统计
            item = tracingService.aggregateAllBaseForUser(userId, username);
        }
        List<Map<String, Object>> data = Collections.singletonList(item);
        return new ResultInfo(1000, "success", data);
    }

    /** 日期范围入参 */
    public static class DateRangeDTO {
        private String startDate;
        private String endDate;

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }

    /**
     * 指定日期范围的日维度指标曲线（按应用 appCode 拆分系列，返回 appCode 与 appName）。
     */
    @PostMapping("/queryDailyInfo")
    public ResultInfo queryDailyInfo(@RequestBody DateRangeDTO dto, javax.servlet.http.HttpSession session) {
        // 获取当前用户信息
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String userRole = roleObj != null ? String.valueOf(roleObj) : null;
        
        // 如果用户未登录，返回空数据
        if (userId == null || userRole == null) {
            return new ResultInfo(1000, "success", new ArrayList<>());
        }
        
        String start = dto.getStartDate();
        String end = dto.getEndDate();
        LocalDate startDate = LocalDate.parse(start, DF);
        LocalDate endDate = LocalDate.parse(end, DF);
        
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(userRole)) {
            // 超级管理员可以看到所有应用的统计
            list = tracingService.aggregateDailyPVByApp(startDate, endDate);
        } else {
            // 普通用户只能看到有权限应用的统计
            list = tracingService.aggregateDailyPVByAppForUser(startDate, endDate, userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }
}
