import { useEffect, useRef } from "react";
import * as echarts from "echarts";

type Props = {
  option: echarts.EChartsCoreOption;
  height?: number;
};

function EChart({ option, height = 360 }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const instanceRef = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const instance = echarts.init(containerRef.current);
    instanceRef.current = instance;
    const handleResize = () => {
      instance.resize();
    };
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      instance.dispose();
      instanceRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!instanceRef.current) return;
    instanceRef.current.setOption(option, true);
    instanceRef.current.resize();
  }, [option]);

  return <div ref={containerRef} style={{ width: "100%", height }} />;
}

export default EChart;

