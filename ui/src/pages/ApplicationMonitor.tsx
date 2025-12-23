import { useEffect, useState } from "react";
import { Button, Card, Col, Row, Select, message } from "antd";
import dayjs from "dayjs";
import { useNavigate, useSearchParams } from "react-router-dom";
import client from "../api/client";
import EChart from "../components/EChart";
import MetricCard from "../components/MetricCard";
import RecentErrorsTable, {
  type RecentErrorItem,
} from "../components/RecentErrorsTable";

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
  const [uvList, setUvList] = useState<UvItem[]>([]);
  const [pagePv, setPagePv] = useState<PagePvItem[]>([]);
  const [errorList, setErrorList] = useState<ErrorItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [tableLoading, setTableLoading] = useState(false);
  const [errorPageNo, setErrorPageNo] = useState(1);
  const [errorPageSize, setErrorPageSize] = useState(10);
  const [errorTotal, setErrorTotal] = useState(0);
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
          refresh(code);
        }
      }
    } catch {
      message.error("加载应用列表失败");
    }
  }

  async function loadErrors(appCode: string, pageNo: number, pageSize: number) {
    try {
      setTableLoading(true);
      const errorResp = await client.get("/application/monitor/errors/recent", {
        params: { appCode, pageNo, pageSize },
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

  async function refresh(appCode: string) {
    setLoading(true);
    try {
      const dates = buildRangeDates(7);
      const [dailyResp, allResp, dailyPvResp, dailyUvResp, weeklyPageResp] =
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
          client.post("/application/monitor/dailyUV", {
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
        setUvList([]);
      }

      if (weeklyPageResp.data && weeklyPageResp.data.code === 1000) {
        let items = weeklyPageResp.data.data as PagePvItem[];
        items = items.slice(0, 10);
        setPagePv(items);
      } else {
        setPagePv([]);
      }

      setErrorPageNo(1);
      await loadErrors(appCode, 1, errorPageSize);
    } catch {
      message.error("加载监控数据失败");
    } finally {
      setLoading(false);
    }
  }

  const pvOption = {
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

  const uvOption = {
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      data: uvList.map((d) => d.DATETIME.slice(5)),
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
            title="日活跃用户（DAU）"
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
            title="页面访问量（PV）"
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
          <Card title="PV 趋势" loading={loading}>
            <EChart option={pvOption} height={360} />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="UV 趋势" loading={loading}>
            <EChart option={uvOption} height={360} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="本周各页面 PV" loading={loading}>
            <EChart option={pagePvOption} height={360} />
          </Card>
        </Col>
      </Row>

      <Card title="近期错误" style={{ marginTop: 16 }}>
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
              const nextNo = p.current || 1;
              const nextSize = p.pageSize || errorPageSize;
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
