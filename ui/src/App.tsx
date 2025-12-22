import { useLayoutEffect } from "react";
import { ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import Register from "./pages/Register";
import MainLayout from "./layouts/MainLayout";
import AnalysisDashboard from "./pages/AnalysisDashboard";
import ApplicationManagement from "./pages/ApplicationManagement";
import ApplicationMonitor from "./pages/ApplicationMonitor";
import UserManagement from "./pages/UserManagement";
import UserBehaviorAnalysis from "./pages/UserBehaviorAnalysis";
import ListingTableDemo from "./pages/ListingTableDemo";
import { WebTracingProvider } from "@web-tracing/react";

function Placeholder() {
  return <div>功能开发中</div>;
}

function App() {
  const options = {
    dsn: "/api/trackweb",
    appName: "埋点分析平台",
    appCode: "e3b5bf27919",
    debug: true,
    pv: true,
    performance: true,
    error: true,
    event: true,
    cacheMaxLength: 10,
    cacheWatingTime: 1000,
  };
  return (
    <WebTracingProvider options={options}>
      <ConfigProvider locale={zhCN}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<MainLayout />}>
            <Route index element={<Navigate to="/analysis" replace />} />
            <Route path="analysis" element={<AnalysisDashboard />} />
            <Route
              path="analysis/userTrack"
              element={<UserBehaviorAnalysis />}
            />
            <Route path="application" element={<ApplicationManagement />} />
            <Route
              path="application/monitor"
              element={<ApplicationMonitor />}
            />
            <Route path="user" element={<UserManagement />} />
            <Route path="listing/table" element={<ListingTableDemo />} />
            <Route path="*" element={<Placeholder />} />
          </Route>
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </ConfigProvider>
    </WebTracingProvider>
  );
}

export default App;
