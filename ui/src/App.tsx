import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import MainLayout from "./layouts/MainLayout";
import ApplicationManagement from "./pages/ApplicationManagement";
import ApplicationMonitor from "./pages/ApplicationMonitor";
import UserManagement from "./pages/UserManagement";

function Dashboard() {
  return <div>监控大屏</div>;
}

function Placeholder() {
  return <div>功能开发中</div>;
}

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/analysis" replace />} />
          <Route path="analysis" element={<Dashboard />} />
          <Route path="application" element={<ApplicationManagement />} />
          <Route path="application/monitor" element={<ApplicationMonitor />} />
          <Route path="user" element={<UserManagement />} />
          <Route path="*" element={<Placeholder />} />
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </ConfigProvider>
  );
}

export default App;
