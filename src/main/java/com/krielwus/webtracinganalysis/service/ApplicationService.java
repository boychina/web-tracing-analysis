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
    private static final Pattern NAME_RULE = Pattern.compile("^.{2,16}$");
    private static final Pattern PREFIX_RULE = Pattern.compile("^[A-Za-z0-9_]{2,8}$");

    public ApplicationService(ApplicationInfoRepository repo) { this.repo = repo; }

    public List<ApplicationInfo> listAll() { return repo.findAll(); }

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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'aggregateDailyPVForApp'");
    }

    public Map<String, Object> aggregateDailyBaseByApp(String trim, LocalDate today) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'aggregateDailyBaseByApp'");
    }

    public Map<String, Object> aggregateAllBaseByApp(String trim) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'aggregateAllBaseByApp'");
    }
}
