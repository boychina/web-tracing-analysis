import { useEffect, useState } from "react";
import { Button, Card, Col, Row, Select, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router-dom";
import client from "../api/client";

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
      const [dailyResp, allResp, dailyPvResp, weeklyPageResp] = await Promise.all([
        client.get("/application/monitor/dailyBase", {
          params: { appCode }
        }),
        client.get("/application/monitor/allBase", {
          params: { appCode }
        }),
        client.post("/application/monitor/dailyPV", {
          appCode,
          startDate: dates[0],
          endDate: dates[dates.length - 1]
        }),
        client.get("/application/monitor/weeklyPagePV", {
          params: { appCode }
        })
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
            PV_NUM: found ? found.PV_NUM : 0
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

  const dailyColumns: ColumnsType<DailyItem> = [
    { title: "日期", dataIndex: "DATETIME" },
    { title: "PV", dataIndex: "PV_NUM" }
  ];

  const pageColumns: ColumnsType<PagePvItem> = [
    { title: "页面地址", dataIndex: "PAGE_URL", ellipsis: true },
    { title: "PV", dataIndex: "PV_NUM", width: 120 }
  ];

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
                value: a.appCode
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
          <Card loading={loading}>
            <Typography.Text>日活跃用户</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.USER_COUNT : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史用户数: {allBase ? allBase.USER_COUNT : 0}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={4}>
          <Card loading={loading}>
            <Typography.Text>日活跃设备</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.DEVICE_NUM : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史设备数: {allBase ? allBase.DEVICE_NUM : 0}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={4}>
          <Card loading={loading}>
            <Typography.Text>日活跃会话</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.SESSION_UNM : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史会话数: {allBase ? allBase.SESSION_UNM : 0}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={4}>
          <Card loading={loading}>
            <Typography.Text>日点击量</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.CLICK_NUM : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史点击量: {allBase ? allBase.CLICK_NUM : 0}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={4}>
          <Card loading={loading}>
            <Typography.Text>日浏览PV</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.PV_NUM : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史浏览数: {allBase ? allBase.PV_NUM : 0}
            </div>
          </Card>
        </Col>
        <Col xs={24} md={4}>
          <Card loading={loading}>
            <Typography.Text>日上报错误</Typography.Text>
            <div style={{ fontSize: 24, marginTop: 8 }}>
              {dailyBase ? dailyBase.ERROR_NUM || 0 : 0}
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
              历史错误数: {allBase ? allBase.ERROR_NUM || 0 : 0}
            </div>
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="本周访问趋势" loading={loading}>
            <Table<DailyItem>
              rowKey="DATETIME"
              columns={dailyColumns}
              dataSource={dailyList}
              pagination={false}
              size="small"
            />
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="本周各页面 PV" loading={loading}>
            <Table<PagePvItem>
              rowKey="PAGE_URL"
              columns={pageColumns}
              dataSource={pagePv}
              pagination={false}
              size="small"
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default ApplicationMonitor;

