package com.krielwus.webtracinganalysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krielwus.webtracinganalysis.entity.BaseInfoRecord;
import com.krielwus.webtracinganalysis.entity.TracingEvent;
import com.krielwus.webtracinganalysis.repository.BaseInfoRecordRepository;
import com.krielwus.webtracinganalysis.repository.TracingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 埋点数据服务。
 * 负责接收前端上报的事件与基线数据，完成解析、入库，
 * 并提供查询与清理等业务能力。
 */
@Service
public class TracingService {
    private final TracingEventRepository tracingEventRepository;
    private final BaseInfoRecordRepository baseInfoRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TracingService(TracingEventRepository tracingEventRepository,
                          BaseInfoRecordRepository baseInfoRecordRepository) {
        this.tracingEventRepository = tracingEventRepository;
        this.baseInfoRecordRepository = baseInfoRecordRepository;
    }

    /**
     * 数据入库：从载荷中提取 eventInfo 与 baseInfo，
     * 按类型分别保存事件与基线记录。
     */
    @Transactional
    public void ingest(Map<String, Object> payload) {
        Object eventInfoObj = payload.get("eventInfo");
        Object baseInfoObj = payload.get("baseInfo");
        Map<String, Object> base = null;
        if (baseInfoObj != null) {
            base = toMap(baseInfoObj);
            BaseInfoRecord record = new BaseInfoRecord();
            record.setPayload(toJson(baseInfoObj));
            baseInfoRecordRepository.save(record);
        }
        if (eventInfoObj != null) {
            List<Map<String, Object>> events = toList(eventInfoObj);
            for (Map<String, Object> e : events) {
                TracingEvent te = new TracingEvent();
                Object type = e.get("eventType");
                te.setEventType(type == null ? "UNKNOWN" : String.valueOf(type));
                te.setPayload(toJson(e));
                String appCode = base != null ? getString(base, "appCode", "APP_CODE") : getString(e, "appCode", "APP_CODE");
                String appName = base != null ? getString(base, "appName", "APP_NAME") : getString(e, "appName", "APP_NAME");
                String sessionId = getString(e, "sessionId", "SESSION_ID");
                if (sessionId == null || sessionId.isEmpty()) {
                    sessionId = base != null ? getString(base, "sessionId", "SESSION_ID") : null;
                }
                te.setAppCode(appCode);
                te.setAppName(appName);
                te.setSessionId(sessionId);
                tracingEventRepository.save(te);
            }
        }
    }

