import type { CSSProperties } from "react";

type Props = {
  data: number[];
  height?: number;
  color?: string;
};

export default function Sparkline({ data, height = 36, color = "#1677ff" }: Props) {
  const max = Math.max(...data.map((v) => Number(v || 0)), 1);
  const bars = data.slice(-12);
  const gap = 2;
  const barWidth = Math.max(2, Math.floor((100 - (bars.length - 1) * gap) / bars.length));
  return (
    <div style={{ display: "flex", alignItems: "end", height, gap }}>
      {bars.map((v, i) => {
        const h = Math.round((Number(v || 0) / max) * height);
        const style: CSSProperties = {
          width: `${barWidth}%`,
          height: Math.max(2, h),
          background: color,
          borderRadius: 2,
          opacity: 0.8,
        };
        return <div key={i} style={style} />;
      })}
    </div>
  );
}
