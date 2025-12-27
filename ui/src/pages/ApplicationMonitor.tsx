import { InfoCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import {
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Row,
  Segmented,
  Select,
  Skeleton,
  Space,
  Tooltip,
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
  const [errorRange, setErrorRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );

  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const initialAppCode = searchParams.get("appCode")?.trim();

  useEffect(() => {
    loadApps();
  }, []);

  async function loadApps() {
    try {
      const resp = await client.get("/application/list");
      if (resp.data && resp.data.code === 1000) {
        const list = resp.data.data as AppItem[];
        setApps(list);
        if (list.length > 0) {
          let code = list[0].appCode;
          if (initialAppCode) {
            const target = list.find((it) => it.appCode === initialAppCode);
            if (target) {
              code = target.appCode;
            }
          }
          setCurrentApp(code);
          onAppChanged(code);
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
          return `${url}<br/>PV: ${val}`;
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
              <EChart option={pagePvOption} height={360} />
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