    /**
     * 查询最新基线信息。
     */
    public Map<String, Object> getBaseInfo() {
        BaseInfoRecord latest = baseInfoRecordRepository.findTopByOrderByCreatedAtDesc();
        if (latest == null) return new HashMap<>();
        return fromJson(latest.getPayload(), new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 查询事件列表，支持按事件类型过滤。
     */
    public List<Map<String, Object>> getAllTracingList(String eventType) {
        List<TracingEvent> list = eventType == null || eventType.isEmpty() ?
                tracingEventRepository.findAllByOrderByCreatedAtDesc() :
                tracingEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TracingEvent te : list) {
            Map<String, Object> m = fromJson(te.getPayload(), new TypeReference<Map<String, Object>>() {});
            out.add(m);
        }
        return out;
    }

    /**
     * 清理所有事件与基线记录（开发调试用）。
     */
    @Transactional
    public void cleanAll() {
        tracingEventRepository.deleteAll();
        baseInfoRecordRepository.deleteAll();
    }

    /**
     * 统计指定日期的基础指标（基于 trace_event）。
     */
    public Map<String, Object> aggregateDailyBase(LocalDate date) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
        Set<String> apps = distinctAppCodesRange(start, end);
        Set<String> users = distinctSdkUserUuidsRange(start, end);
        Set<String> devices = distinctDeviceIdsRange(start, end);
        Set<String> sessions = distinctSessionIdsRange(start, end);
        int pv = 0;
        int click = 0;
        for (TracingEvent e : events) {
            Map<String, Object> m = parsePayload(e);
            String type = getString(m, "eventType", "EVENT_TYPE");
            if (type != null) {
                if ("CLICK".equalsIgnoreCase(type)) click++;
            }
            if (isPV(m, type)) pv++;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("APPLICATION_NUM", apps.size());
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        return item;
    }

    /**
     * 统计所有数据的累计指标（基于 trace_event）。
     */
    public Map<String, Object> aggregateAllBase() {
        List<TracingEvent> events = tracingEventRepository.findAll();
        Set<String> apps = distinctAppCodesAll();
        Set<String> users = distinctSdkUserUuidsAll();
        Set<String> devices = distinctDeviceIdsAll();
        Set<String> sessions = distinctSessionIdsAll();
        int pv = 0;
        int click = 0;
        for (TracingEvent e : events) {
            Map<String, Object> m = parsePayload(e);
            String type = getString(m, "eventType", "EVENT_TYPE");
            if (type != null) {
                if ("CLICK".equalsIgnoreCase(type)) click++;
            }
            if (isPV(m, type)) pv++;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("APPLICATION_NUM", apps.size());
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        return item;
    }

    /**
     * 统计日期范围内各版本的每日 PV 数。
     */
    public List<Map<String, Object>> aggregateDailyPVByVersion(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
            Map<String, Integer> pvByVersion = new HashMap<>();
            for (TracingEvent e : events) {
                Map<String, Object> m = parsePayload(e);
                String type = getString(m, "eventType", "EVENT_TYPE");
                if (!isPV(m, type)) continue;
                String versionId = getString(m, "versionId", "VERSION_ID");
                if (versionId == null || versionId.isEmpty()) continue;
                pvByVersion.put(versionId, pvByVersion.getOrDefault(versionId, 0) + 1);
            }
            List<String> requiredVersions = Arrays.asList("1", "2", "5");
            for (String v : requiredVersions) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("VERSION_ID", v);
                row.put("DATETIME", DF.format(d));
                row.put("PV_NUM", pvByVersion.getOrDefault(v, 0));
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 按应用（appCode）统计日期范围内每日 PV 数，并返回 appCode 与 appName。
     */
    public List<Map<String, Object>> aggregateDailyPVByApp(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> out = new ArrayList<>();
        // 先收集整个日期范围内出现过的应用集合及名称映射（基线 + 事件结构化）
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<BaseInfoRecord> baseInfosAll = baseInfoRecordRepository.findByCreatedAtBetween(rangeStart, rangeEnd);
        Set<String> allCodes = new HashSet<>();
        Map<String, String> nameByCodeGlobal = new HashMap<>();
        for (BaseInfoRecord r : baseInfosAll) {
            Map<String, Object> bm = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String appCode = getString(bm, "appCode", "APP_CODE");
            String appName = getString(bm, "appName", "APP_NAME");
            if (appCode != null && !appCode.isEmpty()) {
                allCodes.add(appCode);
                if (appName != null && !appName.isEmpty()) {
                    nameByCodeGlobal.put(appCode, appName);
                } else {
                    nameByCodeGlobal.putIfAbsent(appCode, appCode);
                }
            }
        }
        List<TracingEvent> eventsAll = tracingEventRepository.findByCreatedAtBetween(rangeStart, rangeEnd);
        for (TracingEvent e : eventsAll) {
            String code = e.getAppCode();
            String name = e.getAppName();
            if (code != null && !code.isEmpty()) {
                allCodes.add(code);
                if (name != null && !name.isEmpty()) {
                    nameByCodeGlobal.put(code, name);
                } else {
                    nameByCodeGlobal.putIfAbsent(code, code);
                }
            }
        }

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            // 当天事件与基线记录
            List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
            List<BaseInfoRecord> baseInfosDay = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
            // 当天 sessionId -> appCode 映射（优先当天）
            Map<String, String> codeBySession = new HashMap<>();
            for (BaseInfoRecord r : baseInfosDay) {
                Map<String, Object> bm = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
                String sessionId = getString(bm, "sessionId", "SESSION_ID");
                String appCode = getString(bm, "appCode", "APP_CODE");
                if (sessionId != null && !sessionId.isEmpty() && appCode != null && !appCode.isEmpty()) {
                    codeBySession.put(sessionId, appCode);
                }
            }
            // 统计 PV 按应用聚合（优先事件结构化列，其次当天 session 映射，最后事件 payload）
            Map<String, Integer> pvByApp = new HashMap<>();
            for (TracingEvent e : events) {
                Map<String, Object> m = parsePayload(e);
                String type = e.getEventType() != null ? e.getEventType() : getString(m, "eventType", "EVENT_TYPE");
                if (!isPV(m, type)) continue;
                String appCode = e.getAppCode();
                if (appCode == null || appCode.isEmpty()) {
                    String sessionId = e.getSessionId() != null ? e.getSessionId() : getString(m, "sessionId", "SESSION_ID");
                    if (sessionId != null && !sessionId.isEmpty()) {
                        appCode = codeBySession.get(sessionId);
                    }
                }
                if (appCode == null || appCode.isEmpty()) {
                    appCode = getString(m, "appCode", "APP_CODE");
                }
                if (appCode == null || appCode.isEmpty()) continue;
                pvByApp.put(appCode, pvByApp.getOrDefault(appCode, 0) + 1);
            }
            // 输出当日每个应用的 PV 行（缺失应用填 0）
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCodeGlobal.getOrDefault(code, code));
                row.put("DATETIME", DF.format(d));
                row.put("PV_NUM", pvByApp.getOrDefault(code, 0));
                out.add(row);
            }
        }
        return out;
    }

