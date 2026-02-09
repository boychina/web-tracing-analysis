import { Button, Layout, Menu, Spin, Dropdown, Tabs } from "antd";
import type { MenuProps } from "antd";
import { ReloadOutlined, UserOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useOutlet } from "react-router-dom";
import client from "../api/client";
import Breadcrumbs from "../components/Breadcrumbs";
import MyDevicesModal from "../components/MyDevicesModal";
import "./style.less";

type RawMenuItem = {
  id: number | string;
  title: string;
  icon?: string;
  type: number;
  path?: string;
  children?: RawMenuItem[];
};

type MenuItem = Required<MenuProps>["items"][number];

type UserInfo = {
  id: number;
  username: string;
  role: string;
};

type VisitedTag = {
  key: string;
  path: string;
  label: string;
};

type CachedView = {
  key: string;
  element: React.ReactNode;
};

const STATIC_MENU: RawMenuItem[] = [
  {
    icon: "layui-icon layui-icon-console",
    id: "10",
    title: "分析页",
    type: 1,
    path: "/analysis",
  },
  {
    icon: "layui-icon layui-icon-console",
    id: "11",
    title: "应用监控",
    type: 1,
    path: "/application/monitor",
  },
  {
    icon: "layui-icon layui-icon-console",
    id: "12",
    title: "应用管理",
    type: 1,
    path: "/application",
  },
  {
    icon: "layui-icon layui-icon-console",
    id: "13",
    title: "用户管理",
    type: 1,
    path: "/user",
  },
];

function buildMenuItems(data: RawMenuItem[]): MenuItem[] {
  return data.map((item) => {
    if (item.type === 0) {
      return {
        key: String(item.id),
        label: item.title,
        children: (item.children || []).map((c) => {
          return {
            key: c.path || "",
            label: <Link to={c.path || ""}>{c.title}</Link>,
          };
        }),
      };
    }
    const path = item.path || "";
    return {
      key: path,
      label: <Link to={path}>{item.title}</Link>,
    };
  });
}

function buildTagLabel(
  pathname: string,
  appCode?: string | null,
  appNameMap?: Record<string, string>,
): string | null {
  if (pathname === "/") return null;
  if (pathname.startsWith("/analysis/userTrack")) return "用户行为分析";
  if (pathname.startsWith("/analysis")) return "分析页";
  if (pathname.startsWith("/application/monitor/errors")) return "异常分析";
  if (pathname.startsWith("/application/monitor/paths")) return "路径分析";
  if (pathname.startsWith("/application/monitor")) {
    const name = appCode ? appNameMap?.[appCode] : "";
    return name || appCode || "应用监控";
  }
  if (pathname.startsWith("/application")) return "应用管理";
  if (pathname.startsWith("/user")) return "用户管理";
  return null;
}

function readCachedAppCode() {
  try {
    const v = localStorage.getItem("web-tracing-ui.applicationMonitor.appCode");
    return v ? v.trim() : "";
  } catch {
    return "";
  }
}

