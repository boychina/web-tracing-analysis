import { Button, Layout, Menu, Spin } from "antd";
import type { MenuProps } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import client from "../api/client";

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

function MainLayout() {
  const [loading, setLoading] = useState(true);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [user, setUser] = useState<UserInfo | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    async function init() {
      try {
        const meResp = await client.get("/user/me");
        if (!active) return;
        if (meResp.data.code !== 1000) {
          navigate("/login", { replace: true });
          return;
        }
        setUser(meResp.data.data as UserInfo);
        setMenuItems(buildMenuItems(STATIC_MENU));
      } finally {
        if (active) setLoading(false);
      }
    }
    init();
    return () => {
      active = false;
    };
  }, [navigate]);

  const selectedKeys = useMemo(() => {
    const path = location.pathname || "/";
    return [path];
  }, [location.pathname]);

  const headerTitle = useMemo(() => {
    const pathname = location.pathname || "/";
    const routeMap: Array<{ path: string; title: string }> = [
      { path: "/analysis/userTrack", title: "用户行为分析" },
      { path: "/analysis", title: "分析页" },
      { path: "/application/monitor", title: "应用监控" },
      { path: "/application", title: "应用管理" },
      { path: "/user", title: "用户管理" },
      { path: "/listing/table", title: "列表" },
    ];
    const found = routeMap.find((r) => pathname.startsWith(r.path));
    return found ? found.title : "管理控制台";
  }, [location.pathname]);

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
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div>{headerTitle}</div>
            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={() => setRefreshKey((v) => v + 1)}
            >
              刷新
            </Button>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <span>{user.username}</span>
            <a
              onClick={async () => {
                try {
                  await client.post("/logout");
                } finally {
                  navigate("/login", { replace: true });
                }
              }}
            >
              退出登录
            </a>
          </div>
        </Layout.Header>
        <Layout.Content style={{ background: "#f5f5f5" }}>
          <div
            style={{
              padding: "24px",
              height: "calc(100vh - 64px)",
              overflow: "auto",
            }}
          >
            <Outlet key={`${location.pathname}:${refreshKey}`} />
          </div>
        </Layout.Content>
      </Layout>
    </Layout>
  );
}

export default MainLayout;
