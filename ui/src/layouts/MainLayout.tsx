import { Button, Layout, Menu, Spin, Dropdown } from "antd";
import type { MenuProps } from "antd";
import { ReloadOutlined, UserOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import client from "../api/client";
import Breadcrumbs from "../components/Breadcrumbs";
import MyDevicesModal from "../components/MyDevicesModal";

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
  const [devicesOpen, setDevicesOpen] = useState(false);

  useEffect(() => {
    let active = true;
    async function init() {
      try {
        const meResp = await client.get("/user/me");
        if (!active) return;
        if (meResp.data.code !== 1000) {
          setUser(null);
          setMenuItems([]);
          navigate("/login", { replace: true });
          return;
        }
        setUser(meResp.data.data as UserInfo);
        setMenuItems(buildMenuItems(STATIC_MENU));
      } catch {
        if (!active) return;
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

  const selectedKeys = useMemo(() => {
    const path = location.pathname || "/";
    return [path];
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
          <div
            style={{ display: "flex", alignItems: "center", gap: 8, flex: 1 }}
          >
            <Breadcrumbs />
            <Button
              type="text"
              icon={<ReloadOutlined />}
              onClick={() => setRefreshKey((v) => v + 1)}
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
        <MyDevicesModal
          open={devicesOpen}
          onClose={() => setDevicesOpen(false)}
        />
      </Layout>
    </Layout>
  );
}

export default MainLayout;
