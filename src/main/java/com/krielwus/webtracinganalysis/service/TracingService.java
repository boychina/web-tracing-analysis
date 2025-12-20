package com.krielwus.webtracinganalysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krielwus.webtracinganalysis.entity.BaseInfoRecord;
import com.krielwus.webtracinganalysis.entity.TracingEvent;
import com.krielwus.webtracinganalysis.repository.BaseInfoRecordRepository;
import com.krielwus.webtracinganalysis.repository.ApplicationInfoRepository;
import com.krielwus.webtracinganalysis.repository.TracingEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ApplicationInfoRepository applicationInfoRepository;
    private final ApplicationService applicationService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Value("${tracing.ingest.queue.maxSize:20000}")
    private int queueMaxSize;
    @Value("${tracing.ingest.batch.size:100}")
    private int batchSize;
    @Value("${tracing.ingest.batch.lingerMs:200}")
    private long lingerMs;
    @Value("${tracing.ingest.consumer.threads:2}")
    private int consumerThreads;
    @Value("${tracing.ingest.offerTimeoutMs:10}")
    private long offerTimeoutMs;
    private BlockingQueue<Map<String, Object>> ingestQueue;
    private ExecutorService consumerPool;
    
    // 缓存用户权限应用代码集合，避免重复查询；key 兼容 userId 和 username
    private final ConcurrentHashMap<String, Set<String>> userAppCodesCache = new ConcurrentHashMap<>();

    @Autowired
    public TracingService(TracingEventRepository tracingEventRepository,
                          BaseInfoRecordRepository baseInfoRecordRepository,
                          ApplicationInfoRepository applicationInfoRepository,
                          @Lazy ApplicationService applicationService,
                          PlatformTransactionManager transactionManager) {
        this.tracingEventRepository = tracingEventRepository;
        this.baseInfoRecordRepository = baseInfoRecordRepository;
        this.applicationInfoRepository = applicationInfoRepository;
        this.applicationService = applicationService;
        this.transactionManager = transactionManager;
    }

    @PostConstruct
    public void initIngest() {
        ingestQueue = new LinkedBlockingQueue<>(queueMaxSize);
        consumerPool = Executors.newFixedThreadPool(consumerThreads);
        for (int i = 0; i < consumerThreads; i++) {
            consumerPool.submit(this::runConsumerLoop);
        }
    }

    @PreDestroy
    public void shutdownConsumers() {
        if (consumerPool != null) {
            consumerPool.shutdownNow();
        }
    }

    public boolean ingestAsync(Map<String, Object> payload) {
        if (payload == null) return true;
        try {
            return ingestQueue.offer(payload, offerTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void runConsumerLoop() {
        java.util.ArrayList<Map<String, Object>> batch = new java.util.ArrayList<>(batchSize);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<String, Object> item = ingestQueue.poll(lingerMs, TimeUnit.MILLISECONDS);
                if (item != null) {
                    batch.add(item);
                    ingestQueue.drainTo(batch, Math.max(0, batchSize - batch.size()));
                }
                if (!batch.isEmpty() && (batch.size() >= batchSize || item == null)) {
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (Exception ignored) {
            }
        }
        if (!batch.isEmpty()) flushBatch(batch);
    }

    private void flushBatch(java.util.List<Map<String, Object>> payloads) {
        org.springframework.transaction.support.TransactionTemplate tt = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tt.execute(status -> {
            java.util.ArrayList<BaseInfoRecord> baseRecords = new java.util.ArrayList<>();
            java.util.ArrayList<TracingEvent> eventRecords = new java.util.ArrayList<>();
            for (Map<String, Object> payload : payloads) {
                Object eventInfoObj = payload.get("eventInfo");
                Object baseInfoObj = payload.get("baseInfo");
                Map<String, Object> base = null;
                if (baseInfoObj != null) {
                    base = toMap(baseInfoObj);
                    BaseInfoRecord record = new BaseInfoRecord();
                    record.setPayload(toJson(baseInfoObj));
                    baseRecords.add(record);
                }
                if (eventInfoObj != null) {
                    java.util.List<Map<String, Object>> events = toList(eventInfoObj);
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
                        eventRecords.add(te);
                    }
                }
            }
            if (!baseRecords.isEmpty()) baseInfoRecordRepository.saveAll(baseRecords);
            if (!eventRecords.isEmpty()) tracingEventRepository.saveAll(eventRecords);
            return null;
        });
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
            List<TracingEvent> batch = new ArrayList<>(events.size());
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
                batch.add(te);
            }
            if (!batch.isEmpty()) tracingEventRepository.saveAll(batch);
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
        int appCount = (int) tracingEventRepository.countDistinctAppCodeBetween(start, end);
        int userCount = (int) tracingEventRepository.countDistinctSdkUserUuidBetween(start, end);
        int deviceCount = (int) tracingEventRepository.countDistinctDeviceIdBetween(start, end);
        int sessionCount = (int) tracingEventRepository.countDistinctSessionIdBetween(start, end);
        int pv = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("PV", start, end);
        int click = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("CLICK", start, end);
        int error = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("ERROR", start, end);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("APPLICATION_NUM", appCount);
        item.put("USER_COUNT", userCount);
        item.put("DEVICE_NUM", deviceCount);
        item.put("SESSION_UNM", sessionCount);
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
        return item;
    }

    /**
     * 统计指定用户有权限的应用的基础指标（基于 trace_event）。
     */
    public Map<String, Object> aggregateDailyBaseForUser(LocalDate date, String userId, String username) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        // 获取用户有权限的应用代码
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        
        // 如果没有权限访问任何应用，返回空数据
        if (userAppCodes.isEmpty()) {
            Map<String, Object> emptyItem = new LinkedHashMap<>();
            emptyItem.put("DAY_TIME", DF.format(date));
            emptyItem.put("APPLICATION_NUM", 0);
            emptyItem.put("USER_COUNT", 0);
            emptyItem.put("DEVICE_NUM", 0);
            emptyItem.put("SESSION_UNM", 0);
            emptyItem.put("CLICK_NUM", 0);
            emptyItem.put("PV_NUM", 0);
            emptyItem.put("ERROR_NUM", 0);
            return emptyItem;
        }
        
        int appCount = (int) tracingEventRepository.countDistinctAppCodeBetweenAndAppCodes(start, end, userAppCodes);
        int userCount = (int) tracingEventRepository.countDistinctSdkUserUuidBetweenAndAppCodes(start, end, userAppCodes);
        int deviceCount = (int) tracingEventRepository.countDistinctDeviceIdBetweenAndAppCodes(start, end, userAppCodes);
        int sessionCount = (int) tracingEventRepository.countDistinctSessionIdBetweenAndAppCodes(start, end, userAppCodes);
        int pv = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("PV", start, end, userAppCodes);
        int click = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("CLICK", start, end, userAppCodes);
        int error = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("ERROR", start, end, userAppCodes);
        
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("APPLICATION_NUM", userAppCodes.size());
        item.put("USER_COUNT", userCount);
        item.put("DEVICE_NUM", deviceCount);
        item.put("SESSION_UNM", sessionCount);
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
        return item;
    }

    /**
     * 统计所有数据的累计指标（基于 trace_event）。
     */
    public Map<String, Object> aggregateAllBase() {
        Set<String> apps = distinctAppCodesAll();
        Set<String> users = distinctSdkUserUuidsAll();
        Set<String> devices = distinctDeviceIdsAll();
        Set<String> sessions = distinctSessionIdsAll();
        int pv = (int) tracingEventRepository.countByEventType("PV");
        int click = (int) tracingEventRepository.countByEventType("CLICK");
        int error = (int) tracingEventRepository.countByEventType("ERROR");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("APPLICATION_NUM", apps.size());
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
        return item;
    }

    /**
     * 统计指定用户有权限的应用的累计指标（基于 trace_event）。
     */
    public Map<String, Object> aggregateAllBaseForUser(String userId, String username) {
        // 获取用户有权限的应用代码
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        
        // 如果没有权限访问任何应用，返回空数据
        if (userAppCodes.isEmpty()) {
            Map<String, Object> emptyItem = new LinkedHashMap<>();
            emptyItem.put("APPLICATION_NUM", 0);
            emptyItem.put("USER_COUNT", 0);
            emptyItem.put("DEVICE_NUM", 0);
            emptyItem.put("SESSION_UNM", 0);
            emptyItem.put("CLICK_NUM", 0);
            emptyItem.put("PV_NUM", 0);
            emptyItem.put("ERROR_NUM", 0);
            return emptyItem;
        }
        
        Set<String> users = distinctSdkUserUuidsAllByAppCodes(userAppCodes);
        Set<String> devices = distinctDeviceIdsAllByAppCodes(userAppCodes);
        Set<String> sessions = distinctSessionIdsAllByAppCodes(userAppCodes);
        int pv = (int) tracingEventRepository.countByEventTypeAndAppCodes("PV", userAppCodes);
        int click = (int) tracingEventRepository.countByEventTypeAndAppCodes("CLICK", userAppCodes);
        int error = (int) tracingEventRepository.countByEventTypeAndAppCodes("ERROR", userAppCodes);
        
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("APPLICATION_NUM", userAppCodes.size());
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
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
     * 按应用（appCode）统计日期范围内每日 PV 数，并返回 appCode 与 appName（仅限用户有权限的应用）。
     */
    public List<Map<String, Object>> aggregateDailyPVByAppForUser(LocalDate startDate, LocalDate endDate, String userId, String username) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        // 获取用户有权限的应用代码
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        
        // 如果没有权限访问任何应用，返回空列表
        if (userAppCodes.isEmpty()) {
            return new ArrayList<>();
        }
        
        java.util.List<Object[]> rows = tracingEventRepository.countDailyPvByAppAndAppCodes(rangeStart, rangeEnd, userAppCodes);
        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty() && userAppCodes.contains(ai.getAppCode())) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }
        
        Set<String> allCodes = new HashSet<>(userAppCodes);
        Map<String, Map<String, Integer>> pv = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            String code = String.valueOf(r[1]);
            int cnt = ((Number) r[2]).intValue();
            allCodes.add(code);
            pv.computeIfAbsent(day, k -> new HashMap<>()).put(code, cnt);
        }
        
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Integer> byCode = pv.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("PV_NUM", byCode.getOrDefault(code, 0));
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 按应用（appCode）统计日期范围内每日 PV 数，并返回 appCode 与 appName。
     */
    public List<Map<String, Object>> aggregateDailyPVByApp(LocalDate startDate, LocalDate endDate) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.List<Object[]> rows = tracingEventRepository.countDailyPvByApp(rangeStart, rangeEnd);
        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty()) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }
        Set<String> allCodes = new HashSet<>();
        Map<String, Map<String, Integer>> pv = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            String code = String.valueOf(r[1]);
            int cnt = ((Number) r[2]).intValue();
            allCodes.add(code);
            pv.computeIfAbsent(day, k -> new HashMap<>()).put(code, cnt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Integer> byCode = pv.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("PV_NUM", byCode.getOrDefault(code, 0));
                out.add(row);
            }
        }
        return out;
    }

    public Map<String, Object> aggregateDailyBaseByApp(String appCode, LocalDate date) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        int userCount = (int) tracingEventRepository.countDistinctSdkUserUuidForAppBetween(appCode, start, end);
        int deviceCount = (int) tracingEventRepository.countDistinctDeviceIdForAppBetween(appCode, start, end);
        int sessionCount = (int) tracingEventRepository.countDistinctSessionIdForAppBetween(appCode, start, end);
        int pv = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("PV", appCode, start, end);
        int click = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("CLICK", appCode, start, end);
        int error = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("ERROR", appCode, start, end);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("USER_COUNT", userCount);
        item.put("DEVICE_NUM", deviceCount);
        item.put("SESSION_UNM", sessionCount);
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
        return item;
    }

    public Map<String, Object> aggregateAllBaseByApp(String appCode) {
        Set<String> users = distinctSdkUserUuidsAllByApp(appCode);
        Set<String> devices = distinctDeviceIdsAllByApp(appCode);
        Set<String> sessions = distinctSessionIdsAllByApp(appCode);
        int pv = (int) tracingEventRepository.countByEventTypeAndAppCode("PV", appCode);
        int click = (int) tracingEventRepository.countByEventTypeAndAppCode("CLICK", appCode);
        int error = (int) tracingEventRepository.countByEventTypeAndAppCode("ERROR", appCode);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("APPLICATION_NUM", 1);
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
        item.put("CLICK_NUM", click);
        item.put("PV_NUM", pv);
        item.put("ERROR_NUM", error);
        return item;
    }

    public List<Map<String, Object>> aggregateDailyPVForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            int pv = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("PV", appCode, start, end);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("APP_CODE", appCode);
            row.put("DATETIME", DF.format(d));
            row.put("PV_NUM", pv);
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> aggregatePagePVForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<TracingEvent> events = tracingEventRepository.findByEventTypeAndAppCodeAndCreatedAtBetween("PV", appCode, start, end);
        Map<String, Integer> pvByPage = new HashMap<>();
        for (TracingEvent e : events) {
            Map<String, Object> m = parsePayload(e);
            String url = getString(m, "triggerPageUrl", "pageUrl", "URL", "PAGE_URL");
            if (url == null || url.isEmpty()) continue;
            pvByPage.put(url, pvByPage.getOrDefault(url, 0) + 1);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(pvByPage.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Integer> en : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("PAGE_URL", en.getKey());
            row.put("PV_NUM", en.getValue());
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

    private Set<String> distinctSessionIdsRangeByAppEvents(Date start, Date end, String appCode) {
        List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (TracingEvent e : events) {
            if (appCode != null && appCode.length() > 0) {
                String code = e.getAppCode();
                if (code == null || !code.equals(appCode)) continue;
            }
            String sessionId = e.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                Map<String, Object> m = parsePayload(e);
                sessionId = getString(m, "sessionId", "SESSION_ID");
            }
            if (sessionId != null && !sessionId.isEmpty()) set.add(sessionId);
        }
        return set;
    }

    private Set<String> distinctDeviceIdsRangeByAppEvents(Date start, Date end, String appCode) {
        List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (TracingEvent e : events) {
            if (appCode != null && appCode.length() > 0) {
                String code = e.getAppCode();
                if (code == null || !code.equals(appCode)) continue;
            }
            Map<String, Object> m = parsePayload(e);
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) set.add(deviceId);
        }
        return set;
    }

    private Set<String> distinctSdkUserUuidsRangeByAppEvents(Date start, Date end, String appCode) {
        List<TracingEvent> events = tracingEventRepository.findByCreatedAtBetween(start, end);
        Set<String> set = new HashSet<>();
        for (TracingEvent e : events) {
            if (appCode != null && appCode.length() > 0) {
                String code = e.getAppCode();
                if (code == null || !code.equals(appCode)) continue;
            }
            Map<String, Object> m = parsePayload(e);
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

    /**
     * 获取用户有权限访问的应用代码集合（带缓存，兼容 userId 与 username）
     */
    private Set<String> getUserAccessibleAppCodes(String userId, String username) {
        if (userId == null && username == null) {
            return new HashSet<>();
        }
        String cacheKey = (userId == null ? "" : userId) + "|" + (username == null ? "" : username);
        
        // 先从缓存中获取
        Set<String> cachedAppCodes = userAppCodesCache.get(cacheKey);
        if (cachedAppCodes != null) {
            return cachedAppCodes;
        }
        
        // 缓存未命中，从数据库查询
        List<com.krielwus.webtracinganalysis.entity.ApplicationInfo> apps = applicationService.listByUser(userId, username, "USER");
        Set<String> appCodes = new HashSet<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo app : apps) {
            if (app.getAppCode() != null && !app.getAppCode().isEmpty()) {
                appCodes.add(app.getAppCode());
            }
        }
        
        // 放入缓存
        userAppCodesCache.put(cacheKey, appCodes);
        return appCodes;
    }

    /**
     * 获取指定应用代码集合的去重用户UUID集合
     */
    private Set<String> distinctSdkUserUuidsAllByAppCodes(Set<String> appCodes) {
        Set<String> set = new HashSet<>();
        for (String appCode : appCodes) {
            Set<String> appUsers = distinctSdkUserUuidsAllByApp(appCode);
            set.addAll(appUsers);
        }
        return set;
    }

    /**
     * 获取指定应用代码集合的去重设备ID集合
     */
    private Set<String> distinctDeviceIdsAllByAppCodes(Set<String> appCodes) {
        Set<String> set = new HashSet<>();
        for (String appCode : appCodes) {
            Set<String> appDevices = distinctDeviceIdsAllByApp(appCode);
            set.addAll(appDevices);
        }
        return set;
    }

    /**
     * 获取指定应用代码集合的去重会话ID集合
     */
    private Set<String> distinctSessionIdsAllByAppCodes(Set<String> appCodes) {
        Set<String> set = new HashSet<>();
        for (String appCode : appCodes) {
            Set<String> appSessions = distinctSessionIdsAllByApp(appCode);
            set.addAll(appSessions);
        }
        return set;
    }
}
