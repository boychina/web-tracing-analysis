import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Row,
  Segmented,
  Skeleton,
  Space,
  Tag,
  message,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import EChart from "../components/EChart";
import MetricCard from "../components/MetricCard";
import RecentErrorsTable, {
  type RecentErrorItem,
} from "../components/RecentErrorsTable";

const { RangePicker } = DatePicker;

/** 状态看板数据 */
type StatusBoard = {
  APPLICATION_NUM?: number;
  USER_COUNT?: number;
  DEVICE_NUM?: number;
  SESSION_UNM?: number;
  CLICK_NUM?: number;
  PV_NUM?: number;
  ERROR_NUM?: number;
  DELAY_MINUTES?: number;
  STATUS?: string;
};

/** 总量趋势（PV/UV），兼容可选应用字段 */
type TrendPoint = {
  DATETIME?: string;
  COUNT?: number;
  APP_CODE?: string;
  APP_NAME?: string;
};

/** 按应用的 PV 曲线 */
type PvByAppPoint = {
  APP_CODE?: string;
  APP_NAME?: string;
  DATETIME?: string;
  PV_NUM?: number;
};

type BaseTotals = {
  APPLICATION_NUM?: number;
  USER_COUNT?: number;
  DEVICE_NUM?: number;
  SESSION_UNM?: number;
  CLICK_NUM?: number;
  PV_NUM?: number;
  ERROR_NUM?: number;
};

type DayjsValue = any;

type Preset = "7d" | "30d" | "90d" | "custom";

