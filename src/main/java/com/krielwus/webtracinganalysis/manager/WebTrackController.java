package com.krielwus.webtracinganalysis.manager;

import com.krielwus.webtracinganalysis.info.ResultInfo;
import com.krielwus.webtracinganalysis.service.TracingService;
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

    public WebTrackController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * 当天基础指标：应用数、用户数、设备数、会话数、点击量、PV。
     */
    @GetMapping("/queryDailyBaseInfo")
    public ResultInfo queryDailyBaseInfo() {
        Map<String, Object> item = tracingService.aggregateDailyBase(LocalDate.now());
        List<Map<String, Object>> data = Collections.singletonList(item);
        return new ResultInfo(1000, "success", data);
    }

    /**
     * 历史累计指标：累计应用数、用户数、设备数、会话数、点击量、PV。
     */
    @GetMapping("/queryAllBaseInfo")
    public ResultInfo queryAllBaseInfo() {
        Map<String, Object> item = tracingService.aggregateAllBase();
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
    public ResultInfo queryDailyInfo(@RequestBody DateRangeDTO dto) {
        String start = dto.getStartDate();
        String end = dto.getEndDate();
        LocalDate startDate = LocalDate.parse(start, DF);
        LocalDate endDate = LocalDate.parse(end, DF);
        List<Map<String, Object>> list = tracingService.aggregateDailyPVByApp(startDate, endDate);
        return new ResultInfo(1000, "success", list);
    }
}
