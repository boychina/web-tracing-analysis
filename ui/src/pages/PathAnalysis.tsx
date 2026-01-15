import { useEffect, useMemo, useState } from "react";
import { Button, Card, Col, Input, InputNumber, Row, Segmented, Skeleton, Space, Typography } from "antd";
import EChart from "../components/EChart";
import client from "../api/client";
import dayjs from "dayjs";

type DayjsValue = any;
type Preset = "7d" | "30d" | "90d" | "custom";

function getPresetRange(preset: Preset): [DayjsValue, DayjsValue] {
  const end = dayjs();
  const start =
    preset === "7d"
      ? end.subtract(7, "day")
      : preset === "30d"
      ? end.subtract(30, "day")
      : preset === "90d"
      ? end.subtract(90, "day")
      : end.subtract(7, "day");
  return [start, end];
}

export default function PathAnalysis() {
  const [appCode, setAppCode] = useState<string>("");
  const [preset, setPreset] = useState<Preset>("7d");
  const [range, setRange] = useState<[DayjsValue, DayjsValue]>(getPresetRange("7d"));
  const [startRoutePath, setStartRoutePath] = useState<string>("/login");
  const [minStayMs, setMinStayMs] = useState<number>(0);
  const [maxDepth, setMaxDepth] = useState<number>(6);
  const [ignorePatterns, setIgnorePatterns] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [nodes, setNodes] = useState<any[]>([]);
  const [links, setLinks] = useState<any[]>([]);
  const [topPaths, setTopPaths] = useState<any[]>([]);

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
      const ignoreRoutePatterns = ignorePatterns
        ? ignorePatterns.split(",").map((s) => s.trim()).filter(Boolean)
        : [];
      const sankeyResp = await client.post("/application/monitor/sessionPaths/sankey", {
        appCode,
        startDate: range[0].format("YYYY-MM-DD"),
        endDate: range[1].format("YYYY-MM-DD"),
        limitSessions: 2000,
        collapseConsecutiveDuplicates: true,
        minStayMs,
        maxDepth,
        ignoreRoutePatterns,
        startRoutePath,
      });
      const aggResp = await client.post("/application/monitor/sessionPaths/aggregate", {
        appCode,
        startDate: range[0].format("YYYY-MM-DD"),
        endDate: range[1].format("YYYY-MM-DD"),
        limitSessions: 2000,
        topN: 20,
        collapseConsecutiveDuplicates: true,
        minStayMs,
        maxDepth,
        ignoreRoutePatterns,
        startRoutePath,
      });
      setNodes(Array.isArray(sankeyResp?.data?.data?.nodes) ? sankeyResp.data.data.nodes : []);
      setLinks(Array.isArray(sankeyResp?.data?.data?.links) ? sankeyResp.data.data.links : []);
      const groups = Array.isArray(aggResp?.data?.data?.groups) ? aggResp.data.data.groups : [];
      const base = groups.length ? groups[0]?.topPaths : aggResp?.data?.data?.topPaths;
      setTopPaths(Array.isArray(base) ? base : []);
    } catch {
      setNodes([]);
      setLinks([]);
      setTopPaths([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (appCode) refresh();
  }, [appCode]);

  const sankeyOption = useMemo(() => {
    return {
      tooltip: { trigger: "item" },
      series: [
        {
          type: "sankey",
          emphasis: { focus: "adjacency" },
          data: nodes,
          links,
          nodeGap: 8,
          layoutIterations: 64,
          draggable: true,
        },
      ],
    } as const;
  }, [nodes, links]);

  return (
    <div>
      <Card
        title="用户流转路径（Sankey Diagram）"
        extra={
          <Space size={8}>
            <Segmented
              value={preset}
              options={[
                { label: "近7天", value: "7d" },
                { label: "近1个月", value: "30d" },
                { label: "近3个月", value: "90d" },
              ]}
              onChange={(v) => {
                const p = v as Preset;
                setPreset(p);
                setRange(getPresetRange(p));
              }}
            />
            <Input
              placeholder="起始页路由，如 /login"
              value={startRoutePath}
              onChange={(e) => setStartRoutePath(e.target.value)}
              style={{ width: 220 }}
            />
            <Input
              placeholder="忽略路由(正则/逗号分隔)"
              value={ignorePatterns}
              onChange={(e) => setIgnorePatterns(e.target.value)}
              style={{ width: 320 }}
            />
            <Space>
              <span>最小停留(ms)</span>
              <InputNumber min={0} value={minStayMs} onChange={(v) => setMinStayMs(Number(v || 0))} />
            </Space>
            <Space>
              <span>最大深度</span>
              <InputNumber min={1} value={maxDepth} onChange={(v) => setMaxDepth(Number(v || 6))} />
            </Space>
            <Button type="primary" onClick={refresh} loading={loading}>
              更新生成分析
            </Button>
          </Space>
        }
      >
        <Skeleton active loading={loading} paragraph={{ rows: 10 }}>
          <EChart option={sankeyOption} height={480} />
        </Skeleton>
      </Card>

      <Card title="Top 路径模式（聚类）" style={{ marginTop: 16 }}>
        <Row gutter={16}>
          <Col span={12}>
            <TableLike data={topPaths.slice(0, 10)} />
          </Col>
          <Col span={12}>
            <TableLike data={topPaths.slice(10, 20)} />
          </Col>
        </Row>
      </Card>
    </div>
  );
}

function TableLike({ data }: { data: any[] }) {
  return (
    <div>
      {data.length === 0 ? (
        <Typography.Text type="secondary">暂无数据</Typography.Text>
      ) : (
        data.map((row, idx) => (
          <div key={idx} style={{ display: "flex", gap: 12, padding: "6px 0" }}>
            <div style={{ width: 60 }}>{row.COUNT}</div>
            <div style={{ width: 80 }}>{typeof row.PCT === "number" ? row.PCT.toFixed(2) + "%" : "-"}</div>
            <Typography.Text ellipsis={{ tooltip: row.PATH }} style={{ maxWidth: 520 }}>
              {row.PATH}
            </Typography.Text>
          </div>
        ))
      )}
    </div>
  );
}
