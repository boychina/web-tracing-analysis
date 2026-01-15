import { InfoCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import {
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Drawer,
  Row,
  Segmented,
  Select,
  Switch,
  InputNumber,
  Skeleton,
  Space,
  Tooltip,
  Table,
  Typography,
  Modal,
  message,
} from "antd";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import client from "../api/client";
import EChart from "../components/EChart";
import MetricCard from "../components/MetricCard";
import RecentErrorsTable, {
  type RecentErrorItem,
} from "../components/RecentErrorsTable";

const { RangePicker } = DatePicker;

const APP_CODE_STORAGE_KEY = "web-tracing-ui.applicationMonitor.appCode";

type AppItem = {
  id: number;
  appName: string;
  appCode: string;
};

type DailyBase = {
  USER_COUNT: number;
  DEVICE_NUM: number;
  SESSION_UNM: number;
  CLICK_NUM: number;
  PV_NUM: number;
  ERROR_NUM: number;
};

type AllBase = {
  APPLICATION_NUM: number;
  USER_COUNT: number;
  DEVICE_NUM: number;
  SESSION_UNM: number;
  CLICK_NUM: number;
  PV_NUM: number;
  ERROR_NUM: number;
};

type DailyItem = {
  DATETIME: string;
  PV_NUM: number;
};

type UvItem = {
  DATETIME: string;
  COUNT: number;
};

type ErrorItem = RecentErrorItem;

type PagePvItem = {
  PAGE_URL: string;
  PV_NUM: number;
  SESSION_NUM?: number;
  USER_NUM?: number;
};

type ErrorTrendItem = {
  DATETIME: string;
  ERROR_NUM: number;
};

type DayjsValue = any;

type Preset = "7d" | "30d" | "90d" | "custom";

function ellipsis(value: string, maxLen: number) {
  if (value.length <= maxLen) return value;
  return `${value.slice(0, maxLen)}...`;
}

function buildDateStrings(start: DayjsValue, end: DayjsValue) {
  const dates: string[] = [];
  let cur = start.startOf("day");
  const last = end.startOf("day");
  while (!cur.isAfter(last)) {
    dates.push(cur.format("YYYY-MM-DD"));
    cur = cur.add(1, "day");
  }
  return dates;
}

function getPresetRange(preset: Preset): [DayjsValue, DayjsValue] {
  const today = dayjs();
  if (preset === "7d") return [today.subtract(6, "day"), today];
  if (preset === "30d") return [today.subtract(29, "day"), today];
  if (preset === "90d") return [today.subtract(89, "day"), today];
  return [today.subtract(6, "day"), today];
}

function ApplicationMonitor() {
  const [apps, setApps] = useState<AppItem[]>([]);
  const [currentApp, setCurrentApp] = useState<string>();
  const [dailyBase, setDailyBase] = useState<DailyBase | null>(null);
  const [allBase, setAllBase] = useState<AllBase | null>(null);
  const [dailyList, setDailyList] = useState<DailyItem[]>([]);
  const [uvList, setUvList] = useState<UvItem[]>([]);
  const [pagePv, setPagePv] = useState<PagePvItem[]>([]);
  const [errorTrend, setErrorTrend] = useState<ErrorTrendItem[]>([]);
  const [errorList, setErrorList] = useState<ErrorItem[]>([]);
  const [baseLoading, setBaseLoading] = useState(false);
  const [pvLoading, setPvLoading] = useState(false);
  const [uvLoading, setUvLoading] = useState(false);
  const [pagePvLoading, setPagePvLoading] = useState(false);
  const [errorTrendLoading, setErrorTrendLoading] = useState(false);
  const [tableLoading, setTableLoading] = useState(false);
  const [errorPageNo, setErrorPageNo] = useState(1);
  const [errorPageSize, setErrorPageSize] = useState(10);
  const [errorTotal, setErrorTotal] = useState(0);
  const [filterErrorCode, setFilterErrorCode] = useState<string>("");
  const [filterSeverity, setFilterSeverity] = useState<string | undefined>(
    undefined
  );
  const [filterRequestUri, setFilterRequestUri] = useState<string>("");

  const [pvPreset, setPvPreset] = useState<Preset>("7d");
  const [uvPreset, setUvPreset] = useState<Preset>("7d");
  const [pagePvPreset, setPagePvPreset] = useState<Preset>("7d");
  const [errorPreset, setErrorPreset] = useState<Preset>("7d");

  const [pvRange, setPvRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );
  const [uvRange, setUvRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );
  const [pagePvRange, setPagePvRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );

  const [pageRouteDrawerOpen, setPageRouteDrawerOpen] = useState(false);
  const [currentRoutePath, setCurrentRoutePath] = useState<string>("");
  const [routeVisitsLoading, setRouteVisitsLoading] = useState(false);
  const [routeVisits, setRouteVisits] = useState<any[]>([]);
  const [routeVisitsTotal, setRouteVisitsTotal] = useState(0);
  const [routeVisitsPageNo, setRouteVisitsPageNo] = useState(1);
  const [routeVisitsPageSize, setRouteVisitsPageSize] = useState(20);

  const [sessionPathPreset, setSessionPathPreset] = useState<Preset>("7d");
  const [sessionPathRange, setSessionPathRange] = useState<
    [DayjsValue, DayjsValue]
  >(getPresetRange("7d"));
  const [sessionPathsLoading, setSessionPathsLoading] = useState(false);
  const [sessionPaths, setSessionPaths] = useState<any[]>([]);
  const [sessionDetailOpen, setSessionDetailOpen] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string>("");
  const [sessionDetailLoading, setSessionDetailLoading] = useState(false);
  const [sessionDetailSteps, setSessionDetailSteps] = useState<any[]>([]);

  const [pathCollapseDuplicates, setPathCollapseDuplicates] = useState(true);
  const [pathMinStayMs, setPathMinStayMs] = useState<number>(0);
  const [pathMaxDepth, setPathMaxDepth] = useState<number>(20);
  const [pathIgnorePatterns, setPathIgnorePatterns] = useState<string>("");

  const [pathAggLoading, setPathAggLoading] = useState(false);
  const [pathAggSessionCount, setPathAggSessionCount] = useState(0);
  const [topPathPatterns, setTopPathPatterns] = useState<any[]>([]);
  const [funnelRows, setFunnelRows] = useState<any[]>([]);
  const [funnelGroups, setFunnelGroups] = useState<any[]>([]);
  const [activeFunnelGroupKey, setActiveFunnelGroupKey] = useState<string>("ALL");
  const [funnelStartRoutePath, setFunnelStartRoutePath] = useState<string>("");
  const [funnelGroupBy, setFunnelGroupBy] = useState<"NONE" | "USER" | "PARAM">(
    "NONE"
  );
  const [funnelGroupParamName, setFunnelGroupParamName] = useState<string>(
    "channel"
  );
  const [errorRange, setErrorRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );

  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialAppCode = searchParams.get("appCode")?.trim();

  useEffect(() => {
    loadApps();
  }, []);

  function readCachedAppCode() {
    try {
      const v = localStorage.getItem(APP_CODE_STORAGE_KEY);
      return v ? v.trim() : "";
    } catch {
      return "";
    }
  }

  function writeCachedAppCode(appCode: string) {
    try {
      if (appCode) localStorage.setItem(APP_CODE_STORAGE_KEY, appCode);
      else localStorage.removeItem(APP_CODE_STORAGE_KEY);
    } catch {}
  }

  async function loadApps() {
    try {
      const resp = await client.get("/application/list");
      if (resp.data && resp.data.code === 1000) {
        const list = resp.data.data as AppItem[];
        setApps(list);
        if (list.length > 0) {
          const cached = readCachedAppCode();
          const validFromUrl = initialAppCode
            ? list.find((it) => it.appCode === initialAppCode)?.appCode
            : undefined;
          const validFromCache = cached
            ? list.find((it) => it.appCode === cached)?.appCode
            : undefined;
          const code = validFromUrl || validFromCache || list[0].appCode;
          await onAppChanged(code);
        }
      }
    } catch {
      message.error("加载应用列表失败");
    }
  }

  const pvDates = useMemo(
    () => buildDateStrings(pvRange[0], pvRange[1]),
    [pvRange]
  );
  const uvDates = useMemo(
    () => buildDateStrings(uvRange[0], uvRange[1]),
    [uvRange]
  );
  const errorDates = useMemo(
    () => buildDateStrings(errorRange[0], errorRange[1]),
    [errorRange]
  );

  async function loadErrors(
    appCode: string,
    pageNo: number,
    pageSize: number,
    filters?: { errorCode?: string; severity?: string; requestUri?: string }
  ) {
    try {
      setTableLoading(true);
      const errorCode = filters?.errorCode ?? filterErrorCode;
      const severity = filters?.severity ?? filterSeverity;
      const requestUri = filters?.requestUri ?? filterRequestUri;
      const errorResp = await client.get("/application/monitor/errors/recent", {
        params: {
          appCode,
          pageNo,
          pageSize,
          errorCode: errorCode?.trim() || undefined,
          severity: severity || undefined,
          requestUri: requestUri?.trim() || undefined,
        },
      });
      if (errorResp.data && errorResp.data.code === 1000) {
        const data = errorResp.data.data;
        if (Array.isArray(data)) {
          setErrorList(data as ErrorItem[]);
          setErrorTotal(data.length);
        } else if (data && Array.isArray(data.list)) {
          setErrorList(data.list as ErrorItem[]);
          setErrorTotal(
            typeof data.total === "number"
              ? data.total
              : Number(data.total || 0)
          );
          setErrorPageNo(
            typeof data.pageNo === "number"
              ? data.pageNo
              : Number(data.pageNo || pageNo)
          );
          setErrorPageSize(
            typeof data.pageSize === "number"
              ? data.pageSize
              : Number(data.pageSize || pageSize)
          );
        } else {
          setErrorList([]);
          setErrorTotal(0);
        }
      } else {
        setErrorList([]);
        setErrorTotal(0);
      }
    } catch {
      setErrorList([]);
      setErrorTotal(0);
    } finally {
      setTableLoading(false);
    }
  }

  async function loadBaseSummary(appCode: string) {
    setBaseLoading(true);
    try {
      const [dailyResp, allResp] = await Promise.all([
        client.get("/application/monitor/dailyBase", {
          params: { appCode },
        }),
        client.get("/application/monitor/allBase", {
          params: { appCode },
        }),
      ]);

      if (dailyResp.data && dailyResp.data.code === 1000) {
        const d = dailyResp.data.data[0] as DailyBase;
        setDailyBase(d);
      } else {
        setDailyBase(null);
      }

      if (allResp.data && allResp.data.code === 1000) {
        const d = Array.isArray(allResp.data.data)
          ? (allResp.data.data[0] as AllBase)
          : (allResp.data.data as AllBase);
        setAllBase(d);
      } else {
        setAllBase(null);
      }
    } catch {
      message.error("加载监控数据失败");
    } finally {
      setBaseLoading(false);
    }
  }

  async function loadPvData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    setPvLoading(true);
    const dates = buildDateStrings(start, end);
    try {
      const dailyPvResp = await client.post("/application/monitor/dailyPV", {
        appCode,
        startDate: start.format("YYYY-MM-DD"),
        endDate: end.format("YYYY-MM-DD"),
      });
      if (dailyPvResp.data && dailyPvResp.data.code === 1000) {
        const list = dailyPvResp.data.data as DailyItem[];
        const mapped = dates.map((date) => {
          const found = list.find((it) => it.DATETIME === date);
          return {
            DATETIME: date,
            PV_NUM: found ? found.PV_NUM : 0,
          };
        });
        setDailyList(mapped);
      } else {
        setDailyList(dates.map((d) => ({ DATETIME: d, PV_NUM: 0 })));
      }
    } catch {
      message.error("加载 PV 趋势失败");
      setDailyList(dates.map((d) => ({ DATETIME: d, PV_NUM: 0 })));
    } finally {
      setPvLoading(false);
    }
  }

  async function loadUvData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    setUvLoading(true);
    const dates = buildDateStrings(start, end);
    try {
      const dailyUvResp = await client.post("/application/monitor/dailyUV", {
        appCode,
        startDate: start.format("YYYY-MM-DD"),
        endDate: end.format("YYYY-MM-DD"),
      });
      if (dailyUvResp.data && dailyUvResp.data.code === 1000) {
        const list = dailyUvResp.data.data as UvItem[];
        const mapped = dates.map((date) => {
          const found = list.find((it) => it.DATETIME === date);
          return {
            DATETIME: date,
            COUNT: found ? found.COUNT : 0,
          };
        });
        setUvList(mapped);
      } else {
        setUvList(dates.map((d) => ({ DATETIME: d, COUNT: 0 })));
      }
    } catch {
      message.error("加载 UV 趋势失败");
      setUvList(dates.map((d) => ({ DATETIME: d, COUNT: 0 })));
    } finally {
      setUvLoading(false);
    }
  }

  async function loadPagePvData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    setPagePvLoading(true);
    try {
      const resp = await client.get("/application/monitor/weeklyPagePV", {
        params: {
          appCode,
          startDate: start.format("YYYY-MM-DD"),
          endDate: end.format("YYYY-MM-DD"),
        },
      });
      if (resp.data && resp.data.code === 1000) {
        let items = resp.data.data as PagePvItem[];
        items = items.slice(0, 10);
        setPagePv(items);
      } else {
        setPagePv([]);
      }
    } catch {
      message.error("加载页面 PV 失败");
      setPagePv([]);
    } finally {
      setPagePvLoading(false);
    }
  }

  async function loadRouteVisits(
    appCode: string,
    routePath: string,
    start: DayjsValue,
    end: DayjsValue,
    pageNo: number,
    pageSize: number
  ) {
    try {
      setRouteVisitsLoading(true);
      const resp = await client.get("/application/monitor/pageRoute/visits", {
        params: {
          appCode,
          routePath,
          startDate: start.format("YYYY-MM-DD"),
          endDate: end.format("YYYY-MM-DD"),
          pageNo,
          pageSize,
        },
      });
      if (resp.data?.code === 1000 && resp.data.data) {
        const data = resp.data.data;
        setRouteVisits(Array.isArray(data.list) ? data.list : []);
        setRouteVisitsTotal(
          typeof data.total === "number" ? data.total : Number(data.total || 0)
        );
        setRouteVisitsPageNo(
          typeof data.pageNo === "number" ? data.pageNo : Number(data.pageNo || 1)
        );
        setRouteVisitsPageSize(
          typeof data.pageSize === "number"
            ? data.pageSize
            : Number(data.pageSize || pageSize)
        );
      } else {
        setRouteVisits([]);
        setRouteVisitsTotal(0);
      }
    } catch {
      setRouteVisits([]);
      setRouteVisitsTotal(0);
    } finally {
      setRouteVisitsLoading(false);
    }
  }

  async function loadSessionPathsData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    try {
      setSessionPathsLoading(true);
      const ignoreRoutePatterns = pathIgnorePatterns
        ? pathIgnorePatterns
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
        : [];
      const resp = await client.post("/application/monitor/sessionPaths", {
        appCode,
        startDate: start.format("YYYY-MM-DD"),
        endDate: end.format("YYYY-MM-DD"),
        limitSessions: 200,
        collapseConsecutiveDuplicates: pathCollapseDuplicates,
        minStayMs: pathMinStayMs || 0,
        maxDepth: pathMaxDepth || 20,
        ignoreRoutePatterns,
      });
      if (resp.data?.code === 1000) {
        setSessionPaths(Array.isArray(resp.data.data) ? resp.data.data : []);
      } else {
        setSessionPaths([]);
      }
    } catch {
      setSessionPaths([]);
    } finally {
      setSessionPathsLoading(false);
    }
  }

  async function loadSessionPathAggregateData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    try {
      setPathAggLoading(true);
      const ignoreRoutePatterns = pathIgnorePatterns
        ? pathIgnorePatterns
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
        : [];
      const resp = await client.post("/application/monitor/sessionPaths/aggregate", {
        appCode,
        startDate: start.format("YYYY-MM-DD"),
        endDate: end.format("YYYY-MM-DD"),
        limitSessions: 1000,
        topN: 30,
        collapseConsecutiveDuplicates: pathCollapseDuplicates,
        minStayMs: pathMinStayMs || 0,
        maxDepth: pathMaxDepth || 20,
        ignoreRoutePatterns,
        startRoutePath: funnelStartRoutePath?.trim() || undefined,
        groupBy: funnelGroupBy,
        groupParamName:
          funnelGroupBy === "PARAM" ? funnelGroupParamName?.trim() : undefined,
        maxGroups: 20,
      });
      if (resp.data?.code === 1000 && resp.data.data) {
        setPathAggSessionCount(
          typeof resp.data.data.sessionCount === "number"
            ? resp.data.data.sessionCount
            : Number(resp.data.data.sessionCount || 0)
        );
        const groups = Array.isArray(resp.data.data.groups)
          ? resp.data.data.groups
          : [];
        setFunnelGroups(groups);
        if (groups.length > 0) {
          const first = groups[0];
          setActiveFunnelGroupKey(String(first.GROUP_KEY ?? "ALL"));
          setTopPathPatterns(Array.isArray(first.topPaths) ? first.topPaths : []);
          setFunnelRows(Array.isArray(first.funnel) ? first.funnel : []);
        } else {
          setActiveFunnelGroupKey("ALL");
          setTopPathPatterns(
            Array.isArray(resp.data.data.topPaths) ? resp.data.data.topPaths : []
          );
          setFunnelRows(Array.isArray(resp.data.data.funnel) ? resp.data.data.funnel : []);
        }
      } else {
        setPathAggSessionCount(0);
        setTopPathPatterns([]);
        setFunnelRows([]);
        setFunnelGroups([]);
        setActiveFunnelGroupKey("ALL");
      }
    } catch {
      setPathAggSessionCount(0);
      setTopPathPatterns([]);
      setFunnelRows([]);
      setFunnelGroups([]);
      setActiveFunnelGroupKey("ALL");
    } finally {
      setPathAggLoading(false);
    }
  }

  function applyFunnelGroup(key: string) {
    const hit = funnelGroups.find((g) => String(g.GROUP_KEY) === key);
    setActiveFunnelGroupKey(key);
    if (hit) {
      setTopPathPatterns(Array.isArray(hit.topPaths) ? hit.topPaths : []);
      setFunnelRows(Array.isArray(hit.funnel) ? hit.funnel : []);
    } else {
      setTopPathPatterns([]);
      setFunnelRows([]);
    }
  }

  async function loadSessionDetail(
    appCode: string,
    sessionId: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    try {
      setSessionDetailLoading(true);
      const resp = await client.get("/application/monitor/sessionPaths/detail", {
        params: {
          appCode,
          sessionId,
          startDate: start.format("YYYY-MM-DD"),
          endDate: end.format("YYYY-MM-DD"),
          collapseConsecutiveDuplicates: pathCollapseDuplicates,
          minStayMs: pathMinStayMs || 0,
          maxDepth: pathMaxDepth || 20,
          ignoreRoutePatterns: pathIgnorePatterns || undefined,
        },
      });
      if (resp.data?.code === 1000) {
        setSessionDetailSteps(Array.isArray(resp.data.data) ? resp.data.data : []);
      } else {
        setSessionDetailSteps([]);
      }
    } catch {
      setSessionDetailSteps([]);
    } finally {
      setSessionDetailLoading(false);
    }
  }

  async function loadErrorTrendData(
    appCode: string,
    start: DayjsValue,
    end: DayjsValue
  ) {
    setErrorTrendLoading(true);
    const dates = buildDateStrings(start, end);
    try {
      const resp = await client.post("/application/monitor/dailyError", {
        appCode,
        startDate: start.format("YYYY-MM-DD"),
        endDate: end.format("YYYY-MM-DD"),
      });
      if (resp.data && resp.data.code === 1000) {
        const list = resp.data.data as ErrorTrendItem[];
        const mapped = dates.map((date) => {
          const found = list.find((it) => it.DATETIME === date);
          return {
            DATETIME: date,
            ERROR_NUM: found ? found.ERROR_NUM : 0,
          };
        });
        setErrorTrend(mapped);
      } else {
        setErrorTrend(dates.map((d) => ({ DATETIME: d, ERROR_NUM: 0 })));
      }
    } catch {
      message.error("加载错误趋势失败");
      setErrorTrend(dates.map((d) => ({ DATETIME: d, ERROR_NUM: 0 })));
    } finally {
      setErrorTrendLoading(false);
    }
  }

  async function onAppChanged(appCode: string) {
    const nextFilters = {
      errorCode: "",
      severity: undefined,
      requestUri: "",
    };
    writeCachedAppCode(appCode);
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (appCode) next.set("appCode", appCode);
        else next.delete("appCode");
        return next;
      },
      { replace: true }
    );
    setCurrentApp(appCode);
    setFilterErrorCode("");
    setFilterSeverity(undefined);
    setFilterRequestUri("");
    setErrorPageNo(1);
    await Promise.all([
      loadBaseSummary(appCode),
      loadPvData(appCode, pvRange[0], pvRange[1]),
      loadUvData(appCode, uvRange[0], uvRange[1]),
      loadPagePvData(appCode, pagePvRange[0], pagePvRange[1]),
      loadErrorTrendData(appCode, errorRange[0], errorRange[1]),
      loadSessionPathsData(appCode, sessionPathRange[0], sessionPathRange[1]),
      loadSessionPathAggregateData(appCode, sessionPathRange[0], sessionPathRange[1]),
      loadErrors(appCode, 1, errorPageSize, nextFilters),
    ]);
  }

  const pvOption = useMemo(() => {
    return {
      grid: { left: 40, right: 20, top: 30, bottom: 40 },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: pvDates.map((d) => d.slice(5)),
      },
      yAxis: { type: "value" },
      series: [
        {
          name: "PV",
          type: "line",
          smooth: true,
          data: dailyList.map((d) => d.PV_NUM),
        },
      ],
    } as const;
  }, [dailyList, pvDates]);

  const uvOption = useMemo(() => {
    return {
      grid: { left: 40, right: 20, top: 30, bottom: 40 },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: uvDates.map((d) => d.slice(5)),
      },
      yAxis: { type: "value" },
      series: [
        {
          name: "UV",
          type: "line",
          smooth: true,
          data: uvList.map((d) => d.COUNT),
        },
      ],
    } as const;
  }, [uvDates, uvList]);

  const pagePvOption = useMemo(() => {
    return {
      grid: { left: 40, right: 20, top: 30, bottom: 90 },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "shadow" },
        formatter: (params: any) => {
          const first = Array.isArray(params) ? params[0] : params;
          const url = first?.axisValue || "";
          const val = first?.data ?? 0;
          const found = pagePv.find((p) => p.PAGE_URL === url);
          const sessionNum = found?.SESSION_NUM ?? 0;
          const userNum = found?.USER_NUM ?? 0;
          return `${url}<br/>PV: ${val}<br/>会话数: ${sessionNum}<br/>用户数: ${userNum}`;
        },
      },
      xAxis: {
        type: "category",
        data: pagePv.map((p) => p.PAGE_URL),
        axisLabel: {
          interval: 0,
          rotate: 30,
          formatter: (value: string) => ellipsis(value, 18),
        },
      },
      yAxis: { type: "value" },
      series: [
        {
          name: "PV",
          type: "bar",
          data: pagePv.map((p) => p.PV_NUM),
          barMaxWidth: 32,
        },
      ],
    } as const;
  }, [pagePv]);

  const errorOption = useMemo(() => {
    return {
      grid: { left: 40, right: 20, top: 30, bottom: 40 },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: errorDates.map((d) => d.slice(5)),
      },
      yAxis: { type: "value" },
      series: [
        {
          name: "错误数",
          type: "line",
          smooth: true,
          data: errorTrend.map((d) => d.ERROR_NUM),
        },
      ],
    } as const;
  }, [errorDates, errorTrend]);

  const pvTitle = (
    <Space wrap align="center" size={8}>
      <span style={{ fontWeight: 600 }}>PV 趋势</span>
      <Segmented
        value={pvPreset}
        onChange={(val) => {
          const preset = val as Preset;
          setPvPreset(preset);
          if (!currentApp) return;
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setPvRange(r);
            loadPvData(currentApp, r[0], r[1]);
          }
        }}
        options={[
          { label: "近7天", value: "7d" },
          { label: "近1个月", value: "30d" },
          { label: "近3个月", value: "90d" },
          { label: "自定义", value: "custom" },
        ]}
        size="small"
      />
      {pvPreset === "custom" && (
        <RangePicker
          allowClear={false}
          value={pvRange}
          disabledDate={(current) => current && current > dayjs()}
          onChange={(vals) => {
            if (!currentApp) return;
            if (vals && vals[0] && vals[1]) {
              setPvRange([vals[0], vals[1]]);
              loadPvData(currentApp, vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => {
          if (!currentApp) return;
          loadPvData(currentApp, pvRange[0], pvRange[1]);
        }}
      >
        刷新
      </Button>
    </Space>
  );

  const uvTitle = (
    <Space wrap align="center" size={8}>
      <span style={{ fontWeight: 600 }}>UV 趋势</span>
      <Segmented
        value={uvPreset}
        onChange={(val) => {
          const preset = val as Preset;
          setUvPreset(preset);
          if (!currentApp) return;
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setUvRange(r);
            loadUvData(currentApp, r[0], r[1]);
          }
        }}
        options={[
          { label: "近7天", value: "7d" },
          { label: "近1个月", value: "30d" },
          { label: "近3个月", value: "90d" },
          { label: "自定义", value: "custom" },
        ]}
        size="small"
      />
      {uvPreset === "custom" && (
        <RangePicker
          allowClear={false}
          value={uvRange}
          disabledDate={(current) => current && current > dayjs()}
          onChange={(vals) => {
            if (!currentApp) return;
            if (vals && vals[0] && vals[1]) {
              setUvRange([vals[0], vals[1]]);
              loadUvData(currentApp, vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => {
          if (!currentApp) return;
          loadUvData(currentApp, uvRange[0], uvRange[1]);
        }}
      >
        刷新
      </Button>
    </Space>
  );

  const pagePvTitle = (
    <Space wrap align="center" size={8}>
      <span style={{ fontWeight: 600 }}>各页面 PV</span>
      <Segmented
        value={pagePvPreset}
        onChange={(val) => {
          const preset = val as Preset;
          setPagePvPreset(preset);
          if (!currentApp) return;
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setPagePvRange(r);
            loadPagePvData(currentApp, r[0], r[1]);
          }
        }}
        options={[
          { label: "近7天", value: "7d" },
          { label: "近1个月", value: "30d" },
          { label: "近3个月", value: "90d" },
          { label: "自定义", value: "custom" },
        ]}
        size="small"
      />
      {pagePvPreset === "custom" && (
        <RangePicker
          allowClear={false}
          value={pagePvRange}
          disabledDate={(current) => current && current > dayjs()}
          onChange={(vals) => {
            if (!currentApp) return;
            if (vals && vals[0] && vals[1]) {
              setPagePvRange([vals[0], vals[1]]);
              loadPagePvData(currentApp, vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => {
          if (!currentApp) return;
          loadPagePvData(currentApp, pagePvRange[0], pagePvRange[1]);
        }}
      >
        刷新
      </Button>
    </Space>
  );

  const errorTitle = (
    <Space wrap align="center" size={8}>
      <span style={{ fontWeight: 600 }}>错误趋势</span>
      <Segmented
        value={errorPreset}
        onChange={(val) => {
          const preset = val as Preset;
          setErrorPreset(preset);
          if (!currentApp) return;
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setErrorRange(r);
            loadErrorTrendData(currentApp, r[0], r[1]);
          }
        }}
        options={[
          { label: "近7天", value: "7d" },
          { label: "近1个月", value: "30d" },
          { label: "近3个月", value: "90d" },
          { label: "自定义", value: "custom" },
        ]}
        size="small"
      />
      {errorPreset === "custom" && (
        <RangePicker
          allowClear={false}
          value={errorRange}
          disabledDate={(current) => current && current > dayjs()}
          onChange={(vals) => {
            if (!currentApp) return;
            if (vals && vals[0] && vals[1]) {
              setErrorRange([vals[0], vals[1]]);
              loadErrorTrendData(currentApp, vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => {
          if (!currentApp) return;
          loadErrorTrendData(currentApp, errorRange[0], errorRange[1]);
        }}
      >
        刷新
      </Button>
    </Space>
  );

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Row align="middle" gutter={16}>
          <Col>
            <span>选择应用</span>
          </Col>
          <Col>
            <Select
              style={{ width: 260 }}
              placeholder="请选择应用"
              value={currentApp}
              onChange={(val) => {
                onAppChanged(val);
              }}
              options={apps.map((a) => ({
                label: `${a.appName}(${a.appCode})`,
                value: a.appCode,
              }))}
            />
          </Col>
          <Col>
            <Button type="link" onClick={() => navigate("/application")}>
              应用管理
            </Button>
          </Col>
        </Row>
      </Card>
      <Row gutter={16}>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="日活跃用户（DAU）"
            value={dailyBase ? dailyBase.USER_COUNT : 0}
            footer={`历史用户数: ${allBase ? allBase.USER_COUNT : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="日活跃设备"
            value={dailyBase ? dailyBase.DEVICE_NUM : 0}
            footer={`历史设备数: ${allBase ? allBase.DEVICE_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="日活跃会话"
            value={dailyBase ? dailyBase.SESSION_UNM : 0}
            footer={`历史会话数: ${allBase ? allBase.SESSION_UNM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="日点击量"
            value={dailyBase ? dailyBase.CLICK_NUM : 0}
            footer={`历史点击量: ${allBase ? allBase.CLICK_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="页面访问量（PV）"
            value={dailyBase ? dailyBase.PV_NUM : 0}
            footer={`历史浏览数: ${allBase ? allBase.PV_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={baseLoading}
            title="日上报错误"
            value={dailyBase ? dailyBase.ERROR_NUM || 0 : 0}
            footer={`历史错误数: ${allBase ? allBase.ERROR_NUM || 0 : 0}`}
          />
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title={pvTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton active loading={pvLoading} paragraph={{ rows: 8 }}>
              <EChart option={pvOption} height={360} />
            </Skeleton>
          </Card>
        </Col>
        <Col span={12}>
          <Card title={uvTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton active loading={uvLoading} paragraph={{ rows: 8 }}>
              <EChart option={uvOption} height={360} />
            </Skeleton>
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title={pagePvTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton active loading={pagePvLoading} paragraph={{ rows: 8 }}>
              <EChart
                option={pagePvOption}
                height={360}
                onChartClick={(params) => {
                  const routePath = params?.name;
                  if (!routePath || !currentApp) return;
                  setCurrentRoutePath(String(routePath));
                  setPageRouteDrawerOpen(true);
                  setRouteVisitsPageNo(1);
                  loadRouteVisits(
                    currentApp,
                    String(routePath),
                    pagePvRange[0],
                    pagePvRange[1],
                    1,
                    routeVisitsPageSize
                  );
                }}
              />
            </Skeleton>
          </Card>
        </Col>
        <Col span={12}>
          <Card title={errorTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton
              active
              loading={errorTrendLoading}
              paragraph={{ rows: 8 }}
            >
              <EChart option={errorOption} height={360} />
            </Skeleton>
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space wrap align="center" size={8}>
            <span style={{ fontWeight: 600 }}>会话路径分析</span>
            <Space size={6}>
              <span>折叠重复</span>
              <Switch
                size="small"
                checked={pathCollapseDuplicates}
                onChange={(v) => setPathCollapseDuplicates(v)}
              />
            </Space>
            <Space size={6}>
              <span>最小停留(ms)</span>
              <InputNumber
                size="small"
                min={0}
                value={pathMinStayMs}
                onChange={(v) => setPathMinStayMs(Number(v || 0))}
              />
            </Space>
            <Space size={6}>
              <span>最大深度</span>
              <InputNumber
                size="small"
                min={1}
                value={pathMaxDepth}
                onChange={(v) => setPathMaxDepth(Number(v || 20))}
              />
            </Space>
            <Input
              size="small"
              style={{ width: 260 }}
              placeholder="忽略路由(正则/逗号分隔)"
              value={pathIgnorePatterns}
              onChange={(e) => setPathIgnorePatterns(e.target.value)}
              allowClear
            />
            <Segmented
              value={sessionPathPreset}
              onChange={(val) => {
                const preset = val as Preset;
                setSessionPathPreset(preset);
                if (!currentApp) return;
                if (preset !== "custom") {
                  const r = getPresetRange(preset);
                  setSessionPathRange(r);
                  loadSessionPathsData(currentApp, r[0], r[1]);
                  loadSessionPathAggregateData(currentApp, r[0], r[1]);
                }
              }}
              options={[
                { label: "近7天", value: "7d" },
                { label: "近1个月", value: "30d" },
                { label: "近3个月", value: "90d" },
                { label: "自定义", value: "custom" },
              ]}
              size="small"
            />
            {sessionPathPreset === "custom" && (
              <RangePicker
                allowClear={false}
                value={sessionPathRange}
                disabledDate={(current) => current && current > dayjs()}
                onChange={(vals) => {
                  if (!currentApp) return;
                  if (vals && vals[0] && vals[1]) {
                    setSessionPathRange([vals[0], vals[1]]);
                    loadSessionPathsData(currentApp, vals[0], vals[1]);
                    loadSessionPathAggregateData(currentApp, vals[0], vals[1]);
                  }
                }}
              />
            )}
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => {
                if (!currentApp) return;
                loadSessionPathsData(currentApp, sessionPathRange[0], sessionPathRange[1]);
                loadSessionPathAggregateData(currentApp, sessionPathRange[0], sessionPathRange[1]);
              }}
            >
              刷新
            </Button>
          </Space>
        }
        style={{ marginTop: 16 }}
      >
        <Skeleton active loading={sessionPathsLoading} paragraph={{ rows: 6 }}>
          <Table
            rowKey="SESSION_ID"
            dataSource={sessionPaths}
            pagination={{ pageSize: 10 }}
            columns={[
              { title: "会话ID", dataIndex: "SESSION_ID", width: 220 },
              { title: "用户", dataIndex: "SDK_USER_UUID", width: 180 },
              { title: "设备", dataIndex: "DEVICE_ID", width: 160 },
              { title: "开始时间", dataIndex: "FIRST_TIME", width: 180 },
              { title: "结束时间", dataIndex: "LAST_TIME", width: 180 },
              { title: "步数", dataIndex: "STEP_COUNT", width: 80 },
              {
                title: "路径",
                dataIndex: "PATH",
                render: (v: string) => (
                  <Typography.Text ellipsis={{ tooltip: v }} style={{ maxWidth: 520 }} >
                    {v}
                  </Typography.Text>
                ),
              },
              {
                title: "操作",
                width: 100,
                render: (_: any, row: any) => (
                  <Button
                    type="link"
                    onClick={() => {
                      if (!currentApp) return;
                      setCurrentSessionId(row.SESSION_ID);
                      setSessionDetailOpen(true);
                      loadSessionDetail(
                        currentApp,
                        row.SESSION_ID,
                        sessionPathRange[0],
                        sessionPathRange[1]
                      );
                    }}
                  >
                    查看
                  </Button>
                ),
              },
            ]}
          />
        </Skeleton>
      </Card>

      <Card
        title={
          <Space wrap align="center" size={8}>
            <span style={{ fontWeight: 600 }}>
              路径聚类 / Top 路径漏斗
            </span>
            <Input
              size="small"
              style={{ width: 180 }}
              placeholder="起始页路由(可选)"
              value={funnelStartRoutePath}
              onChange={(e) => setFunnelStartRoutePath(e.target.value)}
              allowClear
            />
            <Select
              size="small"
              style={{ width: 120 }}
              value={funnelGroupBy}
              onChange={(v) => setFunnelGroupBy(v)}
              options={[
                { label: "不分组", value: "NONE" },
                { label: "按用户", value: "USER" },
                { label: "按参数", value: "PARAM" },
              ]}
            />
            {funnelGroupBy === "PARAM" && (
              <Input
                size="small"
                style={{ width: 140 }}
                placeholder="参数名，如 channel"
                value={funnelGroupParamName}
                onChange={(e) => setFunnelGroupParamName(e.target.value)}
              />
            )}
            {funnelGroups.length > 1 && (
              <Select
                size="small"
                style={{ width: 200 }}
                value={activeFunnelGroupKey}
                onChange={(v) => applyFunnelGroup(String(v))}
                options={funnelGroups.map((g) => ({
                  label: `${g.GROUP_KEY} (${g.SESSION_COUNT})`,
                  value: String(g.GROUP_KEY),
                }))}
              />
            )}
            <Typography.Text type="secondary">
              统计会话数: {pathAggSessionCount}
            </Typography.Text>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => {
                if (!currentApp) return;
                loadSessionPathAggregateData(currentApp, sessionPathRange[0], sessionPathRange[1]);
              }}
            >
              刷新
            </Button>
          </Space>
        }
        style={{ marginTop: 16 }}
      >
        <Skeleton active loading={pathAggLoading} paragraph={{ rows: 8 }}>
          <Row gutter={16}>
            <Col span={14}>
              <Table
                rowKey={(r) => `${r.PATH || ""}-${r.COUNT || 0}`}
                dataSource={topPathPatterns}
                pagination={{ pageSize: 8 }}
                columns={[
                  { title: "会话数", dataIndex: "COUNT", width: 90 },
                  {
                    title: "占比(%)",
                    dataIndex: "PCT",
                    width: 90,
                    render: (v: any) =>
                      typeof v === "number" ? v.toFixed(2) : String(v || "0"),
                  },
                  {
                    title: "路径模式",
                    dataIndex: "PATH",
                    render: (v: string) => (
                      <Typography.Text ellipsis={{ tooltip: v }} style={{ maxWidth: 520 }}>
                        {v}
                      </Typography.Text>
                    ),
                  },
                  {
                    title: "示例",
                    width: 80,
                    render: (_: any, row: any) => (
                      <Button
                        type="link"
                        onClick={() => {
                          if (!currentApp) return;
                          const sid = row.SAMPLE_SESSION_ID;
                          if (!sid) return;
                          setCurrentSessionId(sid);
                          setSessionDetailOpen(true);
                          loadSessionDetail(currentApp, sid, sessionPathRange[0], sessionPathRange[1]);
                        }}
                      >
                        查看
                      </Button>
                    ),
                  },
                ]}
              />
            </Col>
            <Col span={10}>
              <Table
                rowKey={(r) => `${r.STEP}-${r.ROUTE_PATH}-${r.COUNT}`}
                dataSource={funnelRows}
                pagination={{ pageSize: 10 }}
                columns={[
                  { title: "步", dataIndex: "STEP", width: 50 },
                  { title: "路由", dataIndex: "ROUTE_PATH" },
                  { title: "会话数", dataIndex: "COUNT", width: 90 },
                ]}
              />
            </Col>
          </Row>
        </Skeleton>
      </Card>

      <Drawer
        title={`页面访问下钻：${currentRoutePath || "-"}`}
        open={pageRouteDrawerOpen}
        width={920}
        onClose={() => setPageRouteDrawerOpen(false)}
      >
        <Table
          rowKey={(_, idx) => String(idx)}
          loading={routeVisitsLoading}
          dataSource={routeVisits}
          pagination={{
            current: routeVisitsPageNo,
            pageSize: routeVisitsPageSize,
            total: routeVisitsTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
          onChange={(p) => {
            if (!currentApp || !currentRoutePath) return;
            const nextSize = p.pageSize || routeVisitsPageSize;
            const nextNo =
              p.pageSize && p.pageSize !== routeVisitsPageSize ? 1 : p.current || 1;
            setRouteVisitsPageNo(nextNo);
            setRouteVisitsPageSize(nextSize);
            loadRouteVisits(
              currentApp,
              currentRoutePath,
              pagePvRange[0],
              pagePvRange[1],
              nextNo,
              nextSize
            );
          }}
          columns={[
            { title: "时间", dataIndex: "CREATED_AT", width: 180 },
            { title: "会话ID", dataIndex: "SESSION_ID", width: 220 },
            { title: "用户", dataIndex: "SDK_USER_UUID", width: 180 },
            { title: "设备", dataIndex: "DEVICE_ID", width: 160 },
            {
              title: "参数",
              dataIndex: "ROUTE_PARAMS",
              render: (v: string) => (
                <Button
                  type="link"
                  onClick={() => {
                    Modal.info({
                      title: "路由参数",
                      width: 680,
                      content: (
                        <pre style={{ maxHeight: 520, overflow: "auto" }}>
                          {(() => {
                            try {
                              return JSON.stringify(JSON.parse(v || "{}"), null, 2);
                            } catch {
                              return String(v || "");
                            }
                          })()}
                        </pre>
                      ),
                    });
                  }}
                >
                  查看
                </Button>
              ),
            },
            {
              title: "完整URL",
              dataIndex: "FULL_URL",
              render: (v: string) => (
                <Typography.Text ellipsis={{ tooltip: v }} style={{ maxWidth: 260 }}>
                  {v}
                </Typography.Text>
              ),
            },
          ]}
        />
      </Drawer>

      <Drawer
        title={`会话路径详情：${currentSessionId || "-"}`}
        open={sessionDetailOpen}
        width={820}
        onClose={() => setSessionDetailOpen(false)}
      >
        <Table
          rowKey={(_, idx) => String(idx)}
          loading={sessionDetailLoading}
          dataSource={sessionDetailSteps}
          pagination={{ pageSize: 20 }}
          columns={[
            { title: "时间", dataIndex: "CREATED_AT", width: 180 },
            { title: "路由", dataIndex: "ROUTE_PATH", width: 260 },
            { title: "路由类型", dataIndex: "ROUTE_TYPE", width: 100 },
            {
              title: "参数",
              dataIndex: "ROUTE_PARAMS",
              render: (v: string) => (
                <Button
                  type="link"
                  onClick={() => {
                    Modal.info({
                      title: "路由参数",
                      width: 680,
                      content: (
                        <pre style={{ maxHeight: 520, overflow: "auto" }}>
                          {(() => {
                            try {
                              return JSON.stringify(JSON.parse(v || "{}"), null, 2);
                            } catch {
                              return String(v || "");
                            }
                          })()}
                        </pre>
                      ),
                    });
                  }}
                >
                  查看
                </Button>
              ),
            },
            {
              title: "完整URL",
              dataIndex: "FULL_URL",
              render: (v: string) => (
                <Typography.Text ellipsis={{ tooltip: v }} style={{ maxWidth: 260 }}>
                  {v}
                </Typography.Text>
              ),
            },
          ]}
        />
      </Drawer>

      <Card
        title={
          <Space size={6} align="center">
            <span>近期错误</span>
            <Tooltip
              title={
                <div style={{ maxWidth: 380 }}>
                  <div>
                    CRITICAL：影响核心功能/页面不可用（白屏、ChunkLoadError、OOM
                    等）
                  </div>
                  <div>
                    FATAL：导致流程中断或不可恢复错误（含 fatal 关键字等）
                  </div>
                  <div>ERROR：运行时异常、接口 5xx 等</div>
                  <div>WARN：网络超时/失败、接口 4xx 等</div>
                  <div>INFO：仅记录，不影响功能</div>
                  <div style={{ marginTop: 8 }}>
                    说明：如 payload 已上报 severity/level/errLevel
                    则优先使用，否则按规则自动分级。
                  </div>
                </div>
              }
            >
              <InfoCircleOutlined />
            </Tooltip>
          </Space>
        }
        style={{ marginTop: 16 }}
      >
        <Space wrap style={{ marginBottom: 12 }}>
          <Input
            style={{ width: 220 }}
            placeholder="错误类型/代码"
            value={filterErrorCode}
            onChange={(e) => setFilterErrorCode(e.target.value)}
            allowClear
          />
          <Select
            style={{ width: 160 }}
            placeholder="严重程度"
            value={filterSeverity}
            onChange={(v) => setFilterSeverity(v)}
            allowClear
            options={[
              { label: "INFO", value: "INFO" },
              { label: "WARN", value: "WARN" },
              { label: "ERROR", value: "ERROR" },
              { label: "FATAL", value: "FATAL" },
              { label: "CRITICAL", value: "CRITICAL" },
            ]}
          />
          <Input
            style={{ width: 260 }}
            placeholder="页面（triggerPageUrl/requestUri）"
            value={filterRequestUri}
            onChange={(e) => setFilterRequestUri(e.target.value)}
            allowClear
          />
          <Button
            type="primary"
            onClick={() => {
              if (!currentApp) return;
              setErrorPageNo(1);
              loadErrors(currentApp, 1, errorPageSize);
            }}
          >
            查询
          </Button>
          <Button
            onClick={() => {
              if (!currentApp) return;
              const nextFilters = {
                errorCode: "",
                severity: undefined,
                requestUri: "",
              };
              setFilterErrorCode("");
              setFilterSeverity(undefined);
              setFilterRequestUri("");
              setErrorPageNo(1);
              loadErrors(currentApp, 1, errorPageSize, nextFilters);
            }}
          >
            重置
          </Button>
        </Space>
        <RecentErrorsTable
          data={errorList}
          loading={tableLoading}
          currentAppCode={currentApp}
          fetchPayload={async (row) => {
            const id = row.ID;
            const appCode = currentApp;
            if (!id || !appCode) return null;
            const resp = await client.get(
              "/application/monitor/errors/detail",
              {
                params: { appCode, id },
              }
            );
            if (resp.data?.code === 1000 && resp.data.data?.PAYLOAD) {
              return String(resp.data.data.PAYLOAD);
            }
            throw new Error(resp.data?.msg || "查询错误详情失败");
          }}
          tableProps={{
            pagination: {
              current: errorPageNo,
              pageSize: errorPageSize,
              total: errorTotal,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            },
            onChange: (p) => {
              if (!currentApp) return;
              const nextSize = p.pageSize || errorPageSize;
              const nextNo =
                p.pageSize && p.pageSize !== errorPageSize ? 1 : p.current || 1;
              setErrorPageNo(nextNo);
              setErrorPageSize(nextSize);
              loadErrors(currentApp, nextNo, nextSize);
            },
          }}
        />
      </Card>
    </div>
  );
}

export default ApplicationMonitor;
