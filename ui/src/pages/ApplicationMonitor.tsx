import { useEffect, useState } from "react";
import { Button, Card, Col, Row, Select, message } from "antd";
import { useNavigate } from "react-router-dom";
import client from "../api/client";
import EChart from "../components/EChart";
import MetricCard from "../components/MetricCard";

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

type PagePvItem = {
  PAGE_URL: string;
  PV_NUM: number;
};

function ellipsis(value: string, maxLen: number) {
  if (value.length <= maxLen) return value;
  return `${value.slice(0, maxLen)}...`;
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

function ApplicationMonitor() {
  const [apps, setApps] = useState<AppItem[]>([]);
  const [currentApp, setCurrentApp] = useState<string>();
  const [dailyBase, setDailyBase] = useState<DailyBase | null>(null);
  const [allBase, setAllBase] = useState<AllBase | null>(null);
  const [dailyList, setDailyList] = useState<DailyItem[]>([]);
  const [pagePv, setPagePv] = useState<PagePvItem[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

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
          const code = list[0].appCode;
          setCurrentApp(code);
          refresh(code);
        }
      }
    } catch {
      message.error("加载应用列表失败");
    }
  }

  async function refresh(appCode: string) {
    setLoading(true);
    try {
      const dates = buildRangeDates(7);
      const [dailyResp, allResp, dailyPvResp, weeklyPageResp] =
        await Promise.all([
          client.get("/application/monitor/dailyBase", {
            params: { appCode },
          }),
          client.get("/application/monitor/allBase", {
            params: { appCode },
          }),
          client.post("/application/monitor/dailyPV", {
            appCode,
            startDate: dates[0],
            endDate: dates[dates.length - 1],
          }),
          client.get("/application/monitor/weeklyPagePV", {
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
        setDailyList([]);
      }

      if (weeklyPageResp.data && weeklyPageResp.data.code === 1000) {
        let items = weeklyPageResp.data.data as PagePvItem[];
        items = items.slice(0, 10);
        setPagePv(items);
      } else {
        setPagePv([]);
      }
    } catch {
      message.error("加载监控数据失败");
    } finally {
      setLoading(false);
    }
  }

  const trendOption = {
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      data: dailyList.map((d) => d.DATETIME.slice(5)),
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

  const pagePvOption = {
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
                setCurrentApp(val);
                refresh(val);
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
            loading={loading}
            title="日活跃用户"
            value={dailyBase ? dailyBase.USER_COUNT : 0}
            footer={`历史用户数: ${allBase ? allBase.USER_COUNT : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={loading}
            title="日活跃设备"
            value={dailyBase ? dailyBase.DEVICE_NUM : 0}
            footer={`历史设备数: ${allBase ? allBase.DEVICE_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={loading}
            title="日活跃会话"
            value={dailyBase ? dailyBase.SESSION_UNM : 0}
            footer={`历史会话数: ${allBase ? allBase.SESSION_UNM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={loading}
            title="日点击量"
            value={dailyBase ? dailyBase.CLICK_NUM : 0}
            footer={`历史点击量: ${allBase ? allBase.CLICK_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={loading}
            title="日浏览PV"
            value={dailyBase ? dailyBase.PV_NUM : 0}
            footer={`历史浏览数: ${allBase ? allBase.PV_NUM : 0}`}
          />
        </Col>
        <Col xs={24} md={4}>
          <MetricCard
            loading={loading}
            title="日上报错误"
            value={dailyBase ? dailyBase.ERROR_NUM || 0 : 0}
            footer={`历史错误数: ${allBase ? allBase.ERROR_NUM || 0 : 0}`}
          />
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="本周访问趋势" loading={loading}>
            <EChart option={trendOption} height={360} />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="本周各页面 PV" loading={loading}>
            <EChart option={pagePvOption} height={360} />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default ApplicationMonitor;
