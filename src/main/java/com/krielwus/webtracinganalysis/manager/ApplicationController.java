package com.krielwus.webtracinganalysis.manager;

import com.alibaba.fastjson.JSONObject;
import com.krielwus.webtracinganalysis.repository.UserAccountRepository;
import com.krielwus.webtracinganalysis.entity.ApplicationInfo;
import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.ApplicationService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/application")
public class ApplicationController {
    private final ApplicationService service;
    private final UserAccountRepository userRepo;
    public ApplicationController(ApplicationService service, UserAccountRepository userRepo) { this.service = service; this.userRepo = userRepo; }

    @GetMapping("/list")
    public ResultInfo list(javax.servlet.http.HttpSession session) {
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session.getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        
        if (u == null || u.getId() == null) {
            return new ResultInfo(401, "unauthorized");
        }
        
        String userId = String.valueOf(u.getId());
        String username = u.getUsername();
        List<ApplicationInfo> list = service.listByUser(userId, username, role);
        return new ResultInfo(1000, "success", list);
    }

    @GetMapping("/monitor/dailyBase")
    public ResultInfo monitorDailyBase(@RequestParam("appCode") String appCode) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.util.Map<String,Object> item = service.aggregateDailyBaseByApp(appCode.trim(), today);
            java.util.List<java.util.Map<String,Object>> data = java.util.Collections.singletonList(item);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/allBase")
    public ResultInfo monitorAllBase(@RequestParam("appCode") String appCode) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return new ResultInfo(400, "appCode required");
        }
        try {
            java.util.Map<String,Object> item = service.aggregateAllBaseByApp(appCode.trim());
            java.util.List<java.util.Map<String,Object>> data = java.util.Collections.singletonList(item);
            return new ResultInfo(1000, "success", data);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyPV")
    public ResultInfo monitorDailyPV(@RequestBody JSONObject body) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) { return new ResultInfo(400, "appCode required"); }
        if (start == null || end == null) { return new ResultInfo(400, "startDate/endDate required"); }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) { return new ResultInfo(400, "date range invalid"); }
            java.util.List<java.util.Map<String,Object>> list = service.aggregateDailyPVForApp(s, e, appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid"); }
        catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/monitor/dailyUV")
    public ResultInfo monitorDailyUV(@RequestBody JSONObject body) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        String appCode = body.getString("appCode");
        String start = body.getString("startDate");
        String end = body.getString("endDate");
        if (appCode == null || appCode.trim().isEmpty()) { return new ResultInfo(400, "appCode required"); }
        if (start == null || end == null) { return new ResultInfo(400, "startDate/endDate required"); }
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.LocalDate s = java.time.LocalDate.parse(start, fmt);
            java.time.LocalDate e = java.time.LocalDate.parse(end, fmt);
            if (s.isAfter(e)) { return new ResultInfo(400, "date range invalid"); }
            java.util.List<java.util.Map<String,Object>> list = service.aggregateDailyUVForApp(s, e, appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (java.time.format.DateTimeParseException ex) {
            return new ResultInfo(400, "date format invalid"); }
        catch (Exception ex) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/errors/recent")
    public ResultInfo recentErrors(@RequestParam("appCode") String appCode,
                                   @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        if (appCode == null || appCode.trim().isEmpty()) { return new ResultInfo(400, "appCode required"); }
        int l = (limit == null || limit < 1) ? 20 : limit;
        try {
            java.util.List<java.util.Map<String,Object>> list = service.listRecentErrorsByApp(appCode.trim(), l);
            return new ResultInfo(1000, "success", list);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/monitor/weeklyPagePV")
    public ResultInfo weeklyPagePV(@RequestParam("appCode") String appCode,
                                   @RequestParam(value = "days", required = false) Integer days) {
        if (appCode == null || appCode.trim().isEmpty()) { return new ResultInfo(400, "appCode required"); }
        int d = (days == null || days < 1) ? 7 : days;
        try {
            java.time.LocalDate end = java.time.LocalDate.now();
            java.time.LocalDate start = end.minusDays(d - 1);
            java.util.List<java.util.Map<String,Object>> list = service.aggregatePagePVForApp(start, end, appCode.trim());
            return new ResultInfo(1000, "success", list);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/create")
    public ResultInfo create(@RequestBody JSONObject body, javax.servlet.http.HttpSession session) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        String name = body.getString("app_name");
        String prefix = body.getString("app_code_prefix");
        String desc = body.getString("app_desc");
        java.util.List<String> managers = body.getJSONArray("app_managers") == null ? java.util.Collections.emptyList() : body.getJSONArray("app_managers").toJavaList(String.class);
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session.getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (role == null || (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role))) { return new ResultInfo(403, "forbidden"); }
        String creator = (u == null || u.getId() == null) ? null : String.valueOf(u.getId());
        managers = filterValidUserIds(managers);
        try {
            ApplicationInfo saved = service.create(name, prefix, desc, managers, creator);
            return new ResultInfo(1000, "success", saved);
        } catch (IllegalArgumentException e) {
            return new ResultInfo(400, e.getMessage());
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/update")
    public ResultInfo update(@RequestBody JSONObject body, javax.servlet.http.HttpSession session) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        Long id = body.getLong("id");
        if (id == null) { return new ResultInfo(400, "id required"); }
        String name = body.getString("app_name");
        String prefix = body.getString("app_code_prefix");
        String desc = body.getString("app_desc");
        java.util.List<String> managers = body.getJSONArray("app_managers") == null ? null : body.getJSONArray("app_managers").toJavaList(String.class);
        if (managers != null) managers = filterValidUserIds(managers);
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session.getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (u == null || u.getId() == null) { return new ResultInfo(401, "unauthorized"); }
        String operator = "SUPER_ADMIN".equals(role) ? "admin" : String.valueOf(u.getId());
        try {
            ApplicationInfo saved = service.update(id, name, prefix, desc, managers, operator);
            return new ResultInfo(1000, "success", saved);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null) {
                if ("forbidden".equalsIgnoreCase(msg)) return new ResultInfo(403, "forbidden");
                if ("not found".equalsIgnoreCase(msg)) return new ResultInfo(404, "not found");
            }
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @PostMapping("/delete")
    public ResultInfo delete(@RequestBody JSONObject body, javax.servlet.http.HttpSession session) {
        if (body == null) { return new ResultInfo(400, "body required"); }
        Long id = body.getLong("id");
        if (id == null) { return new ResultInfo(400, "id required"); }
        com.krielwus.webtracinganalysis.entity.UserAccount u = (com.krielwus.webtracinganalysis.entity.UserAccount) session.getAttribute("user");
        String role = String.valueOf(session.getAttribute("role"));
        if (u == null || u.getId() == null) { return new ResultInfo(401, "unauthorized"); }
        String operator = "SUPER_ADMIN".equals(role) ? "admin" : String.valueOf(u.getId());
        try {
            service.delete(id, operator);
            return new ResultInfo(1000, "success");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && "forbidden".equalsIgnoreCase(msg)) return new ResultInfo(403, "forbidden");
            return new ResultInfo(400, msg == null ? "bad request" : msg);
        } catch (Exception e) {
            return new ResultInfo(500, "internal error");
        }
    }

    @GetMapping("/users")
    public ResultInfo users(javax.servlet.http.HttpSession session) {
        // 获取当前用户信息
        String currentRole = String.valueOf(session.getAttribute("role"));
        
        // 直接查询非 SUPER_ADMIN 角色用户，提高性能
        java.util.List<com.krielwus.webtracinganalysis.entity.UserAccount> list = userRepo.findByRoleNot("SUPER_ADMIN");
        java.util.List<java.util.Map<String,Object>> users = new java.util.ArrayList<>();
        java.util.Map<String,Object> result = new java.util.HashMap<>();
        
        // 构建返回数据
        for (com.krielwus.webtracinganalysis.entity.UserAccount u : list) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            users.add(m);
        }
        
        result.put("users", users);
        result.put("currentUserRole", currentRole);
        
        return new ResultInfo(1000, "success", result);
    }

    private java.util.List<String> filterValidUserIds(java.util.List<String> managers) {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        for (String m : managers) {
            if (m == null) continue;
            try {
                Long id = Long.valueOf(m);
                com.krielwus.webtracinganalysis.entity.UserAccount ua = userRepo.findById(id).orElse(null);
                if (ua != null) set.add(String.valueOf(id));
            } catch (Exception ignore) {}
        }
        return new java.util.ArrayList<>(set);
    }
}
