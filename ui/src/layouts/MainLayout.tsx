import { Layout, Menu, Spin } from "antd";
import type { MenuProps } from "antd";
import { useEffect, useMemo, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import client from "../api/client";

type RawMenuItem = {
  id: number | string;
  title: string;
  icon?: string;
  type: number;
  href?: string;
  children?: RawMenuItem[];
};

type MenuItem = Required<MenuProps>["items"][number];

type UserInfo = {
  id: number;
  username: string;
  role: string;
};

function mapHrefToPath(href?: string) {
  if (!href) return "/";
  if (href.includes("view/analysis/index.html")) return "/analysis";
  if (href.includes("view/application/monitor.html")) return "/application/monitor";
  if (href.includes("view/application/index.html")) return "/application";
  if (href.includes("view/user/index.html")) return "/user";
  if (href.includes("view/analysis/userTrack.html")) return "/analysis/userTrack";
  if (href.includes("view/listing/table.html")) return "/listing/table";
  return "/";
}

function buildMenuItems(data: RawMenuItem[]): MenuItem[] {
  return data.map((item) => {
    if (item.type === 0) {
      return {
        key: String(item.id),
        label: item.title,
        children: (item.children || []).map((c) => {
          const path = mapHrefToPath(c.href);
          return {
            key: path,
            label: <Link to={path}>{c.title}</Link>
          };
        })
      };
    }
    const path = mapHrefToPath(item.href);
    return {
      key: path,
      label: <Link to={path}>{item.title}</Link>
    };
  });
}

function MainLayout() {
  const [loading, setLoading] = useState(true);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [user, setUser] = useState<UserInfo | null>(null);
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
        const menuResp = await client.get("/getMenuList");
        if (!active) return;
        if (menuResp.data && menuResp.data.code === 200) {
          const rawList = menuResp.data.data as RawMenuItem[];
          setMenuItems(buildMenuItems(rawList));
        } else if (Array.isArray(menuResp.data)) {
          const rawList = menuResp.data as RawMenuItem[];
          setMenuItems(buildMenuItems(rawList));
        } else if (menuResp.data && Array.isArray(menuResp.data.data)) {
          const rawList = menuResp.data.data as RawMenuItem[];
          setMenuItems(buildMenuItems(rawList));
        }
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
            height: "100vh"
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
            alignItems: "center"
          }}
        >
          WebTracing
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
            justifyContent: "space-between"
          }}
        >
          <div>管理控制台</div>
          <div>{user.username}</div>
        </Layout.Header>
        <Layout.Content style={{ padding: 24, background: "#f5f5f5" }}>
          <div
            style={{
              height: "100%",
              minHeight: "calc(100vh - 112px)"
            }}
          >
            <Outlet />
          </div>
        </Layout.Content>
      </Layout>
    </Layout>
  );
}

export default MainLayout;