function MainLayout() {
  const [loading, setLoading] = useState(true);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();
  const [devicesOpen, setDevicesOpen] = useState(false);
  const outlet = useOutlet();
  const [appNameMap, setAppNameMap] = useState<Record<string, string>>({});
  const [visitedTags, setVisitedTags] = useState<VisitedTag[]>(() => {
    try {
      const raw = sessionStorage.getItem("VISITED_TAGS");
      if (!raw) return [];
      const parsed = JSON.parse(raw) as VisitedTag[];
      if (Array.isArray(parsed)) {
        return parsed.filter((t) => t && t.key && t.path && t.label);
      }
      return [];
    } catch {
      return [];
    }
  });
  const [cachedViews, setCachedViews] = useState<CachedView[]>([]);

  const resolvedLocation = useMemo(() => {
    if (!location.pathname.startsWith("/application/monitor")) {
      return {
        path: `${location.pathname}${location.search || ""}`,
        appCode: null,
      };
    }
    const params = new URLSearchParams(location.search);
    const appCode = params.get("appCode")?.trim();
    if (appCode) {
      return { path: `${location.pathname}${location.search || ""}`, appCode };
    }
    const cached = readCachedAppCode();
    if (cached) {
      const next = `${location.pathname}?appCode=${encodeURIComponent(cached)}`;
      return { path: next, appCode: cached };
    }
    return {
      path: `${location.pathname}${location.search || ""}`,
      appCode: null,
    };
  }, [location.pathname, location.search]);

  useEffect(() => {
    let active = true;
    async function init() {
      try {
        const meResp = await client.get("/user/me");
        if (!active) return;
        if (meResp.data.code !== 1000) {
          try {
            sessionStorage.removeItem("VISITED_TAGS");
          } catch {}
          setUser(null);
          setMenuItems([]);
          navigate("/login", { replace: true });
          return;
        }
        setUser(meResp.data.data as UserInfo);
        setMenuItems(buildMenuItems(STATIC_MENU));
      } catch {
        if (!active) return;
        try {
          sessionStorage.removeItem("VISITED_TAGS");
        } catch {}
        setUser(null);
        setMenuItems([]);
        navigate("/login", { replace: true });
      } finally {
        if (active) setLoading(false);
      }
    }
    init();
    return () => {
      active = false;
    };
  }, [navigate]);

  useEffect(() => {
    let active = true;
    async function loadApps() {
      try {
        const resp = await client.get("/application/list");
        if (!active) return;
        if (resp.data?.code === 1000 && Array.isArray(resp.data.data)) {
          const map: Record<string, string> = {};
          for (const item of resp.data.data as any[]) {
            if (item?.appCode) map[item.appCode] = item.appName || item.appCode;
          }
          setAppNameMap(map);
        }
      } catch {}
    }
    loadApps();
    return () => {
      active = false;
    };
  }, []);

  const selectedKeys = useMemo(() => {
    const path = location.pathname || "/";
    return [path];
  }, [location.pathname]);

  const activeKey = resolvedLocation.path;

  useEffect(() => {
    if (
      location.pathname.startsWith("/application/monitor") &&
      resolvedLocation.path !== `${location.pathname}${location.search || ""}`
    ) {
      navigate(resolvedLocation.path, { replace: true });
      return;
    }
    const label = buildTagLabel(
      location.pathname,
      resolvedLocation.appCode,
      appNameMap,
    );
    if (!label) return;
    setVisitedTags((prev) => {
      const exists = prev.find((t) => t.key === activeKey);
      if (exists) {
        if (exists.label !== label) {
          const next = prev.map((t) =>
            t.key === activeKey ? { ...t, label } : t,
          );
          try {
            sessionStorage.setItem("VISITED_TAGS", JSON.stringify(next));
          } catch {}
          return next;
        }
        return prev;
      }
      const next = [...prev, { key: activeKey, path: activeKey, label }];
      try {
        sessionStorage.setItem("VISITED_TAGS", JSON.stringify(next));
      } catch {}
      return next;
    });
  }, [
    activeKey,
    appNameMap,
    location.pathname,
    location.search,
    navigate,
    resolvedLocation,
  ]);

  useEffect(() => {
    setCachedViews((prev) => {
      if (prev.some((item) => item.key === activeKey)) return prev;
      return [...prev, { key: activeKey, element: outlet }];
    });
  }, [activeKey, outlet, refreshKey]);

  const persistTags = (next: VisitedTag[]) => {
    try {
      sessionStorage.setItem("VISITED_TAGS", JSON.stringify(next));
    } catch {}
  };

  const closeTag = (targetKey: string) => {
    setVisitedTags((prev) => {
      const idx = prev.findIndex((t) => t.key === targetKey);
      const next = prev.filter((t) => t.key !== targetKey);
      persistTags(next);
      setCachedViews((views) => views.filter((v) => v.key !== targetKey));
      if (targetKey === activeKey) {
        if (next.length > 0) {
          const nextIndex = idx > 0 ? idx - 1 : 0;
          const nextPath = next[nextIndex]?.path || "/analysis";
          navigate(nextPath);
        } else {
          navigate("/analysis");
        }
      }
      return next;
    });
  };

  const closeOthers = () => {
    setVisitedTags((prev) => {
      const current = prev.find((t) => t.key === activeKey);
      const next = current ? [current] : [];
      persistTags(next);
      setCachedViews((views) =>
        current ? views.filter((v) => v.key === current.key) : [],
      );
      return next;
    });
  };

  const closeAll = () => {
    setVisitedTags([]);
    persistTags([]);
    setCachedViews([]);
    navigate("/analysis");
  };

  const closeRight = () => {
    setVisitedTags((prev) => {
      const idx = prev.findIndex((t) => t.key === activeKey);
      if (idx === -1) return prev;
      const next = prev.slice(0, idx + 1);
      persistTags(next);
      const keepKeys = new Set(next.map((t) => t.key));
      setCachedViews((views) => views.filter((v) => keepKeys.has(v.key)));
      return next;
    });
  };

  const closeLeft = () => {
    setVisitedTags((prev) => {
      const idx = prev.findIndex((t) => t.key === activeKey);
      if (idx === -1) return prev;
      const next = prev.slice(idx);
      persistTags(next);
      const keepKeys = new Set(next.map((t) => t.key));
      setCachedViews((views) => views.filter((v) => keepKeys.has(v.key)));
      return next;
    });
  };

  if (loading) {
    return (
      <Layout style={{ minHeight: "100vh" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            height: "100vh",
          }}
        >
          <Spin size="large" />
        </div>
      </Layout>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Layout.Sider theme="dark">
        <div
          style={{
            height: 48,
            margin: 16,
            color: "#fff",
            fontSize: 18,
            fontWeight: 600,
            display: "flex",
            alignItems: "center",
          }}
        >
          埋点监控
        </div>
        <Menu
          mode="inline"
          theme="dark"
          items={menuItems}
          selectedKeys={selectedKeys}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header
          style={{
            background: "#fff",
            padding: "0 24px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <div
            style={{ display: "flex", alignItems: "center", gap: 8, flex: 1 }}
          >
            <Breadcrumbs />
            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={() => {
                setCachedViews((prev) =>
                  prev.filter((item) => item.key !== activeKey),
                );
                setRefreshKey((v) => v + 1);
              }}
            >
              刷新
            </Button>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Dropdown
              menu={{
                items: [
                  {
                    key: "me-devices",
                    label: "我的设备",
                    onClick: () => setDevicesOpen(true),
                  },
                  {
                    key: "logout",
                    label: "退出登录",
                    onClick: async () => {
                      try {
                        await client.post("/auth/logout");
                        try {
                          localStorage.removeItem("AUTH_TOKEN");
                        } catch {}
                        try {
                          sessionStorage.removeItem("VISITED_TAGS");
                        } catch {}
                        try {
                          const target =
                            window.location.pathname +
                            window.location.search +
                            window.location.hash;
                          sessionStorage.setItem("REDIRECT_TARGET", target);
                        } catch {}
                      } finally {
                        const target =
                          window.location.pathname +
                          window.location.search +
                          window.location.hash;
                        navigate(
                          `/login?redirect=${encodeURIComponent(target)}`,
                          { replace: true },
                        );
                      }
                    },
                  },
                ],
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <UserOutlined />
                <a>{user.username}</a>
              </div>
            </Dropdown>
          </div>
        </Layout.Header>
        <div
          style={{
            background: "#fff",
            padding: "0 16px",
            borderBottom: "1px solid #f0f0f0",
            display: "flex",
            alignItems: "center",
            gap: 8,
          }}
        >
          <Tabs
            type="editable-card"
            hideAdd
            size="small"
            activeKey={activeKey}
            items={visitedTags.map((t) => ({
              key: t.key,
              label: t.label,
            }))}
            onChange={(key) => {
              const target = visitedTags.find((t) => t.key === key);
              navigate(target?.path || String(key));
            }}
            onEdit={(key, action) => {
              if (action === "remove") closeTag(String(key));
            }}
            style={{ flex: 1 }}
          />
        </div>
        <Layout.Content style={{ background: "#f5f5f5" }}>
          <div
            style={{
              padding: "24px",
              height: "calc(100vh - 104px)",
              overflow: "auto",
            }}
          >
            {cachedViews.map((item) => (
              <div
                key={item.key}
                style={{ display: item.key === activeKey ? "block" : "none" }}
              >
                {item.element}
              </div>
            ))}
          </div>
        </Layout.Content>
        <MyDevicesModal
          open={devicesOpen}
          onClose={() => setDevicesOpen(false)}
        />
      </Layout>
    </Layout>
  );
}

export default MainLayout;
