package com.krielwus.webtracinganalysis.manager;

import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.TracingService;
import com.krielwus.webtracinganalysis.service.ApplicationService;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

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
    public ResultInfo queryDailyBaseInfo(HttpSession session) {
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
    public ResultInfo queryAllBaseInfo(HttpSession session) {
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
    public ResultInfo queryDailyInfo(@RequestBody DateRangeDTO dto, HttpSession session) {
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

    /**
     * 状态看板：今日基础指标 + 延迟状态。
     */
    @GetMapping("/statusBoard")
    public ResultInfo statusBoard(HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        boolean superAdmin = "SUPER_ADMIN".equals(role);
        Map<String, Object> data = tracingService.statusBoard(LocalDate.now(), userId, username, superAdmin);
        return new ResultInfo(1000, "success", data);
    }

    /**
     * PV 趋势（按日汇总，总量）。
     */
    @PostMapping("/trend/pv")
    public ResultInfo trendPv(@RequestBody DateRangeDTO dto, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        LocalDate startDate = LocalDate.parse(dto.getStartDate(), DF);
        LocalDate endDate = LocalDate.parse(dto.getEndDate(), DF);
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.aggregateDailyCountByEventType(startDate, endDate, "PV");
        } else {
            list = tracingService.aggregateDailyCountByEventTypeForUser(startDate, endDate, "PV", userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    /**
     * UV 趋势（按日汇总，总量，基于基线表去重）。
     */
    @PostMapping("/trend/uv")
    public ResultInfo trendUv(@RequestBody DateRangeDTO dto, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        LocalDate startDate = LocalDate.parse(dto.getStartDate(), DF);
        LocalDate endDate = LocalDate.parse(dto.getEndDate(), DF);
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.aggregateDailyUV(startDate, endDate);
        } else {
            list = tracingService.aggregateDailyUVForUser(startDate, endDate, userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    /**
     * UV 趋势（按日汇总，按应用拆分，基线表去重）。
     */
    @PostMapping("/trend/uvByApp")
    public ResultInfo trendUvByApp(@RequestBody DateRangeDTO dto, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        LocalDate startDate = LocalDate.parse(dto.getStartDate(), DF);
        LocalDate endDate = LocalDate.parse(dto.getEndDate(), DF);
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.aggregateDailyUVByApp(startDate, endDate);
        } else {
            list = tracingService.aggregateDailyUVByAppForUser(startDate, endDate, userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    /**
     * 错误趋势（event_type = ERROR，按日汇总，总量）。
     */
    @PostMapping("/trend/error")
    public ResultInfo trendError(@RequestBody DateRangeDTO dto, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        LocalDate startDate = LocalDate.parse(dto.getStartDate(), DF);
        LocalDate endDate = LocalDate.parse(dto.getEndDate(), DF);
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.aggregateDailyCountByEventTypeByApp(startDate, endDate, "ERROR");
        } else {
            list = tracingService.aggregateDailyCountByEventTypeByAppForUser(startDate, endDate, "ERROR", userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    /**
     * 最近事件（默认10条）。
     */
    @GetMapping("/events/recent")
    public ResultInfo recentEvents(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
            HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.listRecentEvents(limit);
        } else {
            list = tracingService.listRecentEvents(limit, userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    @GetMapping("/events/recentByApp")
    public ResultInfo recentEventsByApp(@RequestParam("appCode") String appCode,
                                        @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
            HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        try {
            List<Map<String, Object>> list;
            if ("SUPER_ADMIN".equals(role)) {
                list = tracingService.listRecentEventsByApp(appCode, limit, null, null);
            } else {
                list = tracingService.listRecentEventsByApp(appCode, limit, userId, username);
            }
            return new ResultInfo(1000, "success", list);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("forbidden".equalsIgnoreCase(msg)) {
                return new ResultInfo(403, "forbidden");
            }
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    /**
     * 最近错误事件（默认20条）。
     */
    @GetMapping("/errors/recent")
    public ResultInfo recentErrors(@RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        List<Map<String, Object>> list;
        if ("SUPER_ADMIN".equals(role)) {
            list = tracingService.listRecentErrors(limit);
        } else {
            list = tracingService.listRecentErrors(limit, userId, username);
        }
        return new ResultInfo(1000, "success", list);
    }

    @GetMapping("/errors/detail")
    public ResultInfo errorDetail(@RequestParam("id") Long id,
                                  @RequestParam(value = "appCode", required = false) String appCode,
            HttpSession session) {
        if (id == null || id < 1) return new ResultInfo(400, "id required");
        Object roleObj = session.getAttribute("role");
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        try {
            String payload;
            if ("SUPER_ADMIN".equals(role)) {
                payload = tracingService.getErrorPayload(id);
            } else {
                if (appCode == null || appCode.trim().isEmpty()) {
                    return new ResultInfo(400, "appCode required");
                }
                payload = tracingService.getErrorPayloadByApp(appCode.trim(), id);
            }
            if (payload == null) return new ResultInfo(404, "not found");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("PAYLOAD", payload);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    /**
     * 一键数据验证：写入一条PV测试事件。
     */
    @PostMapping("/verify")
    public ResultInfo verify(@RequestBody Map<String, String> body, HttpSession session) {
        Object userIdObj = session.getAttribute("userId");
        Object usernameObj = session.getAttribute("username");
        Object roleObj = session.getAttribute("role");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : null;
        String username = usernameObj != null ? String.valueOf(usernameObj) : null;
        String role = roleObj != null ? String.valueOf(roleObj) : null;
        String appCode = body == null ? null : body.get("appCode");
        try {
            Map<String, Object> result;
            if ("SUPER_ADMIN".equals(role)) {
                result = tracingService.simulateVerifyEvent(appCode, null, null);
            } else {
                result = tracingService.simulateVerifyEvent(appCode, userId, username);
            }
            return new ResultInfo(1000, "success", result);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("forbidden".equalsIgnoreCase(msg)) {
                return new ResultInfo(403, "forbidden");
            }
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }
}
