package com.krielwus.webtracinganalysis.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krielwus.webtracinganalysis.entity.BaseInfoRecord;
import com.krielwus.webtracinganalysis.entity.TracingEvent;
import com.krielwus.webtracinganalysis.repository.BaseInfoRecordRepository;
import com.krielwus.webtracinganalysis.repository.ApplicationInfoRepository;
import com.krielwus.webtracinganalysis.repository.TracingEventRepository;
import org.springframework.data.domain.PageRequest;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
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
    private final com.krielwus.webtracinganalysis.config.SessionPathProperties sessionPathProperties;
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
          PlatformTransactionManager transactionManager,
            com.krielwus.webtracinganalysis.repository.PageViewRouteRepository pageViewRouteRepository,
            com.krielwus.webtracinganalysis.config.SessionPathProperties sessionPathProperties) {
        this.tracingEventRepository = tracingEventRepository;
        this.baseInfoRecordRepository = baseInfoRecordRepository;
        this.applicationInfoRepository = applicationInfoRepository;
        this.applicationService = applicationService;
        this.transactionManager = transactionManager;
        this.pageViewRouteRepository = pageViewRouteRepository;
        this.sessionPathProperties = sessionPathProperties;
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
            java.util.ArrayList<com.krielwus.webtracinganalysis.entity.PageViewRoute> routeRecords = new java.util.ArrayList<>();
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
                        if ("PV".equalsIgnoreCase(te.getEventType())) {
                            String fullUrl = getString(e, "triggerPageUrl", "pageUrl", "URL", "PAGE_URL");
                            String sdkUserUuid = getString(e, "sdkUserUuid", "SDK_USER_UUID");
                            if (sdkUserUuid == null || sdkUserUuid.isEmpty()) {
                                sdkUserUuid = base != null ? getString(base, "sdkUserUuid", "SDK_USER_UUID") : null;
                            }
                            String deviceId = getString(e, "deviceId", "DEVICE_ID");
                            if (deviceId == null || deviceId.isEmpty()) {
                                deviceId = base != null ? getString(base, "deviceId", "DEVICE_ID") : null;
                            }
                            String[] parts = parsePageRoute(fullUrl);
                            com.krielwus.webtracinganalysis.entity.PageViewRoute pvr = new com.krielwus.webtracinganalysis.entity.PageViewRoute();
                            pvr.setAppCode(appCode);
                            pvr.setAppName(appName);
                            pvr.setSessionId(sessionId);
                            pvr.setSdkUserUuid(sdkUserUuid);
                            pvr.setDeviceId(deviceId);
                            pvr.setFullUrl(fullUrl);
                            pvr.setRouteType(parts[0]);
                            pvr.setRoutePath(parts[1]);
                            pvr.setRouteParams(parts[2]);
                            routeRecords.add(pvr);
                        }
                    }
                }
            }
            if (!baseRecords.isEmpty()) baseInfoRecordRepository.saveAll(baseRecords);
            if (!eventRecords.isEmpty()) tracingEventRepository.saveAll(eventRecords);
            if (!routeRecords.isEmpty())
                pageViewRouteRepository.saveAll(routeRecords);
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
            java.util.ArrayList<com.krielwus.webtracinganalysis.entity.PageViewRoute> routeRecords = new java.util.ArrayList<>();
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
                if ("PV".equalsIgnoreCase(te.getEventType())) {
                    String fullUrl = getString(e, "triggerPageUrl", "pageUrl", "URL", "PAGE_URL");
                    String sdkUserUuid = getString(e, "sdkUserUuid", "SDK_USER_UUID");
                    if (sdkUserUuid == null || sdkUserUuid.isEmpty()) {
                        sdkUserUuid = base != null ? getString(base, "sdkUserUuid", "SDK_USER_UUID") : null;
                    }
                    String deviceId = getString(e, "deviceId", "DEVICE_ID");
                    if (deviceId == null || deviceId.isEmpty()) {
                        deviceId = base != null ? getString(base, "deviceId", "DEVICE_ID") : null;
                    }
                    String[] parts = parsePageRoute(fullUrl);
                    com.krielwus.webtracinganalysis.entity.PageViewRoute pvr = new com.krielwus.webtracinganalysis.entity.PageViewRoute();
                    pvr.setAppCode(appCode);
                    pvr.setAppName(appName);
                    pvr.setSessionId(sessionId);
                    pvr.setSdkUserUuid(sdkUserUuid);
                    pvr.setDeviceId(deviceId);
                    pvr.setFullUrl(fullUrl);
                    pvr.setRouteType(parts[0]);
                    pvr.setRoutePath(parts[1]);
                    pvr.setRouteParams(parts[2]);
                    routeRecords.add(pvr);
                }
            }
            if (!batch.isEmpty()) tracingEventRepository.saveAll(batch);
            if (!routeRecords.isEmpty())
                pageViewRouteRepository.saveAll(routeRecords);
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
     * 统计指定日期的基础指标（优先基线表 base_info_record 以获得 user/device/session 更准确计数）。
     */
    public Map<String, Object> aggregateDailyBase(LocalDate date) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> apps = new HashSet<>();
        Set<String> users = new HashSet<>();
        Set<String> devices = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (m == null) continue;
            String appCode = getString(m, "appCode", "APP_CODE");
            if (appCode != null && !appCode.isEmpty()) apps.add(appCode);
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) users.add(uid);
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) devices.add(deviceId);
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) sessions.add(sessionId);
        }

        int pv = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("PV", start, end);
        int click = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("CLICK", start, end);
        int error = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetween("ERROR", start, end);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
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
     * 统计指定用户有权限的应用的基础指标（优先基线表 base_info_record 以获得 user/device/session 更准确计数）。
     */
    public Map<String, Object> aggregateDailyBaseForUser(LocalDate date, String userId, String username) {
        Date start = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
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

        java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> apps = new HashSet<>();
        Set<String> users = new HashSet<>();
        Set<String> devices = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (m == null) continue;
            String appCode = getString(m, "appCode", "APP_CODE");
            if (appCode == null || appCode.isEmpty() || !userAppCodes.contains(appCode)) continue;
            apps.add(appCode);
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) users.add(uid);
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) devices.add(deviceId);
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) sessions.add(sessionId);
        }
        int pv = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("PV", start, end, userAppCodes);
        int click = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("CLICK", start, end, userAppCodes);
        int error = (int) tracingEventRepository.countByEventTypeAndCreatedAtBetweenAndAppCodes("ERROR", start, end, userAppCodes);
        
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
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
     * 按日统计指定事件类型的总量（限定用户权限）。
     */
    public List<Map<String, Object>> aggregateDailyCountByEventTypeForUser(LocalDate startDate, LocalDate endDate, String eventType, String userId, String username) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        List<Object[]> rows;
        if (userAppCodes.isEmpty()) {
            rows = new ArrayList<>();
        } else {
            rows = tracingEventRepository.countDailyByEventTypeAndAppCodes(eventType, rangeStart, rangeEnd, userAppCodes);
        }
        Map<String, Integer> dayCount = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            int cnt = ((Number) r[1]).intValue();
            dayCount.put(day, cnt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DATETIME", day);
            row.put("COUNT", dayCount.getOrDefault(day, 0));
            out.add(row);
        }
        return out;
    }

    /**
     * 按日按应用统计指定事件类型的总量（限定用户权限）。
     */
    public List<Map<String, Object>> aggregateDailyCountByEventTypeByAppForUser(LocalDate startDate, LocalDate endDate, String eventType, String userId, String username) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        List<Object[]> rows;
        if (userAppCodes.isEmpty()) {
            rows = new ArrayList<>();
        } else {
            rows = tracingEventRepository.countDailyByEventTypeByAppAndAppCodes(eventType, rangeStart, rangeEnd, userAppCodes);
        }
        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty() && userAppCodes.contains(ai.getAppCode())) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }
        Set<String> allCodes = new HashSet<>(userAppCodes);
        Map<String, Map<String, Integer>> countMap = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            String code = String.valueOf(r[1]);
            int cnt = ((Number) r[2]).intValue();
            allCodes.add(code);
            countMap.computeIfAbsent(day, k -> new HashMap<>()).put(code, cnt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Integer> byCode = countMap.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("COUNT", byCode.getOrDefault(code, 0));
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 按日统计指定事件类型的总量（全量）。
     */
    public List<Map<String, Object>> aggregateDailyCountByEventType(LocalDate startDate, LocalDate endDate, String eventType) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<Object[]> rows = tracingEventRepository.countDailyByEventType(eventType, rangeStart, rangeEnd);
        Map<String, Integer> dayCount = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            int cnt = ((Number) r[1]).intValue();
            dayCount.put(day, cnt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DATETIME", day);
            row.put("COUNT", dayCount.getOrDefault(day, 0));
            out.add(row);
        }
        return out;
    }

    /**
     * 按日按应用统计指定事件类型的总量（全量）。
     */
    public List<Map<String, Object>> aggregateDailyCountByEventTypeByApp(LocalDate startDate, LocalDate endDate, String eventType) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<Object[]> rows = tracingEventRepository.countDailyByEventTypeByApp(eventType, rangeStart, rangeEnd);
        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty()) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }
        Set<String> allCodes = new HashSet<>();
        Map<String, Map<String, Integer>> countMap = new HashMap<>();
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            String code = String.valueOf(r[1]);
            int cnt = ((Number) r[2]).intValue();
            allCodes.add(code);
            countMap.computeIfAbsent(day, k -> new HashMap<>()).put(code, cnt);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Integer> byCode = countMap.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("COUNT", byCode.getOrDefault(code, 0));
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 最近N条事件（限定用户权限）。
     */
    public List<Map<String, Object>> listRecentEvents(int limit, String userId, String username) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        int l = limit < 1 ? 10 : Math.min(limit, 50);
        List<Object[]> rows;
        if (userAppCodes.isEmpty()) {
            rows = new ArrayList<>();
        } else {
            rows = tracingEventRepository.findRecentByAppCodes(userAppCodes, PageRequest.of(0, l));
        }
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", r[0]);
            m.put("EVENT_TYPE", r[1]);
            m.put("APP_CODE", r[2]);
            m.put("SESSION_ID", r[3]);
            m.put("CREATED_AT", r[4]);
            out.add(m);
        }
        return out;
    }

    /**
     * 最近N条事件（全量）。
     */
    public List<Map<String, Object>> listRecentEvents(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        int l = limit < 1 ? 10 : Math.min(limit, 50);
        List<Object[]> rows = tracingEventRepository.findRecent(PageRequest.of(0, l));
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", r[0]);
            m.put("EVENT_TYPE", r[1]);
            m.put("APP_CODE", r[2]);
            m.put("SESSION_ID", r[3]);
            m.put("CREATED_AT", r[4]);
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listRecentEventsByApp(String appCode, int limit, String userId, String username) {
        if (appCode == null || appCode.trim().isEmpty())
            return Collections.emptyList();
        String code = appCode.trim();
        boolean superAdmin = (userId == null && username == null);
        if (!superAdmin) {
            Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
            if (userAppCodes.isEmpty() || !userAppCodes.contains(code)) {
                throw new IllegalArgumentException("forbidden");
            }
        }
        int l = limit < 1 ? 10 : Math.min(limit, 50);
        List<Object[]> rows = tracingEventRepository.findRecentByAppCodeWithPayload(code, PageRequest.of(0, l));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", r[0]);
            m.put("EVENT_TYPE", r[1]);
            m.put("APP_CODE", r[2]);
            m.put("SESSION_ID", r[3]);
            m.put("CREATED_AT", r[4]);
            m.put("PAYLOAD", r[5]);
            out.add(m);
        }
        return out;
    }

    /**
     * 最近N条 ERROR 事件（全量）。
     */
    public List<Map<String, Object>> listRecentErrors(int limit) {
        int l = limit < 1 ? 10 : Math.min(limit, 200);
        List<Object[]> rows = tracingEventRepository.findRecentErrorsLite(PageRequest.of(0, l));
        return mapErrorLiteRows(rows);
    }

    /**
     * 最近N条 ERROR 事件（限定用户权限）。
     */
    public List<Map<String, Object>> listRecentErrors(int limit, String userId, String username) {
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        if (userAppCodes.isEmpty()) return Collections.emptyList();
        int l = limit < 1 ? 10 : Math.min(limit, 200);
        List<Object[]> rows = tracingEventRepository.findRecentErrorsLiteByAppCodes(userAppCodes, PageRequest.of(0, l));
        return mapErrorLiteRows(rows);
    }

    /**
     * 最近N条 ERROR 事件（指定单个应用）。
     */
    public List<Map<String, Object>> listRecentErrorsByApp(String appCode, int limit) {
        if (appCode == null || appCode.trim().isEmpty()) return Collections.emptyList();
        int l = limit < 1 ? 10 : Math.min(limit, 200);
        List<Object[]> rows = tracingEventRepository.findRecentErrorsLiteByAppCode(appCode.trim(), PageRequest.of(0, l));
        return mapErrorLiteRows(rows);
    }

    public Map<String, Object> pageRecentErrorsByApp(String appCode, int pageNo, int pageSize) {
        if (appCode == null || appCode.trim().isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("list", Collections.emptyList());
            out.put("total", 0);
            out.put("pageNo", 1);
            out.put("pageSize", 20);
            return out;
        }
        String code = appCode.trim();
        int p = pageNo < 1 ? 1 : pageNo;
        int s = pageSize < 1 ? 20 : Math.min(pageSize, 200);
        long total = tracingEventRepository.countErrorsByAppCode(code);
        long offsetLong = (long) (p - 1) * (long) s;
        List<Map<String, Object>> list;
        if (offsetLong > Integer.MAX_VALUE) {
            list = Collections.emptyList();
        } else {
            List<Object[]> rows = tracingEventRepository.findErrorPageLiteByAppCode(code, PageRequest.of(p - 1, s));
            list = mapErrorLiteRows(rows);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", total);
        out.put("pageNo", p);
        out.put("pageSize", s);
        return out;
    }

    public Map<String, Object> pageRecentErrorsByAppWithFilters(String appCode, int pageNo, int pageSize,
            String errorCode, String severity, String requestUri) {
        if (appCode == null || appCode.trim().isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("list", Collections.emptyList());
            out.put("total", 0);
            out.put("pageNo", 1);
            out.put("pageSize", 20);
            return out;
        }
        String code = appCode.trim();
        int p = pageNo < 1 ? 1 : pageNo;
        int s = pageSize < 1 ? 20 : Math.min(pageSize, 200);
        String ec = (errorCode == null || errorCode.trim().isEmpty()) ? null : errorCode.trim();
        String sev = (severity == null || severity.trim().isEmpty()) ? null : severity.trim();
        String uri = (requestUri == null || requestUri.trim().isEmpty()) ? null : requestUri.trim();

        long total = tracingEventRepository.countErrorsByAppCodeWithFilters(code, ec, sev, uri);
        long offsetLong = (long) (p - 1) * (long) s;
        List<Map<String, Object>> list;
        if (offsetLong > Integer.MAX_VALUE) {
            list = Collections.emptyList();
        } else {
            List<Object[]> rows = tracingEventRepository.findErrorPageLiteByAppCodeWithFilters(code, ec, sev, uri,
                    PageRequest.of(p - 1, s));
            list = mapErrorLiteRows(rows);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", total);
        out.put("pageNo", p);
        out.put("pageSize", s);
        return out;
    }

    public String getErrorPayload(long id) {
        return tracingEventRepository.findErrorPayloadById(id);
    }

    public String getErrorPayloadByApp(String appCode, long id) {
        if (appCode == null || appCode.trim().isEmpty())
            return null;
        return tracingEventRepository.findErrorPayloadByIdAndAppCode(id, appCode.trim());
    }

    private List<Map<String, Object>> mapErrorLiteRows(List<Object[]> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", r[0]);
            m.put("APP_CODE", r[1]);
            m.put("APP_NAME", r[2]);
            m.put("SESSION_ID", r[3]);
            m.put("CREATED_AT", r[4]);
            m.put("ERROR_CODE", r[5]);
            m.put("MESSAGE", r[6]);
            m.put("SEVERITY", r[7]);
            m.put("REQUEST_URI", r[8]);
            out.add(m);
        }
        return out;
    }

    /**
     * 将查询结果映射为错误列表。
     */
    private List<Map<String, Object>> mapErrorRows(List<Object[]> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            String payloadStr = r[5] == null ? null : String.valueOf(r[5]);
            Map<String, Object> payload = payloadStr == null
                    ? null
                    : fromJson(payloadStr, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ID", r[0]);
            m.put("EVENT_TYPE", r[1]);
            m.put("APP_CODE", r[2]);
            m.put("APP_NAME", r[3]);
            m.put("SESSION_ID", r[4]);
            m.put("CREATED_AT", r[6]);
            m.put("ERROR_CODE", getString(payload, "errorCode", "ERROR_CODE", "code", "CODE"));
            m.put("MESSAGE", getString(payload, "message", "MESSAGE", "msg", "MSG", "errorMsg", "ERROR_MSG"));
            m.put("SEVERITY", getString(payload, "severity", "SEVERITY", "level", "LEVEL"));
            m.put("REQUEST_URI", getString(payload, "requestUri", "REQUEST_URI", "url", "URL"));
            m.put("PAYLOAD", payloadStr);
            out.add(m);
        }
        return out;
    }

    /**
     * 状态看板（今日）：基础指标 + 数据延迟分钟 + 状态标记。
     */
    public Map<String, Object> statusBoard(LocalDate today, String userId, String username, boolean superAdmin) {
        Map<String, Object> base;
        if (superAdmin) {
            base = aggregateDailyBase(today);
        } else {
            base = aggregateDailyBaseForUser(today, userId, username);
        }
        // 数据延迟：最后一条事件时间到现在的分钟差
        Date latest;
        if (superAdmin) {
            latest = tracingEventRepository.findMaxCreatedAt();
        } else {
            Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
            latest = userAppCodes.isEmpty() ? null : tracingEventRepository.findMaxCreatedAtByAppCodes(userAppCodes);
        }
        long delayMinutes = 9999;
        if (latest != null) {
            delayMinutes = Duration.between(latest.toInstant(), new Date().toInstant()).toMinutes();
            if (delayMinutes < 0) delayMinutes = 0;
        }
        String statusFlag;
        if (latest == null) {
            statusFlag = "NO_DATA";
        } else if (delayMinutes <= 15) {
            statusFlag = "OK";
        } else {
            statusFlag = "LAG";
        }
        base.put("DELAY_MINUTES", delayMinutes);
        base.put("STATUS", statusFlag);
        return base;
    }

    /**
     * 模拟埋点验证：写入一条PV事件。
     */
    public Map<String, Object> simulateVerifyEvent(String appCode, String userId, String username) {
        // superAdmin 判定：传入 userId/username 均为空时视为超级管理员调用
        boolean superAdmin = (userId == null && username == null);
        Set<String> userAppCodes = superAdmin ? Collections.emptySet() : getUserAccessibleAppCodes(userId, username);
        if (appCode == null || appCode.trim().isEmpty()) {
            throw new IllegalArgumentException("appCode required");
        }
        String trimmed = appCode.trim();
        if (!superAdmin && (userAppCodes.isEmpty() || !userAppCodes.contains(trimmed))) {
            throw new IllegalArgumentException("forbidden");
        }
        TracingEvent e = new TracingEvent();
        e.setEventType("PV");
        e.setAppCode(trimmed);
        e.setAppName(trimmed);
        e.setSessionId("verify-session");
        // 简单payload
        e.setPayload("{\"requestUri\":\"/verify/ping\",\"sdkUserUuid\":\"verify-user\"}");
        tracingEventRepository.save(e);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("APP_CODE", trimmed);
        out.put("EVENT_TYPE", "PV");
        out.put("REQUEST_URI", "/verify/ping");
        out.put("CREATED_AT", e.getCreatedAt());
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
        java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
        Set<String> users = new HashSet<>();
        Set<String> devices = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        for (BaseInfoRecord r : records) {
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (m == null) continue;
            String code = getString(m, "appCode", "APP_CODE");
            if (code == null || code.isEmpty() || !code.equals(appCode)) continue;
            String uid = getString(m, "sdkUserUuid");
            if (uid != null && !uid.isEmpty()) users.add(uid);
            String deviceId = getString(m, "deviceId", "DEVICE_ID");
            if (deviceId != null && !deviceId.isEmpty()) devices.add(deviceId);
            String sessionId = getString(m, "sessionId", "SESSION_ID");
            if (sessionId != null && !sessionId.isEmpty()) sessions.add(sessionId);
        }
        int pv = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("PV", appCode, start, end);
        int click = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("CLICK", appCode, start, end);
        int error = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("ERROR", appCode, start, end);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("DAY_TIME", DF.format(date));
        item.put("USER_COUNT", users.size());
        item.put("DEVICE_NUM", devices.size());
        item.put("SESSION_UNM", sessions.size());
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

    /**
     * 按日统计 UV（全量，基于基线表去重 sdkUserUuid）。
     */
    public List<Map<String, Object>> aggregateDailyUV(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
            Set<String> users = new HashSet<>();
            for (BaseInfoRecord r : records) {
                Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
                if (m == null) continue;
                String uid = getString(m, "sdkUserUuid", "SDK_USER_UUID");
                if (uid != null && !uid.isEmpty()) users.add(uid);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DATETIME", DF.format(d));
            row.put("COUNT", users.size());
            out.add(row);
        }
        return out;
    }

    /**
     * 按日统计 UV（限定用户权限，基于基线表）。
     */
    public List<Map<String, Object>> aggregateDailyUVForUser(LocalDate startDate, LocalDate endDate, String userId, String username) {
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (userAppCodes.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("DATETIME", DF.format(d));
                row.put("COUNT", 0);
                out.add(row);
                continue;
            }
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
            Set<String> users = new HashSet<>();
            for (BaseInfoRecord r : records) {
                Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
                if (m == null) continue;
                String appCode = getString(m, "appCode", "APP_CODE");
                if (appCode == null || !userAppCodes.contains(appCode)) continue;
                String uid = getString(m, "sdkUserUuid", "SDK_USER_UUID");
                if (uid != null && !uid.isEmpty()) users.add(uid);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DATETIME", DF.format(d));
            row.put("COUNT", users.size());
            out.add(row);
        }
        return out;
    }

    /**
     * 按日按应用统计 UV（全量，基于基线表去重 sdkUserUuid）。
     */
    public List<Map<String, Object>> aggregateDailyUVByApp(LocalDate startDate, LocalDate endDate) {
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(rangeStart, rangeEnd);

        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty()) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }

        Set<String> allCodes = new HashSet<>();
        Map<String, Map<String, Set<String>>> dayUserByCode = new HashMap<>();
        for (BaseInfoRecord r : records) {
            String day = DF.format(r.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (m == null) continue;
            String code = getString(m, "appCode", "APP_CODE");
            String uid = getString(m, "sdkUserUuid", "SDK_USER_UUID");
            if (code == null || code.isEmpty() || uid == null || uid.isEmpty()) continue;
            allCodes.add(code);
            dayUserByCode
                    .computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(code, k -> new HashSet<>())
                    .add(uid);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Set<String>> byCode = dayUserByCode.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("COUNT", byCode.getOrDefault(code, Collections.emptySet()).size());
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 按日按应用统计 UV（限定用户权限，基于基线表去重 sdkUserUuid）。
     */
    public List<Map<String, Object>> aggregateDailyUVByAppForUser(LocalDate startDate, LocalDate endDate, String userId, String username) {
        Set<String> userAppCodes = getUserAccessibleAppCodes(userId, username);
        if (userAppCodes.isEmpty()) {
            return new ArrayList<>();
        }
        Date rangeStart = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date rangeEnd = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(rangeStart, rangeEnd);

        Map<String, String> nameByCode = new HashMap<>();
        for (com.krielwus.webtracinganalysis.entity.ApplicationInfo ai : applicationInfoRepository.findAll()) {
            if (ai.getAppCode() != null && !ai.getAppCode().isEmpty() && userAppCodes.contains(ai.getAppCode())) {
                nameByCode.put(ai.getAppCode(), ai.getAppName() == null ? ai.getAppCode() : ai.getAppName());
            }
        }

        Set<String> allCodes = new HashSet<>(userAppCodes);
        Map<String, Map<String, Set<String>>> dayUserByCode = new HashMap<>();
        for (BaseInfoRecord r : records) {
            String day = DF.format(r.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
            if (m == null) continue;
            String code = getString(m, "appCode", "APP_CODE");
            if (code == null || code.isEmpty() || !userAppCodes.contains(code)) continue;
            String uid = getString(m, "sdkUserUuid", "SDK_USER_UUID");
            if (uid == null || uid.isEmpty()) continue;
            dayUserByCode
                    .computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(code, k -> new HashSet<>())
                    .add(uid);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String day = DF.format(d);
            Map<String, Set<String>> byCode = dayUserByCode.getOrDefault(day, Collections.emptyMap());
            for (String code : allCodes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("APP_CODE", code);
                row.put("APP_NAME", nameByCode.getOrDefault(code, code));
                row.put("DATETIME", day);
                row.put("COUNT", byCode.getOrDefault(code, Collections.emptySet()).size());
                out.add(row);
            }
        }
        return out;
    }

    /**
     * 按日统计应用 UV（基于基线表）。
     */
    public List<Map<String, Object>> aggregateDailyUVForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (appCode == null || appCode.trim().isEmpty()) return out;
        String trimmed = appCode.trim();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            java.util.List<BaseInfoRecord> records = baseInfoRecordRepository.findByCreatedAtBetween(start, end);
            Set<String> users = new HashSet<>();
            for (BaseInfoRecord r : records) {
                Map<String, Object> m = fromJson(r.getPayload(), new TypeReference<Map<String, Object>>() {});
                if (m == null) continue;
                String code = getString(m, "appCode", "APP_CODE");
                if (code == null || !trimmed.equals(code)) continue;
                String uid = getString(m, "sdkUserUuid", "SDK_USER_UUID");
                if (uid != null && !uid.isEmpty()) users.add(uid);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("APP_CODE", trimmed);
            row.put("DATETIME", DF.format(d));
            row.put("COUNT", users.size());
            out.add(row);
        }
        return out;
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

    public List<Map<String, Object>> aggregateDailyErrorForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Date start = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date end = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            int cnt = (int) tracingEventRepository.countByEventTypeAndAppCodeAndCreatedAtBetween("ERROR", appCode,
                    start, end);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("APP_CODE", appCode);
            row.put("DATETIME", DF.format(d));
            row.put("ERROR_NUM", cnt);
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> aggregatePagePVForApp(LocalDate startDate, LocalDate endDate, String appCode) {
        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        java.util.List<Object[]> rows = pageViewRouteRepository.countRoutePvForAppBetween(appCode, start, end);
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("PAGE_URL", String.valueOf(r[0]));
            row.put("PV_NUM", ((Number) r[1]).intValue());
            row.put("SESSION_NUM", ((Number) r[2]).intValue());
            row.put("USER_NUM", ((Number) r[3]).intValue());
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> pageRouteVisits(String appCode, String routePath, LocalDate startDate, LocalDate endDate,
            int pageNo, int pageSize) {
        if (appCode == null || appCode.trim().isEmpty() || routePath == null || routePath.trim().isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("list", Collections.emptyList());
            out.put("total", 0);
            out.put("pageNo", 1);
            out.put("pageSize", 20);
            return out;
        }
        int p = pageNo < 1 ? 1 : pageNo;
        int s = pageSize < 1 ? 20 : Math.min(pageSize, 200);
        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        org.springframework.data.domain.Page<com.krielwus.webtracinganalysis.entity.PageViewRoute> page = pageViewRouteRepository
                .findByAppCodeAndRoutePathAndCreatedAtBetweenOrderByCreatedAtDesc(appCode.trim(), routePath.trim(),
                        start, end,
                        PageRequest.of(p - 1, s));
        List<Map<String, Object>> list = new ArrayList<>();
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : page.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("CREATED_AT", r.getCreatedAt());
            m.put("SESSION_ID", r.getSessionId());
            m.put("SDK_USER_UUID", r.getSdkUserUuid());
            m.put("DEVICE_ID", r.getDeviceId());
            m.put("ROUTE_PATH", r.getRoutePath());
            m.put("ROUTE_TYPE", r.getRouteType());
            m.put("ROUTE_PARAMS", r.getRouteParams());
            m.put("FULL_URL", r.getFullUrl());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("total", page.getTotalElements());
        out.put("pageNo", p);
        out.put("pageSize", s);
        return out;
    }

    public List<Map<String, Object>> listSessionPaths(String appCode, LocalDate startDate, LocalDate endDate,
            int limitSessions) {
        return listSessionPaths(appCode, startDate, endDate, limitSessions,
                sessionPathProperties.isCollapseConsecutiveDuplicates(),
                sessionPathProperties.getMinStayMs(),
                sessionPathProperties.getIgnoreRoutePatterns(),
                sessionPathProperties.getMaxDepth());
    }

    public List<Map<String, Object>> listSessionPaths(String appCode, LocalDate startDate, LocalDate endDate,
            int limitSessions,
            Boolean collapseConsecutiveDuplicates,
            Long minStayMs,
            List<String> ignoreRoutePatterns,
            Integer maxDepth) {
        if (appCode == null || appCode.trim().isEmpty())
            return Collections.emptyList();
        boolean collapse = collapseConsecutiveDuplicates == null
                ? sessionPathProperties.isCollapseConsecutiveDuplicates()
                : collapseConsecutiveDuplicates;
        long minStay = minStayMs == null ? sessionPathProperties.getMinStayMs() : Math.max(0, minStayMs);
        int depth = maxDepth == null ? sessionPathProperties.getMaxDepth() : Math.max(1, maxDepth);
        int limit = limitSessions < 1 ? sessionPathProperties.getDefaultLimitSessions() : Math.min(limitSessions, 2000);
        List<String> ignore = (ignoreRoutePatterns == null) ? sessionPathProperties.getIgnoreRoutePatterns()
                : ignoreRoutePatterns;

        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<String> sessionIds = pageViewRouteRepository.findRecentSessionIdsBetween(appCode.trim(), start, end,
                PageRequest.of(0, limit));
        if (sessionIds.isEmpty())
            return Collections.emptyList();
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> rows = pageViewRouteRepository
                .findByAppCodeAndSessionIdsBetweenOrdered(appCode.trim(), sessionIds, start, end);
        Map<String, List<com.krielwus.webtracinganalysis.entity.PageViewRoute>> bySession = new LinkedHashMap<>();
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : rows) {
            if (r.getSessionId() == null || r.getSessionId().isEmpty())
                continue;
            bySession.computeIfAbsent(r.getSessionId(), k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String sid : sessionIds) {
            List<com.krielwus.webtracinganalysis.entity.PageViewRoute> list = bySession.get(sid);
            if (list == null || list.isEmpty())
                continue;
            List<SessionStep> steps = buildSessionSteps(list, collapse, minStay, ignore, depth);
            if (steps.isEmpty())
                continue;
            com.krielwus.webtracinganalysis.entity.PageViewRoute first = list.get(0);
            com.krielwus.webtracinganalysis.entity.PageViewRoute lastRow = list.get(list.size() - 1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("SESSION_ID", sid);
            m.put("SDK_USER_UUID", first.getSdkUserUuid());
            m.put("DEVICE_ID", first.getDeviceId());
            m.put("FIRST_TIME", first.getCreatedAt());
            m.put("LAST_TIME", lastRow.getCreatedAt());
            m.put("STEP_COUNT", steps.size());
            m.put("PATH", String.join(" -> ",
                    steps.stream().map(s -> s.routePath).collect(java.util.stream.Collectors.toList())));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> getSessionPathDetail(String appCode, String sessionId, LocalDate startDate,
            LocalDate endDate) {
        return getSessionPathDetail(appCode, sessionId, startDate, endDate,
                sessionPathProperties.isCollapseConsecutiveDuplicates(),
                sessionPathProperties.getMinStayMs(),
                sessionPathProperties.getIgnoreRoutePatterns(),
                sessionPathProperties.getMaxDepth());
    }

    public List<Map<String, Object>> getSessionPathDetail(String appCode, String sessionId, LocalDate startDate,
            LocalDate endDate,
            Boolean collapseConsecutiveDuplicates,
            Long minStayMs,
            List<String> ignoreRoutePatterns,
            Integer maxDepth) {
        if (appCode == null || appCode.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        boolean collapse = collapseConsecutiveDuplicates == null
                ? sessionPathProperties.isCollapseConsecutiveDuplicates()
                : collapseConsecutiveDuplicates;
        long minStay = minStayMs == null ? sessionPathProperties.getMinStayMs() : Math.max(0, minStayMs);
        int depth = maxDepth == null ? sessionPathProperties.getMaxDepth() : Math.max(1, maxDepth);
        List<String> ignore = (ignoreRoutePatterns == null) ? sessionPathProperties.getIgnoreRoutePatterns()
                : ignoreRoutePatterns;

        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> rows = pageViewRouteRepository
                .findByAppCodeAndSessionIdAndCreatedAtBetweenOrderByCreatedAtAsc(appCode.trim(), sessionId.trim(),
                        start, end);
        if (rows.isEmpty())
            return Collections.emptyList();
        List<SessionStep> steps = buildSessionSteps(rows, collapse, minStay, ignore, depth);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SessionStep s : steps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("CREATED_AT", s.createdAt);
            m.put("ROUTE_PATH", s.routePath);
            m.put("ROUTE_TYPE", s.routeType);
            m.put("ROUTE_PARAMS", s.routeParams);
            m.put("FULL_URL", s.fullUrl);
            m.put("DURATION_MS", s.durationMs);
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> aggregateSessionPathPatterns(String appCode, LocalDate startDate, LocalDate endDate,
            int limitSessions,
            int topN,
            Boolean collapseConsecutiveDuplicates,
            Long minStayMs,
            List<String> ignoreRoutePatterns,
            Integer maxDepth) {
        return aggregateSessionPathPatterns(appCode, startDate, endDate, limitSessions, topN,
                collapseConsecutiveDuplicates,
                minStayMs, ignoreRoutePatterns, maxDepth, null, null, null, null);
    }

    public Map<String, Object> aggregateSessionPathPatterns(String appCode, LocalDate startDate, LocalDate endDate,
            int limitSessions,
            int topN,
            Boolean collapseConsecutiveDuplicates,
            Long minStayMs,
            List<String> ignoreRoutePatterns,
            Integer maxDepth,
            String startRoutePath,
            String groupBy,
            String groupParamName,
            Integer maxGroups) {
        if (appCode == null || appCode.trim().isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("topPaths", Collections.emptyList());
            out.put("funnel", Collections.emptyList());
            out.put("sessionCount", 0);
            return out;
        }
        boolean collapse = collapseConsecutiveDuplicates == null
                ? sessionPathProperties.isCollapseConsecutiveDuplicates()
                : collapseConsecutiveDuplicates;
        long minStay = minStayMs == null ? sessionPathProperties.getMinStayMs() : Math.max(0, minStayMs);
        int depth = maxDepth == null ? sessionPathProperties.getMaxDepth() : Math.max(1, maxDepth);
        int limit = limitSessions < 1 ? sessionPathProperties.getDefaultLimitSessions() : Math.min(limitSessions, 5000);
        int n = topN < 1 ? 20 : Math.min(topN, 200);
        int groupsLimit = (maxGroups == null || maxGroups < 1) ? 20 : Math.min(maxGroups, 200);
        List<String> ignore = (ignoreRoutePatterns == null) ? sessionPathProperties.getIgnoreRoutePatterns()
                : ignoreRoutePatterns;

        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<String> sessionIds = pageViewRouteRepository.findRecentSessionIdsBetween(appCode.trim(), start, end,
                PageRequest.of(0, limit));
        if (sessionIds.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("topPaths", Collections.emptyList());
            out.put("funnel", Collections.emptyList());
            out.put("sessionCount", 0);
            return out;
        }
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> rows = pageViewRouteRepository
                .findByAppCodeAndSessionIdsBetweenOrdered(appCode.trim(), sessionIds, start, end);
        Map<String, List<com.krielwus.webtracinganalysis.entity.PageViewRoute>> bySession = new LinkedHashMap<>();
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : rows) {
            if (r.getSessionId() == null || r.getSessionId().isEmpty())
                continue;
            bySession.computeIfAbsent(r.getSessionId(), k -> new ArrayList<>()).add(r);
        }

        String startRoute = (startRoutePath == null || startRoutePath.trim().isEmpty()) ? null : startRoutePath.trim();
        String groupMode = (groupBy == null) ? "NONE" : groupBy.trim().toUpperCase(Locale.ROOT);
        if (!"USER".equals(groupMode) && !"PARAM".equals(groupMode))
            groupMode = "NONE";
        String groupParam = (groupParamName == null || groupParamName.trim().isEmpty()) ? null : groupParamName.trim();

        Map<String, GroupAgg> groupAgg = new HashMap<>();
        long sessionsUsedTotal = 0;
        for (String sid : sessionIds) {
            List<com.krielwus.webtracinganalysis.entity.PageViewRoute> list = bySession.get(sid);
            if (list == null || list.isEmpty())
                continue;
            List<SessionStep> rawSteps = buildSessionSteps(list, collapse, minStay, ignore, 1000);
            if (rawSteps.isEmpty())
                continue;
            List<SessionStep> steps = applyStartRouteAndDepth(rawSteps, startRoute, depth);
            if (steps.isEmpty())
                continue;
            sessionsUsedTotal++;
            String gk = "ALL";
            if ("USER".equals(groupMode)) {
                String user = list.get(0).getSdkUserUuid();
                gk = (user == null || user.isEmpty()) ? "UNKNOWN" : user;
            } else if ("PARAM".equals(groupMode)) {
                if (groupParam == null || groupParam.isEmpty()) {
                    gk = "UNKNOWN";
                } else {
                    String v = extractRouteParamValue(steps.get(0).routeParams, groupParam);
                    gk = (v == null || v.isEmpty()) ? "UNKNOWN" : v;
                }
            }

            GroupAgg agg = groupAgg.computeIfAbsent(gk, k -> new GroupAgg());
            agg.sessionsUsed++;
            List<String> routeSeq = steps.stream().map(s -> s.routePath).collect(java.util.stream.Collectors.toList());
            String key = String.join(" -> ", routeSeq);
            agg.pathCount.put(key, agg.pathCount.getOrDefault(key, 0L) + 1L);
            agg.sampleSession.putIfAbsent(key, sid);
            for (int i = 0; i < routeSeq.size(); i++) {
                String route = routeSeq.get(i);
                agg.funnel.computeIfAbsent(i + 1, k -> new HashMap<>()).put(route,
                        agg.funnel.getOrDefault(i + 1, Collections.emptyMap()).getOrDefault(route, 0L) + 1L);
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        List<Map.Entry<String, GroupAgg>> gEntries = new ArrayList<>(groupAgg.entrySet());
        gEntries.sort((a, b) -> Long.compare(b.getValue().sessionsUsed, a.getValue().sessionsUsed));
        int takeGroups = Math.min(groupsLimit, gEntries.size());
        for (int gi = 0; gi < takeGroups; gi++) {
            Map.Entry<String, GroupAgg> ge = gEntries.get(gi);
            String gk = ge.getKey();
            GroupAgg agg = ge.getValue();

            List<Map<String, Object>> topPaths = new ArrayList<>();
            List<Map.Entry<String, Long>> pathEntries = new ArrayList<>(agg.pathCount.entrySet());
            pathEntries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(n, pathEntries.size()); i++) {
                Map.Entry<String, Long> en = pathEntries.get(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("PATH", en.getKey());
                m.put("COUNT", en.getValue());
                m.put("PCT", agg.sessionsUsed <= 0 ? 0 : (double) en.getValue() * 100.0 / (double) agg.sessionsUsed);
                m.put("SAMPLE_SESSION_ID", agg.sampleSession.get(en.getKey()));
                topPaths.add(m);
            }

            List<Map<String, Object>> funnelOut = new ArrayList<>();
            List<Integer> stepsIdx = new ArrayList<>(agg.funnel.keySet());
            Collections.sort(stepsIdx);
            for (Integer stepIndex : stepsIdx) {
                Map<String, Long> mp = agg.funnel.get(stepIndex);
                if (mp == null)
                    continue;
                List<Map.Entry<String, Long>> ents = new ArrayList<>(mp.entrySet());
                ents.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                int take = Math.min(10, ents.size());
                for (int i = 0; i < take; i++) {
                    Map.Entry<String, Long> en = ents.get(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("STEP", stepIndex);
                    m.put("ROUTE_PATH", en.getKey());
                    m.put("COUNT", en.getValue());
                    funnelOut.add(m);
                }
            }

            Map<String, Object> groupObj = new LinkedHashMap<>();
            groupObj.put("GROUP_KEY", gk);
            groupObj.put("SESSION_COUNT", agg.sessionsUsed);
            groupObj.put("topPaths", topPaths);
            groupObj.put("funnel", funnelOut);
            groups.add(groupObj);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupBy", groupMode);
        out.put("groupParamName", groupParam);
        out.put("startRoutePath", startRoute);
        out.put("groups", groups);
        out.put("sessionCount", sessionsUsedTotal);
        if ("NONE".equals(groupMode)) {
            if (!groups.isEmpty()) {
                Map<String, Object> first = groups.get(0);
                out.put("topPaths", first.get("topPaths"));
                out.put("funnel", first.get("funnel"));
            } else {
                out.put("topPaths", Collections.emptyList());
                out.put("funnel", Collections.emptyList());
            }
        }
        return out;
    }

    private static class GroupAgg {
        private long sessionsUsed = 0;
        private final Map<String, Long> pathCount = new HashMap<>();
        private final Map<String, String> sampleSession = new HashMap<>();
        private final Map<Integer, Map<String, Long>> funnel = new HashMap<>();
    }

    private List<SessionStep> applyStartRouteAndDepth(List<SessionStep> steps, String startRoutePath, int maxDepth) {
        if (steps == null || steps.isEmpty())
            return Collections.emptyList();
        List<SessionStep> sliced = steps;
        if (startRoutePath != null && !startRoutePath.isEmpty()) {
            int idx = -1;
            for (int i = 0; i < steps.size(); i++) {
                if (startRoutePath.equals(steps.get(i).routePath)) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0)
                return Collections.emptyList();
            sliced = steps.subList(idx, steps.size());
        }
        int take = Math.min(maxDepth, sliced.size());
        List<SessionStep> cut = sliced.subList(0, take);
        List<SessionStep> out = new ArrayList<>();
        for (int i = 0; i < cut.size(); i++) {
            SessionStep s = cut.get(i);
            Long duration = null;
            if (i < cut.size() - 1) {
                long d = cut.get(i + 1).createdAt.getTime() - s.createdAt.getTime();
                duration = Math.max(0, d);
            }
            out.add(new SessionStep(s.createdAt, s.routePath, s.routeType, s.routeParams, s.fullUrl, duration));
        }
        return out;
    }

    private String extractRouteParamValue(String routeParamsJson, String key) {
        if (routeParamsJson == null || routeParamsJson.isEmpty() || key == null || key.isEmpty())
            return null;
        try {
            Map<String, Object> m = objectMapper.readValue(routeParamsJson, new TypeReference<Map<String, Object>>() {
            });
            Object v = m.get(key);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> aggregateSessionSankey(String appCode, LocalDate startDate, LocalDate endDate,
            int limitSessions,
            Boolean collapseConsecutiveDuplicates,
            Long minStayMs,
            List<String> ignoreRoutePatterns,
            Integer maxDepth,
            String startRoutePath) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", Collections.emptyList());
        out.put("links", Collections.emptyList());
        if (appCode == null || appCode.trim().isEmpty())
            return out;
        boolean collapse = collapseConsecutiveDuplicates == null
                ? sessionPathProperties.isCollapseConsecutiveDuplicates()
                : collapseConsecutiveDuplicates;
        long minStay = minStayMs == null ? sessionPathProperties.getMinStayMs() : Math.max(0, minStayMs);
        int depth = maxDepth == null ? sessionPathProperties.getMaxDepth() : Math.max(1, maxDepth);
        int limit = limitSessions < 1 ? sessionPathProperties.getDefaultLimitSessions() : Math.min(limitSessions, 5000);
        Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<String> sessionIds = pageViewRouteRepository.findRecentSessionIdsBetween(appCode.trim(), start, end,
                PageRequest.of(0, limit));
        if (sessionIds.isEmpty())
            return out;
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> rows = pageViewRouteRepository
                .findByAppCodeAndSessionIdsBetweenOrdered(appCode.trim(), sessionIds, start, end);
        Map<String, List<com.krielwus.webtracinganalysis.entity.PageViewRoute>> bySession = new LinkedHashMap<>();
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : rows) {
            if (r.getSessionId() == null || r.getSessionId().isEmpty())
                continue;
            bySession.computeIfAbsent(r.getSessionId(), k -> new ArrayList<>()).add(r);
        }
        Map<String, Integer> nodeIndex = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Long> linkCount = new HashMap<>();
        for (String sid : sessionIds) {
            List<com.krielwus.webtracinganalysis.entity.PageViewRoute> list = bySession.get(sid);
            if (list == null || list.isEmpty())
                continue;
            List<SessionStep> steps = buildSessionSteps(list, collapse, minStay, ignoreRoutePatterns, 1000);
            if (steps.isEmpty())
                continue;
            List<SessionStep> sliced = applyStartRouteAndDepth(steps, startRoutePath, depth);
            if (sliced.isEmpty())
                continue;
            for (int i = 0; i < sliced.size() - 1; i++) {
                String a = sliced.get(i).routePath;
                String b = sliced.get(i + 1).routePath;
                if (a == null || a.isEmpty() || b == null || b.isEmpty())
                    continue;
                String key = a + "||" + b;
                linkCount.put(key, linkCount.getOrDefault(key, 0L) + 1L);
            }
        }
        Set<String> allNodes = new LinkedHashSet<>();
        for (String k : linkCount.keySet()) {
            String[] ab = k.split("\\|\\|");
            allNodes.add(ab[0]);
            allNodes.add(ab[1]);
        }
        int idx = 0;
        for (String name : allNodes) {
            nodeIndex.put(name, idx++);
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("name", name);
            nodes.add(n);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Long> en : linkCount.entrySet()) {
            String[] ab = en.getKey().split("\\|\\|");
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("source", ab[0]);
            l.put("target", ab[1]);
            l.put("value", en.getValue());
            links.add(l);
        }
        out.put("nodes", nodes);
        out.put("links", links);
        return out;
    }

    private static class SessionStep {
        private final Date createdAt;
        private final String routePath;
        private final String routeType;
        private final String routeParams;
        private final String fullUrl;
        private final Long durationMs;

        private SessionStep(Date createdAt, String routePath, String routeType, String routeParams, String fullUrl,
                Long durationMs) {
            this.createdAt = createdAt;
            this.routePath = routePath;
            this.routeType = routeType;
            this.routeParams = routeParams;
            this.fullUrl = fullUrl;
            this.durationMs = durationMs;
        }
    }

    private List<SessionStep> buildSessionSteps(List<com.krielwus.webtracinganalysis.entity.PageViewRoute> rows,
            boolean collapseConsecutiveDuplicates,
            long minStayMs,
            List<String> ignoreRoutePatterns,
            int maxDepth) {
        List<java.util.regex.Pattern> patterns = new ArrayList<>();
        if (ignoreRoutePatterns != null) {
            for (String p : ignoreRoutePatterns) {
                if (p == null || p.trim().isEmpty())
                    continue;
                try {
                    patterns.add(java.util.regex.Pattern.compile(p.trim()));
                } catch (Exception ignore) {
                }
            }
        }
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> filtered = new ArrayList<>();
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : rows) {
            String path = r.getRoutePath();
            if (path == null || path.isEmpty())
                continue;
            boolean ignored = false;
            for (java.util.regex.Pattern pt : patterns) {
                if (pt.matcher(path).find()) {
                    ignored = true;
                    break;
                }
            }
            if (!ignored)
                filtered.add(r);
        }
        if (filtered.isEmpty())
            return Collections.emptyList();

        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> collapsed = new ArrayList<>();
        com.krielwus.webtracinganalysis.entity.PageViewRoute last = null;
        for (com.krielwus.webtracinganalysis.entity.PageViewRoute r : filtered) {
            if (!collapseConsecutiveDuplicates) {
                collapsed.add(r);
                continue;
            }
            if (last == null) {
                collapsed.add(r);
                last = r;
                continue;
            }
            String cur = r.getRoutePath();
            String prev = last.getRoutePath();
            if (cur != null && cur.equals(prev)) {
                collapsed.set(collapsed.size() - 1, r);
                last = r;
            } else {
                collapsed.add(r);
                last = r;
            }
        }

        if (collapsed.isEmpty())
            return Collections.emptyList();

        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> pruned = new ArrayList<>(collapsed);
        if (minStayMs > 0) {
            boolean changed;
            do {
                changed = false;
                if (pruned.size() <= 1)
                    break;
                List<Long> durations = new ArrayList<>();
                for (int i = 0; i < pruned.size(); i++) {
                    if (i == pruned.size() - 1) {
                        durations.add(null);
                    } else {
                        long d = pruned.get(i + 1).getCreatedAt().getTime() - pruned.get(i).getCreatedAt().getTime();
                        durations.add(Math.max(0, d));
                    }
                }
                List<com.krielwus.webtracinganalysis.entity.PageViewRoute> next = new ArrayList<>();
                for (int i = 0; i < pruned.size(); i++) {
                    Long d = durations.get(i);
                    if (d != null && d > 0 && d < minStayMs) {
                        changed = true;
                        continue;
                    }
                    next.add(pruned.get(i));
                }
                pruned = next;
            } while (changed);
        }

        int take = Math.min(maxDepth, pruned.size());
        List<com.krielwus.webtracinganalysis.entity.PageViewRoute> cut = pruned.subList(0, take);
        List<SessionStep> out = new ArrayList<>();
        for (int i = 0; i < cut.size(); i++) {
            com.krielwus.webtracinganalysis.entity.PageViewRoute r = cut.get(i);
            Long duration = null;
            if (i < cut.size() - 1) {
                long d = cut.get(i + 1).getCreatedAt().getTime() - r.getCreatedAt().getTime();
                duration = Math.max(0, d);
            }
            out.add(new SessionStep(r.getCreatedAt(), r.getRoutePath(), r.getRouteType(), r.getRouteParams(),
                    r.getFullUrl(), duration));
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

    private final com.krielwus.webtracinganalysis.repository.PageViewRouteRepository pageViewRouteRepository;

    private String[] parsePageRoute(String url) {
        String type = "history";
        String path = "/";
        String paramsJson = "{}";
        if (url == null || url.isEmpty())
            return new String[] { type, path, paramsJson };
        try {
            int idx = url.indexOf('#');
            if (idx >= 0) {
                type = "hash";
                String hash = url.substring(idx + 1);
                String route = hash;
                String query = null;
                int q = hash.indexOf('?');
                if (q >= 0) {
                    route = hash.substring(0, q);
                    query = hash.substring(q + 1);
                }
                path = route != null && route.length() > 0 ? (route.startsWith("/") ? route : "/" + route) : "/";
                paramsJson = buildParamsJson(query);
            } else {
                java.net.URI uri = java.net.URI.create(url);
                path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
                paramsJson = buildParamsJson(uri.getQuery());
            }
        } catch (Exception ignore) {
        }
        return new String[] { type, path, paramsJson };
    }

    private String buildParamsJson(String query) {
        if (query == null || query.isEmpty())
            return "{}";
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String p : pairs) {
            if (p == null || p.isEmpty())
                continue;
            int eq = p.indexOf('=');
            String k, v;
            if (eq >= 0) {
                k = p.substring(0, eq);
                v = p.substring(eq + 1);
            } else {
                k = p;
                v = "";
            }
            try {
                k = java.net.URLDecoder.decode(k, java.nio.charset.StandardCharsets.UTF_8.name());
                v = java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception ignore) {
            }
            if (!k.isEmpty())
                map.put(k, v);
        }
        return toJson(map);
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
