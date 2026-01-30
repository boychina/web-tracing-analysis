import { useEffect, useRef } from "react";
import * as echarts from "echarts";

type Props = {
  option: echarts.EChartsCoreOption;
  height?: number;
  onChartClick?: (params: any) => void;
};

function EChart({ option, height = 360, onChartClick }: Props) {
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
    const observer =
      typeof ResizeObserver !== "undefined"
        ? new ResizeObserver(() => {
            instance.resize();
          })
        : null;
    if (observer) observer.observe(containerRef.current);
    return () => {
      window.removeEventListener("resize", handleResize);
      if (observer) observer.disconnect();
      instance.dispose();
      instanceRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!instanceRef.current) return;
    instanceRef.current.setOption(option, true);
    instanceRef.current.resize();
  }, [option]);

  useEffect(() => {
    const instance = instanceRef.current;
    if (!instance) return;
    if (!onChartClick) return;
    const handler = (params: any) => {
      onChartClick(params);
    };
    instance.on("click", handler);
    return () => {
      instance.off("click", handler);
    };
  }, [onChartClick]);

  return <div ref={containerRef} style={{ width: "100%", height }} />;
}

export default EChart;
