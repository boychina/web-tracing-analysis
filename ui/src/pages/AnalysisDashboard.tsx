import { Card, Col, Row, Skeleton } from "antd";
import { useEffect, useMemo, useState } from "react";
import client from "../api/client";
import EChart from "../components/EChart";
import MetricCard from "../components/MetricCard";

type BaseInfoItem = {
  DAY_TIME?: string;
  APPLICATION_NUM?: number;
  USER_COUNT?: number;
  DEVICE_NUM?: number;
  SESSION_UNM?: number;
  CLICK_NUM?: number;
  PV_NUM?: number;
  ERROR_NUM?: number;
};

type DailySeriesItem = {
  APP_CODE?: string;
  APP_NAME?: string;
  DATETIME?: string;
  PV_NUM?: number;
};

function formatNumber(value?: number) {
  return typeof value === "number" ? value : 0;
}

function buildRangeDates(n: number) {
  const result: string[] = [];
  const today = new Date();
  for (let i = 0; i < n; i += 1) {
    const d = new Date(today.getTime() - i * 24 * 60 * 60 * 1000);
    const y = d.getFullYear();
    const m = `0${d.getMonth() + 1}`.slice(-2);
    const day = `0${d.getDate()}`.slice(-2);
    result.push(`${y}-${m}-${day}`);
  }
  return result.reverse();
}

function AnalysisDashboard() {
  const [dailyBase, setDailyBase] = useState<BaseInfoItem | null>(null);
  const [allBase, setAllBase] = useState<BaseInfoItem | null>(null);
  const [series, setSeries] = useState<DailySeriesItem[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;
    async function fetchData() {
      setLoading(true);
      try {
        const [dailyResp, allResp] = await Promise.all([
          client.get("/webTrack/queryDailyBaseInfo"),
          client.get("/webTrack/queryAllBaseInfo"),
        ]);
        if (
          mounted &&
          dailyResp.data &&
          dailyResp.data.code === 1000 &&
          Array.isArray(dailyResp.data.data) &&
          dailyResp.data.data.length > 0
        ) {
          setDailyBase(dailyResp.data.data[0] as BaseInfoItem);
        }
        if (
          mounted &&
          allResp.data &&
          allResp.data.code === 1000 &&
          Array.isArray(allResp.data.data) &&
          allResp.data.data.length > 0
        ) {
          setAllBase(allResp.data.data[0] as BaseInfoItem);
        }
        const dates = buildRangeDates(30);
        const dailyInfoResp = await client.post("/webTrack/queryDailyInfo", {
          startDate: dates[0],
          endDate: dates[dates.length - 1],
        });
        if (
          mounted &&
          dailyInfoResp.data &&
          dailyInfoResp.data.code === 1000 &&
          Array.isArray(dailyInfoResp.data.data)
        ) {
          setSeries(dailyInfoResp.data.data as DailySeriesItem[]);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    }
    fetchData();
    return () => {
      mounted = false;
    };
  }, []);

  const chartOption = useMemo(() => {
    const dates = buildRangeDates(30);
    const nameByCode: Record<string, string> = {};
    const pvByCodeAndDate = new Map<string, Map<string, number>>();
    const codes: string[] = [];

    for (const item of series) {
      const code = item.APP_CODE;
      const date = item.DATETIME;
      if (!code || !date) continue;
      if (!pvByCodeAndDate.has(code)) {
        pvByCodeAndDate.set(code, new Map());
        codes.push(code);
      }
      if (item.APP_NAME) {
        nameByCode[code] = item.APP_NAME;
      }
      const pv = typeof item.PV_NUM === "number" ? item.PV_NUM : 0;
      pvByCodeAndDate.get(code)!.set(date, pv);
    }

    const seriesList = codes.map((code) => {
      const name = nameByCode[code] || code;
      const map = pvByCodeAndDate.get(code) || new Map<string, number>();
      return {
        name,
        type: "line",
        smooth: true,
        data: dates.map((d) => map.get(d) || 0),
      };
    });

    return {
      tooltip: { trigger: "axis" },
      legend: { data: seriesList.map((s) => s.name) },
      grid: { left: 40, right: 20, top: 50, bottom: 50 },
      xAxis: {
        type: "category",
        data: dates,
        axisLabel: { formatter: (value: string) => value.slice(5) },
      },
      yAxis: { type: "value" },
      toolbox: { feature: { saveAsImage: {} } },
      series: seriesList,
    };
  }, [series]);

  const total = allBase || {};
  const today = dailyBase || {};

  return (
    <div>
      <Skeleton loading={loading && !dailyBase} active>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={4}>
            <MetricCard
              title="应用数量"
              value={formatNumber(today.APPLICATION_NUM)}
              footer={`历史总数: ${formatNumber(total.APPLICATION_NUM)}`}
            />
          </Col>
          <Col xs={12} md={4}>
            <MetricCard
              title="日活跃用户"
              value={formatNumber(today.USER_COUNT)}
              footer={`历史用户数: ${formatNumber(total.USER_COUNT)}`}
            />
          </Col>
          <Col xs={12} md={4}>
            <MetricCard
              title="日活跃设备"
              value={formatNumber(today.DEVICE_NUM)}
              footer={`历史设备数: ${formatNumber(total.DEVICE_NUM)}`}
            />
          </Col>
          <Col xs={12} md={4}>
            <MetricCard
              title="日活跃会话"
              value={formatNumber(today.SESSION_UNM)}
              footer={`历史会话数: ${formatNumber(total.SESSION_UNM)}`}
            />
          </Col>
          <Col xs={12} md={4}>
            <MetricCard
              title="日点击量"
              value={formatNumber(today.CLICK_NUM)}
              footer={`历史点击量: ${formatNumber(total.CLICK_NUM)}`}
            />
          </Col>
          <Col xs={12} md={4}>
            <MetricCard
              title="日浏览PV"
              value={formatNumber(today.PV_NUM)}
              footer={`历史浏览数: ${formatNumber(total.PV_NUM)}`}
            />
          </Col>
        </Row>
      </Skeleton>
      <Card title="应用访问趋势" style={{ marginTop: 16 }} loading={loading}>
        <EChart option={chartOption} height={420} />
      </Card>
    </div>
  );
}

export default AnalysisDashboard;
