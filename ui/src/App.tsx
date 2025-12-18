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
import {
  init,
  beforePushEventList,
  beforeSendData,
  afterSendData,
  setUserUuid,
  getBaseInfo,
  getFirstScreen,
  getIPs,
} from "@web-tracing/core";

function start() {
  init({
    dsn: "/api/trackweb",
    appName: "React应用",
    appCode: "ca2c50faf31",
    debug: true,
    pv: true,
    // performance: true,
    // error: true,
    event: true,
    // localization: true,
    cacheMaxLength: 10,
    cacheWatingTime: 1000,
    userUuid: "init_userUuid",

    scopeError: true,

    // tracesSampleRate: 0.5,

    // ignoreErrors: ['111', /^promise/, /.*split is not .* function/],
    // ignoreRequest: ['111', /normal/],

    beforePushEventList(data) {
      // console.log('beforePushEventList-data', data)
      return data;
    },
    beforeSendData(data) {
      // console.log('beforeSendData-data', data)
      // return { xx: 2123 }
      // 返回false代表sdk不再发送
      // return false
      return data;
    },
    afterSendData(data) {
      // console.log('afterSendData-data', data)
    },
  });
}

function Placeholder() {
  return <div>功能开发中</div>;
}

function App() {
  useLayoutEffect(() => {
    console.log(">>>>>>> 启动埋点");
    start();
  }, []);
  return (
    <ConfigProvider locale={zhCN}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/analysis" replace />} />
          <Route path="analysis" element={<AnalysisDashboard />} />
          <Route path="analysis/userTrack" element={<UserBehaviorAnalysis />} />
          <Route path="application" element={<ApplicationManagement />} />
          <Route path="application/monitor" element={<ApplicationMonitor />} />
          <Route path="user" element={<UserManagement />} />
          <Route path="listing/table" element={<ListingTableDemo />} />
          <Route path="*" element={<Placeholder />} />
        </Route>
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </ConfigProvider>
  );
}

export default App;