function formatNumber(value?: number) {
  return typeof value === "number" ? value : 0;
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

type ErrorItem = RecentErrorItem;

function AnalysisDashboard() {
  const [statusBoard, setStatusBoard] = useState<StatusBoard | null>(null);
  const [allBase, setAllBase] = useState<BaseTotals | null>(null);
  const [pvTrend, setPvTrend] = useState<TrendPoint[]>([]);
  const [uvTrend, setUvTrend] = useState<TrendPoint[]>([]);
  const [uvByApp, setUvByApp] = useState<TrendPoint[]>([]);
  const [pvByApp, setPvByApp] = useState<PvByAppPoint[]>([]);
  const [errorList, setErrorList] = useState<ErrorItem[]>([]);

  const navigate = useNavigate();

  const [boardLoading, setBoardLoading] = useState(false);
  const [pvLoading, setPvLoading] = useState(false);
  const [uvLoading, setUvLoading] = useState(false);
  const [errorListLoading, setErrorListLoading] = useState(false);

  const [pvPreset, setPvPreset] = useState<Preset>("7d");
  const [uvPreset, setUvPreset] = useState<Preset>("7d");
  const [pvRange, setPvRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );
  const [uvRange, setUvRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d")
  );

  const pvDates = useMemo(
    () => buildDateStrings(pvRange[0], pvRange[1]),
    [pvRange]
  );
  const uvDates = useMemo(
    () => buildDateStrings(uvRange[0], uvRange[1]),
    [uvRange]
  );

  const loadOverview = useCallback(async () => {
    setBoardLoading(true);
    try {
      const [statusResp, allResp] = await Promise.all([
        client.get("/webTrack/statusBoard"),
        client.get("/webTrack/queryAllBaseInfo"),
      ]);
      if (statusResp.data?.code === 1000) {
        setStatusBoard(statusResp.data.data as StatusBoard);
      } else {
        setStatusBoard(null);
      }
      if (allResp.data?.code === 1000 && Array.isArray(allResp.data.data)) {
        const item = allResp.data.data[0] as BaseTotals;
        setAllBase(item);
      }
    } finally {
      setBoardLoading(false);
    }
  }, []);

  const loadPvData = useCallback(async (start: DayjsValue, end: DayjsValue) => {
    setPvLoading(true);
    const payload = {
      startDate: start.format("YYYY-MM-DD"),
      endDate: end.format("YYYY-MM-DD"),
    };
    try {
      const [pvResp, pvByAppResp] = await Promise.all([
        client.post("/webTrack/trend/pv", payload),
        client.post("/webTrack/queryDailyInfo", payload),
      ]);
      if (pvResp.data?.code === 1000 && Array.isArray(pvResp.data.data)) {
        setPvTrend(pvResp.data.data as TrendPoint[]);
      } else {
        setPvTrend([]);
      }
      if (
        pvByAppResp.data?.code === 1000 &&
        Array.isArray(pvByAppResp.data.data)
      ) {
        setPvByApp(pvByAppResp.data.data as PvByAppPoint[]);
      } else {
        setPvByApp([]);
      }
    } catch {
      message.error("加载 PV 趋势失败");
    } finally {
      setPvLoading(false);
    }
  }, []);

  const loadUvData = useCallback(async (start: DayjsValue, end: DayjsValue) => {
    setUvLoading(true);
    const payload = {
      startDate: start.format("YYYY-MM-DD"),
      endDate: end.format("YYYY-MM-DD"),
    };
    try {
      const [uvResp, uvByAppResp] = await Promise.all([
        client.post("/webTrack/trend/uv", payload),
        client.post("/webTrack/trend/uvByApp", payload),
      ]);
      if (uvResp.data?.code === 1000 && Array.isArray(uvResp.data.data)) {
        setUvTrend(uvResp.data.data as TrendPoint[]);
      } else {
        setUvTrend([]);
      }
      if (
        uvByAppResp.data?.code === 1000 &&
        Array.isArray(uvByAppResp.data.data)
      ) {
        setUvByApp(uvByAppResp.data.data as TrendPoint[]);
      } else {
        setUvByApp([]);
      }
    } catch {
      message.error("加载 UV 趋势失败");
    } finally {
      setUvLoading(false);
    }
  }, []);

  const loadErrorList = useCallback(async (limit = 10) => {
    setErrorListLoading(true);
    try {
      const resp = await client.get("/webTrack/errors/recent", {
        params: { limit },
      });
      if (resp.data?.code === 1000 && Array.isArray(resp.data.data)) {
        setErrorList(resp.data.data as ErrorItem[]);
      } else {
        setErrorList([]);
      }
    } catch {
      message.error("加载错误列表失败");
    } finally {
      setErrorListLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  useEffect(() => {
    loadPvData(pvRange[0], pvRange[1]);
  }, [loadPvData, pvRange]);

  useEffect(() => {
    loadUvData(uvRange[0], uvRange[1]);
  }, [loadUvData, uvRange]);

  useEffect(() => {
    loadErrorList();
  }, [loadErrorList]);

  const pvOption = useMemo(() => {
    const nameByCode: Record<string, string> = {};
    const pvMap = new Map<string, Map<string, number>>();
    const enabledCodes = new Set<string>();
    const totalMap = new Map<string, number>();

    for (const item of pvTrend) {
      const day = item.DATETIME;
      if (!day) continue;
      totalMap.set(day, typeof item.COUNT === "number" ? item.COUNT : 0);
    }

    for (const item of pvByApp) {
      const code = item.APP_CODE;
      const date = item.DATETIME;
      if (!code || !date) continue;
      const pv = typeof item.PV_NUM === "number" ? item.PV_NUM : 0;
      nameByCode[code] = item.APP_NAME || code;
      enabledCodes.add(code);
      if (!pvMap.has(code)) {
        pvMap.set(code, new Map());
      }
      pvMap.get(code)!.set(date, pv);
    }

    const codes = Array.from(enabledCodes);
    const series = [
      {
        name: "总PV",
        type: "line",
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3 },
        data: pvDates.map((d) => totalMap.get(d) ?? 0),
      },
      ...codes.map((code) => {
        const values = pvMap.get(code) || new Map<string, number>();
        return {
          name: nameByCode[code] || code,
          type: "line",
          smooth: true,
          showSymbol: false,
          data: pvDates.map((d) => values.get(d) || 0),
        };
      }),
    ];

    return {
      tooltip: { trigger: "axis" },
      legend: { type: "scroll", data: series.map((s) => s.name) },
      grid: { left: 40, right: 20, top: 60, bottom: 60 },
      xAxis: {
        type: "category",
        data: pvDates,
        axisLabel: { formatter: (v: string) => v.slice(5) },
      },
      yAxis: { type: "value" },
      toolbox: { feature: { saveAsImage: {} } },
      series,
    } as const;
  }, [pvDates, pvByApp, pvTrend]);

  const uvOption = useMemo(() => {
    const totalMap = new Map<string, number>();
    for (const item of uvTrend) {
      if (!item.DATETIME) continue;
      totalMap.set(
        item.DATETIME,
        typeof item.COUNT === "number" ? item.COUNT : 0
      );
    }

    const nameByCode: Record<string, string> = {};
    const uvMap = new Map<string, Map<string, number>>();
    const enabledCodes = new Set<string>();
    for (const item of uvByApp) {
      const code = item.APP_CODE;
      const date = item.DATETIME;
      if (!code || !date) continue;
      const cnt = typeof item.COUNT === "number" ? item.COUNT : 0;
      nameByCode[code] = item.APP_NAME || code;
      enabledCodes.add(code);
      if (!uvMap.has(code)) {
        uvMap.set(code, new Map());
      }
      uvMap.get(code)!.set(date, cnt);
    }

    const codes = Array.from(enabledCodes);
    const series = [
      {
        name: "总UV",
        type: "line",
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3 },
        data: uvDates.map((d) => totalMap.get(d) || 0),
      },
      ...codes.map((code) => {
        const values = uvMap.get(code) || new Map<string, number>();
        return {
          name: nameByCode[code] || code,
          type: "line",
          smooth: true,
          showSymbol: false,
          data: uvDates.map((d) => values.get(d) || 0),
        };
      }),
    ];

    return {
      tooltip: { trigger: "axis" },
      legend: { type: "scroll", data: series.map((s) => s.name) },
      grid: { left: 40, right: 20, top: 50, bottom: 50 },
      xAxis: {
        type: "category",
        data: uvDates,
        axisLabel: { formatter: (v: string) => v.slice(5) },
      },
      yAxis: { type: "value" },
      series,
    } as const;
  }, [uvDates, uvTrend, uvByApp]);

  const delayMinutes = statusBoard?.DELAY_MINUTES ?? null;
  const statusFlag = statusBoard?.STATUS;

  const statusAlert = useMemo(() => {
    if (!statusFlag) return null;
    if (statusFlag === "NO_DATA") {
      return (
        <Alert
          type="info"
          showIcon
          message="暂无数据"
          description="未检测到上报数据，请确认SDK接入或待数据同步"
        />
      );
    }
    if (statusFlag === "LAG") {
      return (
        <Alert
          type="warning"
          showIcon
          message="数据延迟预警"
          description={`最新上报距离当前约 ${
            delayMinutes ?? "?"
          } 分钟，请检查上报链路`}
        />
      );
    }
    if ((statusBoard?.ERROR_NUM || 0) > 0) {
      return (
        <Alert
          type="warning"
          showIcon
          message="检测到错误上报"
          description={`今日错误数：${formatNumber(
            statusBoard?.ERROR_NUM
          )}，请关注近期错误列表`}
        />
      );
    }
    return null;
  }, [delayMinutes, statusBoard?.ERROR_NUM, statusFlag]);

  const metrics = statusBoard || {};

  const pvTitle = (
    <Space wrap align="center" size={8}>
      <span style={{ fontWeight: 600 }}>PV 趋势</span>
      <Segmented
        value={pvPreset}
        onChange={(val) => {
          const preset = val as Preset;
          setPvPreset(preset);
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setPvRange(r);
            loadPvData(r[0], r[1]);
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
            if (vals && vals[0] && vals[1]) {
              setPvRange([vals[0], vals[1]]);
              loadPvData(vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => loadPvData(pvRange[0], pvRange[1])}
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
          if (preset !== "custom") {
            const r = getPresetRange(preset);
            setUvRange(r);
            loadUvData(r[0], r[1]);
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
            if (vals && vals[0] && vals[1]) {
              setUvRange([vals[0], vals[1]]);
              loadUvData(vals[0], vals[1]);
            }
          }}
        />
      )}
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => loadUvData(uvRange[0], uvRange[1])}
      >
        刷新
      </Button>
    </Space>
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {statusAlert}

      <Row gutter={[16, 16]}>
        <Col xs={12} md={4}>
          <MetricCard
            title="应用数量"
            loading={boardLoading}
            value={formatNumber(metrics.APPLICATION_NUM)}
            footer={`历史总数: ${formatNumber(allBase?.APPLICATION_NUM)}`}
          />
        </Col>
        <Col xs={12} md={4}>
          <MetricCard
            title="日活跃用户（DAU）"
            loading={boardLoading}
            value={formatNumber(metrics.USER_COUNT)}
            footer={`历史用户数: ${formatNumber(allBase?.USER_COUNT)}`}
          />
        </Col>
        <Col xs={12} md={4}>
          <MetricCard
            title="日活设备"
            loading={boardLoading}
            value={formatNumber(metrics.DEVICE_NUM)}
            footer={`历史设备数: ${formatNumber(allBase?.DEVICE_NUM)}`}
          />
        </Col>
        <Col xs={12} md={4}>
          <MetricCard
            title="日活会话"
            loading={boardLoading}
            value={formatNumber(metrics.SESSION_UNM)}
            footer={`历史会话数: ${formatNumber(allBase?.SESSION_UNM)}`}
          />
        </Col>
        <Col xs={12} md={4}>
          <MetricCard
            title="日点击量"
            loading={boardLoading}
            value={formatNumber(metrics.CLICK_NUM)}
            footer={`历史点击量: ${formatNumber(allBase?.CLICK_NUM)}`}
          />
        </Col>
        <Col xs={12} md={4}>
          <MetricCard
            title="页面访问量（PV）"
            loading={boardLoading}
            value={formatNumber(metrics.PV_NUM)}
            footer={`历史浏览数: ${formatNumber(allBase?.PV_NUM)}`}
          />
        </Col>
      </Row>

      {delayMinutes != null && (
        <Tag
          color={
            statusFlag === "OK"
              ? "green"
              : statusFlag === "LAG"
              ? "orange"
              : "default"
          }
        >
          数据延迟 {delayMinutes} 分钟
        </Tag>
      )}

      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Card title={pvTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton active loading={pvLoading} paragraph={{ rows: 8 }}>
              <EChart option={pvOption} height={380} />
            </Skeleton>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title={uvTitle} bodyStyle={{ paddingTop: 0 }}>
            <Skeleton active loading={uvLoading} paragraph={{ rows: 8 }}>
              <EChart option={uvOption} height={380} />
            </Skeleton>
          </Card>
        </Col>
      </Row>

      <Card title="近期错误" bodyStyle={{ paddingTop: 0 }}>
        <RecentErrorsTable
          data={errorList}
          loading={errorListLoading}
          showApp
          onAppClick={(appCode) => {
            navigate(
              `/application/monitor?appCode=${encodeURIComponent(appCode)}`
            );
          }}
          fetchPayload={async (row) => {
            const id = row.ID;
            if (!id) return null;
            const resp = await client.get("/webTrack/errors/detail", {
              params: { id, appCode: row.APP_CODE },
            });
            if (resp.data?.code === 1000 && resp.data.data?.PAYLOAD) {
              return String(resp.data.data.PAYLOAD);
            }
            throw new Error(resp.data?.msg || "查询错误详情失败");
          }}
          tableProps={{ pagination: false }}
        />
      </Card>
    </Space>
  );
}

export default AnalysisDashboard;
