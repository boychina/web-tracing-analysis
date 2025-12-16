import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Card, Form, Input, message } from "antd";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import client from "../api/client";

type RegisterFormValues = {
  username: string;
  password: string;
};

function Register() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const onFinish = async (values: RegisterFormValues) => {
    setLoading(true);
    try {
      const resp = await client.post("/register", values);
      const data = resp.data;
      if (data.code === 200) {
        message.success("注册成功，请登录");
        navigate("/login", { replace: true });
      } else {
        message.error(data.msg || "注册失败");
      }
    } catch {
      message.error("网络错误");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <Card className="login-card">
        <h1 className="login-title">注册账号</h1>
        <Form<RegisterFormValues> name="register" size="large" onFinish={onFinish}>
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
              autoComplete="new-password"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              注册
            </Button>
          </Form.Item>
          <div style={{ textAlign: "right" }}>
            <Link to="/login">已有账号？去登录</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}

export default Register;
