import { Card, Col, Row, Skeleton, Statistic } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts";
import client from "../api/client";

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
  if (value == null) return 0;
  return value;
}

function computeDateRange(days: number) {
  const now = new Date();
  const past = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  const format = (d: Date) => {
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, "0");
    const day = `${d.getDate()}`.padStart(2, "0");
    return `${y}-${m}-${day}`;
  };
  return {
    startDate: format(past),
    endDate: format(now)
  };
}

function AnalysisDashboard() {
  const [dailyBase, setDailyBase] = useState<BaseInfoItem | null>(null);
  const [allBase, setAllBase] = useState<BaseInfoItem | null>(null);
  const [series, setSeries] = useState<DailySeriesItem[]>([]);
  const [loading, setLoading] = useState(false);
  const chartRef = useRef<HTMLDivElement | null>(null);
  const chartInstanceRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!chartRef.current) return;
    const instance = echarts.init(chartRef.current);
    chartInstanceRef.current = instance;
    const handleResize = () => {
      instance.resize();
    };
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      instance.dispose();
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    async function fetchData() {
      setLoading(true);
      try {
        const [dailyResp, allResp] = await Promise.all([
          client.get("/webTrack/queryDailyBaseInfo"),
          client.get("/webTrack/queryAllBaseInfo")
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
        const range = computeDateRange(7);
        const dailyInfoResp = await client.post("/webTrack/queryDailyInfo", {
          startDate: range.startDate,
          endDate: range.endDate
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
    const daysSet = new Set<string>();
    const codesSet = new Set<string>();
    const nameByCode: Record<string, string> = {};
    series.forEach((item) => {
      if (item.DATETIME) {
        daysSet.add(item.DATETIME);
      }
      if (item.APP_CODE) {
        codesSet.add(item.APP_CODE);
        if (item.APP_NAME) {
          nameByCode[item.APP_CODE] = item.APP_NAME;
        }
      }
    });
    const days = Array.from(daysSet).sort();
    const codes = Array.from(codesSet);
    const seriesList = codes.map((code) => {
      const name = nameByCode[code] || code;
      const data = days.map((day) => {
        const row = series.find(
          (it) => it.APP_CODE === code && it.DATETIME === day
        );
        return row && typeof row.PV_NUM === "number" ? row.PV_NUM : 0;
      });
      return {
        name,
        type: "line",
        smooth: true,
        data
      };
    });
    return {
      title: {
        text: "一周访问趋势图",
        textStyle: { color: "#28333e", fontSize: 14 }
      },
      tooltip: { trigger: "axis" },
      legend: {
        data: codes.map((code) => nameByCode[code] || code)
      },
      xAxis: {
        type: "category",
        data: days
      },
      yAxis: {
        type: "value"
      },
      toolbox: {
        feature: {
          saveAsImage: {}
        }
      },
      series: seriesList
    };
  }, [series]);

  useEffect(() => {
    if (!chartInstanceRef.current) return;
    chartInstanceRef.current.setOption(chartOption);
  }, [chartOption]);

  const total = allBase || {};
  const today = dailyBase || {};

  return (
    <div>
      <Skeleton loading={loading && !dailyBase} active>
        <Row gutter={[16, 16]}>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="应用数量"
                value={formatNumber(today.APPLICATION_NUM)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史总数: {formatNumber(total.APPLICATION_NUM)}
              </div>
            </Card>
          </Col>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="日活跃用户"
                value={formatNumber(today.USER_COUNT)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史用户数: {formatNumber(total.USER_COUNT)}
              </div>
            </Card>
          </Col>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="日活跃设备"
                value={formatNumber(today.DEVICE_NUM)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史设备数: {formatNumber(total.DEVICE_NUM)}
              </div>
            </Card>
          </Col>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="日活跃会话"
                value={formatNumber(today.SESSION_UNM)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史会话数: {formatNumber(total.SESSION_UNM)}
              </div>
            </Card>
          </Col>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="日点击量"
                value={formatNumber(today.CLICK_NUM)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史点击量: {formatNumber(total.CLICK_NUM)}
              </div>
            </Card>
          </Col>
          <Col xs={12} md={4}>
            <Card>
              <Statistic
                title="日浏览PV"
                value={formatNumber(today.PV_NUM)}
              />
              <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
                历史浏览数: {formatNumber(total.PV_NUM)}
              </div>
            </Card>
          </Col>
        </Row>
      </Skeleton>
      <Card style={{ marginTop: 16 }}>
        <div
          ref={chartRef}
          style={{ width: "100%", height: 400, minHeight: 400 }}
        />
      </Card>
    </div>
  );
}

export default AnalysisDashboard;

