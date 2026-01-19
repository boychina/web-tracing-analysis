import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Card, Form, Input, message } from "antd";
import { Link, useLocation } from "react-router-dom";
import { useState } from "react";
import client from "../api/client";

type LoginFormValues = {
  username: string;
  password: string;
};

function Login() {
  const [loading, setLoading] = useState(false);
  const location = useLocation();

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const resp = await client.post("/login", values);
      const data = resp.data;
      if (data.code === 1000) {
        const at = data?.data?.accessToken;
        if (at) {
          localStorage.setItem("AUTH_TOKEN", at);
        }
        message.success("登录成功");
        const params = new URLSearchParams(location.search);
        const qRedirect = params.get("redirect");
        let target = qRedirect || "/";
        if (!qRedirect) {
          try {
            const s = sessionStorage.getItem("REDIRECT_TARGET");
            if (s) {
              target = s;
              sessionStorage.removeItem("REDIRECT_TARGET");
            }
          } catch {}
        }
        window.location.href = target;
      } else {
        message.error(data.msg || "登录失败");
      }
    } catch (e) {
      message.error("网络错误");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <Card className="login-card">
        <h1 className="login-title">Web Tracing Analysis</h1>
        <Form<LoginFormValues> name="login" size="large" onFinish={onFinish}>
          <Form.Item
            name="username"
            rules={[{ required: true, message: "请输入账号" }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="账户"
              autoComplete="username"
            />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              autoComplete="current-password"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: "right" }}>
            <Link to="/register">没有账号？去注册</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}

export default Login;
