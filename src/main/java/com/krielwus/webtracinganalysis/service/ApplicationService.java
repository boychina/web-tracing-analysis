package com.krielwus.webtracinganalysis.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.krielwus.webtracinganalysis.entity.ApplicationInfo;
import com.krielwus.webtracinganalysis.repository.ApplicationInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ApplicationService {
    private final ApplicationInfoRepository repo;
    private final TracingService tracingService;
    private static final Pattern NAME_RULE = Pattern.compile("^.{2,16}$");
    private static final Pattern PREFIX_RULE = Pattern.compile("^[A-Za-z0-9_]{2,16}$");

    public ApplicationService(ApplicationInfoRepository repo, TracingService tracingService) { this.repo = repo; this.tracingService = tracingService; }

    public List<ApplicationInfo> listAll() { return repo.findAll(); }

    public List<ApplicationInfo> listByUser(String userId, String username, String userRole) {
        if ("SUPER_ADMIN".equals(userRole)) {
            return repo.findAll();
        }
        
        // 对于普通用户，app_managers 是 JSON 数组，只能在内存中过滤
        List<ApplicationInfo> allApps = repo.findAll();
        return allApps.stream()
                .filter(app -> app.getAppManagers() != null && !app.getAppManagers().trim().isEmpty())
                .filter(app -> isUserManager(app, userId, username))
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean isUserManager(ApplicationInfo app, String userId, String username) {
        if (app.getAppManagers() == null) {
            return false;
        }
        try {
            java.util.List<String> managers = JSON.parseArray(app.getAppManagers(), String.class);
            if (managers == null) return false;
            // 兼容存储 userId（数字或字符串）或 username 的情况
            if (userId != null && managers.contains(userId)) return true;
            if (username != null && managers.contains(username)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public ApplicationInfo create(String appName, String appCodePrefix, String appDesc, java.util.List<String> managers, String creator) {
        validate(appName, appCodePrefix);
        String code = genCode(appName, appCodePrefix);
        while (repo.findByAppCode(code) != null) { code = genCode(appName, appCodePrefix); }
        ApplicationInfo ai = new ApplicationInfo();
        ai.setAppName(appName);
        ai.setAppCodePrefix(appCodePrefix);
        ai.setAppDesc(appDesc);
        ai.setAppCode(code);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (creator != null && !creator.trim().isEmpty()) set.add(creator.trim());
        if (managers != null) for (String m : managers) { if (m != null && !m.trim().isEmpty()) set.add(m.trim()); }
        ai.setAppManagers(JSON.toJSONString(new java.util.ArrayList<>(set)));
        return repo.save(ai);
    }

    @Transactional
    public ApplicationInfo update(Long id, String appName, String appCodePrefix, String appDesc, java.util.List<String> managers, String operator) {
        validate(appName, appCodePrefix);
        Optional<ApplicationInfo> opt = repo.findById(id);
        if (!opt.isPresent()) throw new IllegalArgumentException("not found");
        ApplicationInfo ai = opt.get();
        if (!canOperate(ai, operator)) throw new IllegalArgumentException("forbidden");
        ai.setAppName(appName);
        ai.setAppCodePrefix(appCodePrefix);
        ai.setAppDesc(appDesc);
        if (managers != null) {
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            for (String m : managers) { if (m != null && !m.trim().isEmpty()) set.add(m.trim()); }
            ai.setAppManagers(JSON.toJSONString(new java.util.ArrayList<>(set)));
        }
        return repo.save(ai);
    }

    @Transactional
    public void delete(Long id, String operator) {
        Optional<ApplicationInfo> opt = repo.findById(id);
        if (!opt.isPresent()) return;
        if (!canOperate(opt.get(), operator)) throw new IllegalArgumentException("forbidden");
        repo.deleteById(id);
    }

    private boolean canOperate(ApplicationInfo ai, String operator) {
        if (operator == null) return false;
        if ("admin".equalsIgnoreCase(operator)) return true;
        try {
            java.util.List<String> list = JSON.parseArray(ai.getAppManagers(), String.class);
            return list != null && list.contains(operator);
        } catch (Exception e) {
            return false;
        }
    }

    private void validate(String appName, String appCodePrefix) {
        if (appName == null || !NAME_RULE.matcher(appName).matches())
            throw new IllegalArgumentException("app_name invalid");
        if (appCodePrefix == null || !PREFIX_RULE.matcher(appCodePrefix).matches())
            throw new IllegalArgumentException("app_code_prefix invalid");
    }

    private String genCode(String appName, String prefix) {
        String base = prefix + ":" + appName + ":" + System.nanoTime() + ":" + RandomUtil.randomString(6);
        String hex = SecureUtil.sha1(base);
        String alnum = hex.replaceAll("[^A-Za-z0-9]", "");
        return alnum.substring(0, Math.min(11, alnum.length()));
    }

    public List<Map<String, Object>> aggregateDailyPVForApp(LocalDate s, LocalDate e, String trim) {
        return tracingService.aggregateDailyPVForApp(s, e, trim);
    }

    public Map<String, Object> aggregateDailyBaseByApp(String trim, LocalDate today) {
        return tracingService.aggregateDailyBaseByApp(trim, today);
    }

    public Map<String, Object> aggregateAllBaseByApp(String trim) {
        return tracingService.aggregateAllBaseByApp(trim);
    }

    public List<Map<String, Object>> aggregatePagePVForApp(LocalDate start, LocalDate end, String trim) {
        return tracingService.aggregatePagePVForApp(start, end, trim);
    }

    public List<Map<String, Object>> aggregateDailyUVForApp(LocalDate start, LocalDate end, String appCode) {
        return tracingService.aggregateDailyUVForApp(start, end, appCode);
    }

    public List<Map<String, Object>> listRecentErrorsByApp(String appCode, int limit) {
        return tracingService.listRecentErrorsByApp(appCode, limit);
    }

    public Map<String, Object> pageRecentErrorsByApp(String appCode, int pageNo, int pageSize) {
        return tracingService.pageRecentErrorsByApp(appCode, pageNo, pageSize);
    }

    public String getErrorPayloadByApp(String appCode, long id) {
        return tracingService.getErrorPayloadByApp(appCode, id);
    }
}
