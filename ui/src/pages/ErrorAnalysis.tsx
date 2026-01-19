import { useEffect, useMemo, useState } from "react";
import { Button, Card, Col, Input, Row, Segmented, Select, Skeleton, Space } from "antd";
import EChart from "../components/EChart";
import client from "../api/client";
import RecentErrorsTable, { RecentErrorItem } from "../components/RecentErrorsTable";
import dayjs from "dayjs";

type DayjsValue = any;
type Preset = "24h" | "7d" | "30d";

function getPresetRange(preset: Preset): [DayjsValue, DayjsValue] {
  const end = dayjs();
  const start =
    preset === "24h" ? end.subtract(1, "day") : preset === "7d" ? end.subtract(7, "day") : end.subtract(30, "day");
  return [start, end];
}

export default function ErrorAnalysis() {
  const [appCode, setAppCode] = useState<string>("");
  const [preset, setPreset] = useState<Preset>("24h");
  const [range, setRange] = useState<[DayjsValue, DayjsValue]>(getPresetRange("24h"));
  const [severity, setSeverity] = useState<string | undefined>(undefined);
  const [errorCode, setErrorCode] = useState<string | undefined>(undefined);
  const [requestUri, setRequestUri] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [errorTrendData, setErrorTrendData] = useState<any[]>([]);
  const [errorPieData, setErrorPieData] = useState<any[]>([]);
  const [errors, setErrors] = useState<RecentErrorItem[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const resp = await client.get("/application/list");
        const list = resp?.data?.data || [];
        if (list.length > 0) setAppCode(list[0].appCode);
      } catch {}
    })();
  }, []);

  async function refresh() {
    if (!appCode) return;
    try {
      setLoading(true);
      // Trend by hour (reuse daily with short range; server returns daily, we simply render as columns)
      const dailyTrend = await client.get("/application/monitor/weeklyError", {
        params: {
          appCode,
          days: 1,
          startDate: range[0].format("YYYY-MM-DD"),
          endDate: range[1].format("YYYY-MM-DD"),
        },
      });
      setErrorTrendData(Array.isArray(dailyTrend?.data?.data) ? dailyTrend.data.data : []);
      // Pie: group by URI from recent errors
      const recent = await client.get("/application/monitor/errors/recent", {
        params: { appCode, limit: 200 },
      });
      const rows = Array.isArray(recent?.data?.data) ? recent.data.data : [];
      setErrors(rows as RecentErrorItem[]);
      const uriCount: Record<string, number> = {};
      rows.forEach((r: any) => {
        const uri = r.REQUEST_URI || "";
        if (!uri) return;
        uriCount[uri] = (uriCount[uri] || 0) + 1;
      });
      const pie = Object.entries(uriCount)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 6)
        .map(([name, value]) => ({ name, value }));
      setErrorPieData(pie);
    } catch {
      setErrorTrendData([]);
      setErrorPieData([]);
      setErrors([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (appCode) refresh();
  }, [appCode]);

  const errorPieOption = useMemo(() => {
    return {
      tooltip: { trigger: "item" },
      series: [{ type: "pie", radius: ["40%", "70%"], data: errorPieData }],
    } as const;
  }, [errorPieData]);

  const errorTrendOption = useMemo(() => {
    return {
      grid: { left: 40, right: 20, top: 30, bottom: 40 },
      xAxis: { type: "category", data: errorTrendData.map((d) => d.DATETIME || d.time || "") },
      yAxis: { type: "value" },
      series: [{ type: "bar", data: errorTrendData.map((d) => d.ERROR_NUM || d.count || 0), barMaxWidth: 28 }],
    } as const;
  }, [errorTrendData]);

  return (
    <div>
      <Card
        title="异常监控 - 智能识别分析"
        extra={
          <Space size={8}>
            <Segmented
              value={preset}
              options={[
                { label: "近24小时", value: "24h" },
                { label: "近7天", value: "7d" },
                { label: "近1个月", value: "30d" },
              ]}
              onChange={(v) => {
                const p = v as Preset;
                setPreset(p);
                setRange(getPresetRange(p));
              }}
            />
            <Select
              placeholder="严重程度"
              allowClear
              value={severity}
              onChange={setSeverity}
              options={[
                { label: "CRITICAL", value: "CRITICAL" },
                { label: "HIGH", value: "HIGH" },
                { label: "MEDIUM", value: "MEDIUM" },
                { label: "INFO", value: "INFO" },
              ]}
              style={{ width: 140 }}
            />
            <Input
              placeholder="错误代码"
              allowClear
              value={errorCode}
              onChange={(e) => setErrorCode(e.target.value || undefined)}
              style={{ width: 160 }}
            />
            <Input
              placeholder="页面URI"
              allowClear
              value={requestUri}
              onChange={(e) => setRequestUri(e.target.value || undefined)}
              style={{ width: 220 }}
            />
            <Button type="primary" onClick={refresh} loading={loading}>
              刷新视图
            </Button>
          </Space>
        }
      >
        <Skeleton active loading={loading} paragraph={{ rows: 8 }}>
          <Row gutter={16}>
            <Col span={10}>
              <EChart option={errorPieOption} height={280} />
            </Col>
            <Col span={14}>
              <EChart option={errorTrendOption} height={280} />
            </Col>
          </Row>
        </Skeleton>
      </Card>

      <Card title="智能聚类异常列表" style={{ marginTop: 16 }}>
        <RecentErrorsTable
          data={errors}
          loading={loading}
          tableProps={{
            pagination: { pageSize: 10 },
          }}
          fetchPayload={async (row) => {
            try {
              const resp = await client.get(
                "/application/monitor/errors/detail",
                {
                  params: { appCode, id: row.ID },
                },
              );
              if (resp.data?.code === 1000 && resp.data?.data?.PAYLOAD) {
                return String(resp.data.data.PAYLOAD);
              }
              return null;
            } catch {
              return null;
            }
          }}
        />
      </Card>
    </div>
  );
}
