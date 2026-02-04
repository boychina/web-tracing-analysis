import { Breadcrumb } from "antd";
import { Link, useLocation, useSearchParams } from "react-router-dom";

type Crumb = { title: string; to?: string };

function buildCrumbs(pathname: string, appCode?: string | null): Crumb[] {
  if (pathname === "/") {
    return [];
  }
  if (pathname.startsWith("/analysis/userTrack")) {
    return [
      { title: "分析页", to: "/analysis" },
      { title: "用户行为分析" },
    ];
  }
  if (pathname.startsWith("/analysis")) {
    return [{ title: "分析页" }];
  }
  if (pathname.startsWith("/application/monitor/errors")) {
    const to =
      appCode && appCode.trim()
        ? `/application/monitor?appCode=${encodeURIComponent(appCode)}`
        : "/application/monitor";
    return [{ title: "应用监控", to }, { title: "异常分析" }];
  }
  if (pathname.startsWith("/application/monitor/paths")) {
    const to =
      appCode && appCode.trim()
        ? `/application/monitor?appCode=${encodeURIComponent(appCode)}`
        : "/application/monitor";
    return [{ title: "应用监控", to }, { title: "路径分析" }];
  }
  if (pathname.startsWith("/application/monitor")) {
    return [{ title: "应用监控" }];
  }
  if (pathname.startsWith("/application")) {
    return [{ title: "应用管理" }];
  }
  if (pathname.startsWith("/user")) {
    return [{ title: "用户管理" }];
  }
  return [{ title: "管理控制台" }];
}

export default function Breadcrumbs() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const appCode = searchParams.get("appCode");
  const crumbs = buildCrumbs(location.pathname, appCode);
  return (
    <div>
      <Breadcrumb
        items={crumbs.map((c) =>
          c.to
            ? { title: <Link to={c.to}>{c.title}</Link> }
            : { title: c.title }
        )}
      />
    </div>
  );
}