    public Map<String, Object> aggregateDailyBaseByApp(String appCode, LocalDate date) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
        Set<String> users = distinctSdkUserUuidsRangeByApp(start, end, appCode);
        Set<String> devices = distinctDeviceIdsRangeByApp(start, end, appCode);
        Set<String> sessions = distinctSessionIdsRangeByApp(start, end, appCode);
        int pv = 0;
        int click = 0;
        for (TracingEvent e : events) {
            if (appCode != null && appCode.length() > 0) {
                String code = e.getAppCode();
                if (code == null || !code.equals(appCode)) continue;
            }
            Map<String, Object> m = parsePayload(e);
            String type = e.getEventType() != null ? e.getEventType() : getString(m, "eventType", "EVENT_TYPE");
            if (type != null) {
                if ("CLICK".equalsIgnoreCase(type)) click++;
            }
            if (isPV(m, type)) pv++;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("APPLICATION_NUM", 1);
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        return item;
    }

    public Map<String, Object> aggregateAllBaseByApp(String appCode) {
        List<TracingEvent> events = tracingEventRepository.findAll();
        Set<String> users = distinctSdkUserUuidsAllByApp(appCode);
        Set<String> devices = distinctDeviceIdsAllByApp(appCode);
        Set<String> sessions = distinctSessionIdsAllByApp(appCode);
        int pv = 0;
        int click = 0;
        for (TracingEvent e : events) {
            if (appCode != null && appCode.length() > 0) {
                String code = e.getAppCode();
                if (code == null || !code.equals(appCode)) continue;
            }
            Map<String, Object> m = parsePayload(e);
            String type = e.getEventType() != null ? e.getEventType() : getString(m, "eventType", "EVENT_TYPE");
            if (type != null) {
                if ("CLICK".equalsIgnoreCase(type)) click++;
            }
            if (isPV(m, type)) pv++;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("APPLICATION_NUM", 1);
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        return item;
    }

    public List<Map<String, Object>> aggregateDailyPVForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
            int pv = 0;
            for (TracingEvent e : events) {
                if (appCode != null && appCode.length() > 0) {
                    String code = e.getAppCode();
                    if (code == null || !code.equals(appCode)) continue;
                }
                Map<String, Object> m = parsePayload(e);
                String type = e.getEventType() != null ? e.getEventType() : getString(m, "eventType", "EVENT_TYPE");
                if (isPV(m, type)) pv++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("APP_CODE", appCode);
            row.put("DATETIME", DF.format(d));
            row.put("PV_NUM", pv);
            out.add(row);
        }
        return out;
    }

