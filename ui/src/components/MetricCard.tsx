import { Card, Statistic, Tag } from "antd";
import type { ReactNode } from "react";
import Sparkline from "./Sparkline";

type Props = {
  title: ReactNode;
  value: number;
  footer?: ReactNode;
  loading?: boolean;
  trendData?: number[];
  status?: "up" | "down" | "warn";
  statusText?: string;
};

function MetricCard({ title, value, footer, loading, trendData, status, statusText }: Props) {
  return (
    <Card loading={loading}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <Statistic title={title} value={value} />
        {status ? (
          <Tag color={status === "up" ? "green" : status === "down" ? "red" : "gold"}>
            {statusText || (status === "up" ? "正" : status === "down" ? "下降" : "预警")}
          </Tag>
        ) : null}
      </div>
      {trendData && trendData.length > 0 ? (
        <div style={{ marginTop: 8 }}>
          <Sparkline data={trendData} height={28} color="#1677ff" />
        </div>
      ) : null}
      {footer ? <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>{footer}</div> : null}
    </Card>
  );
}

export default MetricCard;
