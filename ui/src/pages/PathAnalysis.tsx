import { useEffect, useMemo, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  Col,
  Input,
  InputNumber,
  Row,
  Segmented,
  Skeleton,
  Space,
  Typography,
  Select,
} from "antd";
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
  const [searchParams] = useSearchParams();
  const urlAppCode = searchParams.get("appCode");
  const navigate = useNavigate();

  const [appCode, setAppCode] = useState<string>(urlAppCode || "");
  const [appList, setAppList] = useState<any[]>([]);
  const [preset, setPreset] = useState<Preset>("7d");
  const [range, setRange] = useState<[DayjsValue, DayjsValue]>(
    getPresetRange("7d"),
  );
  const [startRoutePath, setStartRoutePath] = useState<string>("");
  const [minStayMs, setMinStayMs] = useState<number>(0);
  const [maxDepth, setMaxDepth] = useState<number>(6);
  const [ignorePatterns, setIgnorePatterns] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [nodes, setNodes] = useState<any[]>([]);
  const [links, setLinks] = useState<any[]>([]);
  const [topPaths, setTopPaths] = useState<any[]>([]);

  // New state for grouping
  const [groupBy, setGroupBy] = useState<string>("NONE"); // NONE, USER, PARAM
  const [groupParamName, setGroupParamName] = useState<string>("");

  const [funnelData, setFunnelData] = useState<any[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const resp = await client.get("/application/list");
        const list = resp?.data?.data || [];
        setAppList(list);
      } catch {}
    })();
  }, []);

  useEffect(() => {
    if (urlAppCode) {
      setAppCode(urlAppCode);
    } else if (appList.length > 0 && !appCode) {
      setAppCode(appList[0].appCode);
    }
  }, [urlAppCode, appList]);

  const currentAppName = useMemo(() => {
    const found = appList.find((a) => a.appCode === appCode);
    return found ? found.appName : appCode;
  }, [appCode, appList]);

  async function refresh() {
    if (!appCode) return;
    try {
      setLoading(true);
      const ignoreRoutePatterns = ignorePatterns
        ? ignorePatterns
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
        : [];

      const [sankeyResp, aggResp] = await Promise.all([
        client.post("/application/monitor/sessionPaths/sankey", {
          appCode,
          startDate: range[0].format("YYYY-MM-DD"),
          endDate: range[1].format("YYYY-MM-DD"),
          limitSessions: 2000,
          collapseConsecutiveDuplicates: true,
          minStayMs,
          maxDepth,
          ignoreRoutePatterns,
          startRoutePath,
        }),
        client.post("/application/monitor/sessionPaths/aggregate", {
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
          groupBy,
          groupParamName: groupBy === "PARAM" ? groupParamName : undefined,
        }),
      ]);

      setNodes(
        Array.isArray(sankeyResp?.data?.data?.nodes)
          ? sankeyResp.data.data.nodes
          : [],
      );
      setLinks(
        Array.isArray(sankeyResp?.data?.data?.links)
          ? sankeyResp.data.data.links
          : [],
      );

      const groups = Array.isArray(aggResp?.data?.data?.groups)
        ? aggResp.data.data.groups
        : [];
      const base = groups.length
        ? groups[0]?.topPaths
        : aggResp?.data?.data?.topPaths;
      const funnel = groups.length
        ? groups[0]?.funnel
        : aggResp?.data?.data?.funnel;

      setTopPaths(Array.isArray(base) ? base : []);
      setFunnelData(Array.isArray(funnel) ? funnel : []);
    } catch {
      setNodes([]);
      setLinks([]);
      setTopPaths([]);
      setFunnelData([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (appCode) refresh();
  }, [appCode]);

  const sankeyOption = useMemo(() => {
    return {
      tooltip: {
        trigger: "item",
        formatter: (params: any) => {
          if (params.dataType === "edge") {
            const s = params.data.source;
            const t = params.data.target;
            const sName =
              s.indexOf(":") > -1 ? s.substring(s.indexOf(":") + 1) : s;
            const tName =
              t.indexOf(":") > -1 ? t.substring(t.indexOf(":") + 1) : t;
            return `${sName} > ${tName} : ${params.data.value}`;
          } else {
            const name = params.name;
            const showName =
              name.indexOf(":") > -1
                ? name.substring(name.indexOf(":") + 1)
                : name;
            return `${showName} : ${params.value}`;
          }
        },
      },
      series: [
        {
          type: "sankey",
          emphasis: { focus: "adjacency" },
          data: nodes,
          links,
          nodeGap: 8,
          layoutIterations: 64,
          draggable: true,
          label: {
            formatter: (params: any) => {
              const name = params.name || "";
              const idx = name.indexOf(":");
              return idx > -1 ? name.substring(idx + 1) : name;
            },
          },
        },
      ],
    } as const;
  }, [nodes, links]);

  return (
    <div>
      <Card
        title={`用户流转路径（Sankey Diagram） - ${currentAppName}`}
        extra={
          <Space size={8}>
            <Space>
              <span>分组:</span>
              <Select
                value={groupBy}
                onChange={setGroupBy}
                style={{ width: 100 }}
                options={[
                  { label: "无", value: "NONE" },
                  { label: "用户", value: "USER" },
                  { label: "参数", value: "PARAM" },
                ]}
              />
              {groupBy === "PARAM" && (
                <Input
                  placeholder="参数名"
                  value={groupParamName}
                  onChange={(e) => setGroupParamName(e.target.value)}
                  style={{ width: 100 }}
                />
              )}
            </Space>
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
              style={{ width: 180 }}
            />
            <Input
              placeholder="忽略路由(正则/逗号分隔)"
              value={ignorePatterns}
              onChange={(e) => setIgnorePatterns(e.target.value)}
              style={{ width: 220 }}
            />
            <Space>
              <span>最小停留</span>
              <InputNumber
                min={0}
                value={minStayMs}
                onChange={(v) => setMinStayMs(Number(v || 0))}
                style={{ width: 70 }}
              />
            </Space>
            <Space>
              <span>深度</span>
              <InputNumber
                min={1}
                value={maxDepth}
                onChange={(v) => setMaxDepth(Number(v || 6))}
                style={{ width: 60 }}
              />
            </Space>
            <Button type="primary" onClick={refresh} loading={loading}>
              更新
            </Button>
          </Space>
        }
      >
        <div style={{ height: 480 }}>
          <Skeleton
            active
            loading={loading}
            paragraph={{ rows: 10 }}
            style={{ paddingTop: 12 }}
          >
            {nodes.length === 0 && links.length === 0 ? (
              <div
                style={{
                  height: 480,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexDirection: "column",
                }}
              >
                <Typography.Text type="secondary">暂无路径数据</Typography.Text>
                <Typography.Text type="secondary">
                  可尝试调整时间范围、起始页路由或忽略规则
                </Typography.Text>
                <Space style={{ marginTop: 8 }}>
                  <Button size="small" onClick={refresh}>
                    重新加载
                  </Button>
                  <Button
                    size="small"
                    type="link"
                    onClick={() => navigate("/application")}
                  >
                    前往应用管理
                  </Button>
                </Space>
              </div>
            ) : (
              <EChart option={sankeyOption} height={480} />
            )}
          </Skeleton>
        </div>
      </Card>

      <Card title="关键路径漏斗 (Funnel)" style={{ marginTop: 16 }}>
        <FunnelView data={funnelData} />
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

function FunnelView({ data }: { data: any[] }) {
  const steps = useMemo(() => {
    const map = new Map<number, any[]>();
    data.forEach((d) => {
      if (!map.has(d.STEP)) map.set(d.STEP, []);
      map.get(d.STEP)?.push(d);
    });
    return Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
  }, [data]);

  if (data.length === 0)
    return <Typography.Text type="secondary">暂无数据</Typography.Text>;

  return (
    <div
      style={{ display: "flex", overflowX: "auto", gap: 16, paddingBottom: 12 }}
    >
      {steps.map(([step, items]) => (
        <Card
          key={step}
          size="small"
          title={`第 ${step} 步`}
          style={{ minWidth: 200, flexShrink: 0 }}
        >
          {items.map((item, idx) => (
            <div
              key={idx}
              style={{
                display: "flex",
                justifyContent: "space-between",
                marginBottom: 4,
                fontSize: 12,
              }}
            >
              <Typography.Text
                ellipsis
                style={{ maxWidth: 120 }}
                title={item.ROUTE_PATH}
              >
                {item.ROUTE_PATH}
              </Typography.Text>
              <span style={{ color: "#888" }}>{item.COUNT}</span>
            </div>
          ))}
        </Card>
      ))}
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
            <div style={{ width: 80 }}>
              {typeof row.PCT === "number" ? row.PCT.toFixed(2) + "%" : "-"}
            </div>
            <Typography.Text
              ellipsis={{ tooltip: row.PATH }}
              style={{ maxWidth: 520 }}
            >
              {row.PATH}
            </Typography.Text>
          </div>
        ))
      )}
    </div>
  );
}
