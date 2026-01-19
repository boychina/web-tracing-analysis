import axios, { AxiosError } from "axios";
import { message } from "antd";

const client = axios.create({
  baseURL: "/api",
  withCredentials: true,
  timeout: 10000,
});

let redirectingToLogin = false;
let refreshing = false;
let pendingQueue: Array<() => void> = [];

client.interceptors.request.use(
  (config) => {
    try {
      let deviceId = localStorage.getItem("DEVICE_ID");
      if (!deviceId) {
        deviceId = Math.random().toString(36).slice(2) + Date.now().toString(36);
        localStorage.setItem("DEVICE_ID", deviceId);
      }
      const token = localStorage.getItem("AUTH_TOKEN");
      if (token) {
        config.headers = config.headers || {};
        (config.headers as any).Authorization = `Bearer ${token}`;
      }
      (config.headers as any)["X-Device-Id"] = deviceId!;
    } catch {}
    (config as any).headers = {
      ...(config.headers || {}),
      "X-Requested-With": "XMLHttpRequest",
      Accept: "application/json",
    };
    if (
      (config.method || "get").toLowerCase() === "post" &&
      !(config.headers as any)["Content-Type"]
    ) {
      (config.headers as any)["Content-Type"] = "application/json";
    }
    return config;
  },
  (error) => {
    message.error("请求发送失败");
    return Promise.reject(error);
  }
);

client.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError) => {
    const status = error.response?.status;
    if (status === 401) {
      const original = error.config!;
      if ((original as any)._retry) {
        message.warning("登录已过期，请重新登录");
        if (!redirectingToLogin && window.location.pathname !== "/login") {
          redirectingToLogin = true;
          window.location.href = "/login";
        }
        return Promise.reject(error);
      }
      (original as any)._retry = true;
      if (refreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push(() => {
            client(original!).then(resolve).catch(reject);
          });
        });
      }
      refreshing = true;
      return client
        .post("/auth/refresh", {}, { withCredentials: true })
        .then((resp) => {
          const at = (resp.data as any)?.data?.accessToken;
          if (at) {
            localStorage.setItem("AUTH_TOKEN", at);
            pendingQueue.forEach((fn) => fn());
            pendingQueue = [];
            return client(original!);
          } else {
            throw new Error("no access token");
          }
        })
        .catch(() => {
          message.warning("登录已过期，请重新登录");
          if (!redirectingToLogin && window.location.pathname !== "/login") {
            redirectingToLogin = true;
            window.location.href = "/login";
          }
          return Promise.reject(error);
        })
        .finally(() => {
          refreshing = false;
        });
    } else if (status === 403) {
      message.error("无权限访问");
    } else if (status === 404) {
      message.error("接口不存在");
    } else if (status && status >= 500) {
      message.error("服务器异常，请稍后再试");
    } else if (error.code === "ECONNABORTED") {
      message.error("请求超时，请检查网络");
    } else if (!error.response) {
      message.error("网络错误或服务器不可用");
    } else {
      const msg =
        (error.response.data as any)?.msg ||
        (error.response.data as any)?.message ||
        "请求异常";
      message.error(msg);
    }
    return Promise.reject(error);
  }
);

export default client;
