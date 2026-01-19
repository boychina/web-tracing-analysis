import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message
} from "antd";
import type { ColumnsType } from "antd/es/table";
import client from "../api/client";

type UserRow = {
  id: number;
  username: string;
  role: string;
  createdAt: string;
};

type MeInfo = {
  id: number;
  username: string;
  role: string;
};

type UserFormValues = {
  username: string;
  password?: string;
  role?: string;
};

function roleLabel(code: string) {
  if (code === "SUPER_ADMIN") return "超级管理员";
  if (code === "ADMIN") return "管理员";
  return "用户";
}

function UserManagement() {
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<UserRow[]>([]);
  const [me, setMe] = useState<MeInfo | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<UserRow | null>(null);
  const [form] = Form.useForm<UserFormValues>();
  const [devices, setDevices] = useState<any[]>([]);

  const isSuper = me?.role === "SUPER_ADMIN";
  const isAdmin = me?.role === "ADMIN";

  useEffect(() => {
    init();
  }, []);

  async function init() {
    setLoading(true);
    try {
      const meResp = await client.get("/user/me");
      if (meResp.data && meResp.data.code === 1000) {
        setMe(meResp.data.data as MeInfo);
      }
      await loadUsers();
      await loadDevices();
    } catch {
      message.error("加载用户数据失败");
    } finally {
      setLoading(false);
    }
  }

  async function loadUsers() {
    const resp = await client.get("/user/list");
    if (resp.data && resp.data.code === 1000) {
      setList(resp.data.data as UserRow[]);
    }
  }
  async function loadDevices() {
    try {
      const resp = await client.get("/auth/devices");
      if (resp.data && resp.data.code === 1000) {
        setDevices(resp.data.data || []);
      }
    } catch {}
  }
  async function kickDevice(tokenId: number) {
    try {
      const resp = await client.post("/auth/kick", { tokenId });
      if (resp.data && resp.data.code === 1000) {
        message.success("已踢出设备");
        await loadDevices();
      } else {
        message.error(resp.data?.msg || "操作失败");
      }
    } catch {
      message.error("操作失败");
    }
  }
  async function forceLogoutUser(id: number) {
    try {
      const resp = await client.post("/auth/forceLogout", { userId: id });
      if (resp.data && resp.data.code === 1000) {
        message.success("已强制下线该用户");
        await loadDevices();
      } else {
        message.error(resp.data?.msg || "操作失败");
      }
    } catch {
      message.error("操作失败");
    }
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  }

  function openEdit(row: UserRow) {
    setEditing(row);
    form.setFieldsValue({
      username: row.username,
      password: "",
      role: row.role
    });
    setModalOpen(true);
  }

  async function handleSubmit() {
    try {
      const values = await form.validateFields();
      const name = values.username.trim();
      if (name.length < 2) {
        message.error("用户名长度不合法");
        return;
      }
      if (!editing) {
        const payload: any = {
          username: name,
          password: values.password || "",
          role: values.role || "USER"
        };
        const resp = await client.post("/user/create", payload);
        if (resp.data && resp.data.code === 1000) {
          message.success("新增成功");
          setModalOpen(false);
          await loadUsers();
        } else {
          message.error(resp.data?.msg || "新增失败");
        }
      } else {
        const payload: any = {
          id: editing.id,
          username: name
        };
        if (values.password) payload.password = values.password;
        if (isSuper && values.role) payload.role = values.role;
        const resp = await client.post("/user/update", payload);
        if (resp.data && resp.data.code === 1000) {
          message.success("编辑成功");
          setModalOpen(false);
          setEditing(null);
          await loadUsers();
        } else {
          message.error(resp.data?.msg || "编辑失败");
        }
      }
    } catch (e) {
      if ((e as any).errorFields) return;
    }
  }

  async function setRole(id: number, role: "ADMIN" | "USER") {
    try {
      const resp = await client.post("/user/setRole", { id, role });
      if (resp.data && resp.data.code === 1000) {
        message.success("角色已更新");
        await loadUsers();
      } else {
        message.error(resp.data?.msg || "更新失败");
      }
    } catch {
      message.error("更新失败");
    }
  }

  async function deleteUser(id: number, username: string) {
    Modal.confirm({
      title: "确认删除该用户？",
      onOk: async () => {
        try {
          const resp = await client.post("/user/delete", { id });
          if (resp.data && resp.data.code === 1000) {
            message.success("删除成功");
            await loadUsers();
          } else {
            message.error(resp.data?.msg || "删除失败");
          }
        } catch {
          message.error("删除失败");
        }
      }
    });
  }

  const columns: ColumnsType<UserRow> = useMemo(
    () => [
      { title: "ID", dataIndex: "id", width: 80 },
      { title: "用户名", dataIndex: "username" },
      {
        title: "角色",
        dataIndex: "role",
        render: (value: string) => <Tag>{roleLabel(value)}</Tag>
      },
      { title: "创建时间", dataIndex: "createdAt" },
      {
        title: "操作",
        width: 260,
        render: (_, record) => {
          const self = me;
          const buttons: JSX.Element[] = [];
          const lowerName = record.username.toLowerCase();
          if (isSuper && lowerName !== "admin") {
            if (record.role !== "ADMIN") {
              buttons.push(
                <Button
                  key="set-admin"
                  type="link"
                  size="small"
                  onClick={() => setRole(record.id, "ADMIN")}
                >
                  设为管理员
                </Button>
              );
            }
            if (record.role !== "USER") {
              buttons.push(
                <Button
                  key="set-user"
                  type="link"
                  size="small"
                  onClick={() => setRole(record.id, "USER")}
                >
                  设为用户
                </Button>
              );
            }
          }
          let canEdit = true;
          if (lowerName === "admin" && !isSuper) {
            canEdit = false;
          }
          if (me && me.role === "USER") {
            canEdit = !!(
              self &&
              self.username &&
              self.username.toLowerCase() === lowerName
            );
          }
          if (canEdit) {
            buttons.push(
              <Button
                key="edit"
                type="link"
                size="small"
                onClick={() => openEdit(record)}
              >
                编辑
              </Button>
            );
          }
          let canDelete = lowerName !== "admin";
          if (
            isAdmin &&
            self &&
            self.username &&
            self.username.toLowerCase() === lowerName
          ) {
            canDelete = false;
          }
          if (me && me.role === "USER") {
            canDelete = false;
          }
          if (canDelete) {
            buttons.push(
              <Button
                key="delete"
                type="link"
                danger
                size="small"
                onClick={() => deleteUser(record.id, record.username)}
              >
                删除
              </Button>
            );
          }
          if ((isAdmin || isSuper) && record.username.toLowerCase() !== "admin") {
            buttons.push(
              <Button
                key="force-logout"
                type="link"
                size="small"
                onClick={() => forceLogoutUser(record.id)}
              >
                强制下线
              </Button>
            );
          }
          if (!buttons.length) return null;
          return <Space>{buttons}</Space>;
        }
      }
    ],
    [isAdmin, isSuper, me]
  );

  return (
    <Card
      title="用户管理"
      extra={
        (isSuper || isAdmin) && (
          <Button type="primary" onClick={openCreate}>
            新增用户
          </Button>
        )
      }
    >
      <Table<UserRow>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={list}
        pagination={{ pageSize: 10 }}
      />
      <Card title="当前活跃设备" style={{ marginTop: 16 }}>
        <Table
          rowKey="id"
          dataSource={devices}
          columns={[
            { title: "ID", dataIndex: "id", width: 80 },
            { title: "设备ID", dataIndex: "deviceId" },
            { title: "最近刷新", dataIndex: "lastRefreshAt" },
            { title: "过期时间", dataIndex: "expiresAt" },
            {
              title: "操作",
              width: 160,
              render: (_: any, row: any) => (
                <Space>
                  {!row.revoked && (
                    <Button type="link" size="small" onClick={() => kickDevice(row.id)}>
                      踢出
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
          pagination={{ pageSize: 5 }}
        />
      </Card>
      <Modal
        open={modalOpen}
        title={editing ? "编辑用户" : "新增用户"}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={handleSubmit}
        destroyOnClose
      >
        <Form<UserFormValues> form={form} layout="vertical">
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={
              editing
                ? []
                : [{ required: true, message: "请输入密码" }]
            }
          >
            <Input.Password />
          </Form.Item>
          {!editing && isSuper && (
            <Form.Item
              label="角色"
              name="role"
              initialValue="USER"
              rules={[{ required: true, message: "请选择角色" }]}
            >
              <Select>
                <Select.Option value="USER">用户</Select.Option>
                <Select.Option value="ADMIN">管理员</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}

export default UserManagement;
