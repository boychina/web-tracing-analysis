import { Card, Statistic } from "antd";
import type { ReactNode } from "react";

type Props = {
  title: ReactNode;
  value: number;
  footer?: ReactNode;
  loading?: boolean;
};

function MetricCard({ title, value, footer, loading }: Props) {
  return (
    <Card loading={loading}>
      <Statistic title={title} value={value} />
      {footer ? (
        <div style={{ marginTop: 8, fontSize: 12, color: "#999" }}>
          {footer}
        </div>
      ) : null}
    </Card>
  );
}

export default MetricCard;