    /** 将事件载荷解析为 Map */
    private Map<String, Object> parsePayload(TracingEvent e) {
        return fromJson(e.getPayload(), new TypeReference<Map<String, Object>>() {});
    }

    /** 兼容大小写与不同命名的字段读取 */
    private String getString(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    /** 判断是否为 PV 事件，兼容常见别名与页面事件特征 */
    private boolean isPV(Map<String, Object> m, String type) {
        if (type == null) return false;
        return "PV".equalsIgnoreCase(type);
    }

    /**
     * 对象转为 JSON 字符串（失败返回空对象）。
     */
    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    /**
     * 兼容多种类型，将载荷转换为事件列表。
     */
    private List<Map<String, Object>> toList(Object obj) {
        if (obj instanceof List) {
            return (List<Map<String, Object>>) obj;
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }

    /**
     * JSON 解析为目标类型（失败返回 null）。
     */
    private <T> T fromJson(String s, TypeReference<T> ref) {
        try { return objectMapper.readValue(s, ref); } catch (Exception e) { return null; }
    }

    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException e) {
            return new HashMap<>();
        }
    }

    private Set<String> distinctAppCodesRange(Date start, Date end) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String appCode = getString(m, "appCode", "APP_CODE");
            if (appCode != null && !appCode.isEmpty()) set.add(appCode);
        }
        return set;
    }

    private Set<String> distinctDeviceIdsRangeByApp(Date start, Date end, String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) set.add(deviceId);
        }
        return set;
    }

    private Set<String> distinctSessionIdsRangeByApp(Date start, Date end, String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) set.add(sessionId);
        }
        return set;
    }

    private Set<String> distinctSdkUserUuidsRangeByApp(Date start, Date end, String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) set.add(uid);
        }
        return set;
    }

    private Set<String> distinctDeviceIdsAllByApp(String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) set.add(deviceId);
        }
        return set;
    }

    private Set<String> distinctSessionIdsAllByApp(String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) set.add(sessionId);
        }
        return set;
    }

    private Set<String> distinctSdkUserUuidsAllByApp(String appCode) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String code = getString(m, "appCode", "APP_CODE");
            if (appCode != null && appCode.length() > 0 && (code == null || !code.equals(appCode))) continue;
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) set.add(uid);
        }
        return set;
    }

    private Set<String> distinctAppCodesAll() {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String appCode = getString(m, "appCode", "APP_CODE");
            if (appCode != null && !appCode.isEmpty()) set.add(appCode);
        }
        return set;
    }

    private Set<String> distinctDeviceIdsRange(Date start, Date end) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) set.add(deviceId);
        }
        return set;
    }

    private Set<String> distinctDeviceIdsAll() {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) set.add(deviceId);
        }
        return set;
    }

    private Set<String> distinctSessionIdsRange(Date start, Date end) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) set.add(sessionId);
        }
        return set;
    }

    private Set<String> distinctSessionIdsAll() {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) set.add(sessionId);
        }
        return set;
    }

    private Set<String> distinctSdkUserUuidsRange(Date start, Date end) {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) set.add(uid);
        }
        return set;
    }

    private Set<String> distinctSdkUserUuidsAll() {
        List<BaseInfoRecord> records = baseInfoRecordRepository.findAll();
        Set<String> set = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) set.add(uid);
        }
        return set;
    }
}
