import { useEffect, useMemo, useState, useCallback } from "react";
import {
  Button,
  Card,
  Checkbox,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Table,
  Tag,
  message
} from "antd";
import type { ColumnsType } from "antd/es/table";
import client from "../api/client";

type ApplicationRow = {
  id: number;
  appName: string;
  appCode: string;
  appCodePrefix: string;
  appDesc?: string;
  appManagers?: string;
};

type UserItem = {
  id: number;
  username: string;
  role: string;
};

type CurrentUser = {
  id: number;
  username: string;
  role: string;
};

type AppFormValues = {
  appName: string;
  appCodePrefix: string;
  appDesc?: string;
  appManagers?: number[];
};

function parseManagers(raw?: string) {
  if (!raw) return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr.map((v) => String(v));
  } catch {
    return [];
  }
}

function ApplicationManagement() {
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<ApplicationRow[]>([]);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [userMap, setUserMap] = useState<Record<string, string>>({});
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ApplicationRow | null>(null);
  const [form] = Form.useForm<AppFormValues>();

  const isSuper = currentUser?.role === "SUPER_ADMIN";
  const isAdmin = currentUser?.role === "ADMIN";

  const canCreate = isSuper || isAdmin;

  const userCheckboxes = useMemo(() => {
    if (!users || users.length === 0) return [];
    
    // 计算每列的用户数量
    const columnCount = 3;
    const itemsPerColumn = Math.ceil(users.length / columnCount);
    
    // 将用户分成三组
    const columns = [];
    for (let i = 0; i < columnCount; i++) {
      const start = i * itemsPerColumn;
      const end = Math.min(start + itemsPerColumn, users.length);
      const columnUsers = users.slice(start, end);
      
      columns.push(
        <Col span={8} key={i}>
          <Space style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start'}} size="small">
            {columnUsers.map((u) => (
              <Checkbox key={u.id} value={u.id}>
                {u.username}
              </Checkbox>
            ))}
          </Space>
        </Col>
      );
    }
    
    return columns;
  }, [users]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [me, us, apps] = await Promise.all([
        client.get("/user/me"),
        client.get("/application/users"),
        client.get("/application/list")
      ]);
      if (me.data && me.data.code === 1000) {
        setCurrentUser(me.data.data as CurrentUser);
      }
      if (us.data && us.data.code === 1000 && us.data.data) {
        let arr: UserItem[];
        
        // 兼容多种响应格式
        if (Array.isArray(us.data.data)) {
          // 数组格式：直接是用户数组（实际响应格式）
          arr = (us.data.data as any[]).map((u: any) => ({
            id: u.id,
            username: u.username,
            role: 'USER' // 默认角色，因为实际响应中没有role字段
          }));
        } else if (us.data.data.users) {
          // 对象格式：包含 users 字段
          const responseData = us.data.data as { users: any[], currentUserRole?: string };
          arr = responseData.users.map((u: any) => ({
            id: u.id,
            username: u.username,
            role: u.role || 'USER'
          }));
        } else {
          arr = [];
        }
        
        setUsers(arr);
        const m: Record<string, string> = {};
        arr.forEach((u) => {
          m[String(u.id)] = u.username;
        });
        setUserMap(m);
      }
      if (apps.data && apps.data.code === 1000) {
        console.log('应用数据:', apps.data.data);
        const applicationList = apps.data.data as ApplicationRow[];
        setList(applicationList);
      }
    } catch (error) {
      console.error('加载数据失败:', error);
      message.error("加载应用数据失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const columns: ColumnsType<ApplicationRow> = useMemo(
    () => [
      { title: "ID", dataIndex: "id", width: 80 },
      { title: "应用名称", dataIndex: "appName" },
      { title: "应用代码", dataIndex: "appCode" },
      { title: "代码前缀", dataIndex: "appCodePrefix" },
      { title: "描述", dataIndex: "appDesc" },
      {
        title: "管理员",
        render: (_, record) => {
          const ids = parseManagers(record.appManagers);
          if (!ids.length) return "-";
          return (
            <Space size="small" wrap>
              {ids.map((id) => (
                <Tag key={id}>{userMap[id] || id}</Tag>
              ))}
            </Space>
          );
        }
      },
      {
        title: "操作",
        width: 200,
        render: (_, record) => {
          const ids = parseManagers(record.appManagers);
          const uid = currentUser?.id;
          let allowEdit = false;
          let allowDel = false;
          if (isSuper) {
            allowEdit = true;
            allowDel = true;
          } else if (isAdmin && uid != null) {
            if (ids.includes(String(uid))) {
              allowEdit = true;
              allowDel = true;
            }
          }
          if (!allowEdit && !allowDel) return null;
          return (
            <Space>
              {allowEdit && (
                <Button
                  type="link"
                  size="small"
                  onClick={() => handleEdit(record)}
                >
                  编辑
                </Button>
              )}
              {allowDel && (
                <Button
                  type="link"
                  size="small"
                  danger
                  onClick={() => handleDelete(record)}
                >
                  删除
                </Button>
              )}
            </Space>
          );
        }
      }
    ],
    [currentUser, isAdmin, isSuper, userMap]
  );

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  }

  function handleEdit(row: ApplicationRow) {
    setEditing(row);
    const ids = parseManagers(row.appManagers);
    form.setFieldsValue({
      appName: row.appName,
      appCodePrefix: row.appCodePrefix,
      appDesc: row.appDesc,
      appManagers: ids.map((id) => Number(id))
    });
    setModalOpen(true);
  }

  function handleDelete(row: ApplicationRow) {
    Modal.confirm({
      title: "确认删除该应用？",
      onOk: async () => {
        try {
          const resp = await client.post("/application/delete", {
            id: row.id
          });
          if (resp.data && resp.data.code === 1000) {
            message.success("删除成功");
            loadAll();
          } else {
            message.error(resp.data?.msg || "删除失败");
          }
        } catch {
          message.error("删除失败");
        }
      }
    });
  }

  async function handleSubmit() {
    try {
      const values = await form.validateFields();
      const name = values.appName.trim();
      const prefix = values.appCodePrefix.trim();
      if (name.length < 2 || name.length > 16) {
        message.error("应用名称长度需2-16");
        return;
      }
      if (!/^[A-Za-z0-9_]{2,16}$/.test(prefix)) {
        message.error("代码前缀需2-16位字母数字下划线");
        return;
      }
      const managers = (values.appManagers || []).map((id) => String(id));
      const payload: any = {
        app_name: name,
        app_code_prefix: prefix,
        app_desc: values.appDesc || "",
        app_managers: managers
      };
      let url = "/application/create";
      if (editing) {
        url = "/application/update";
        payload.id = editing.id;
      }
      const resp = await client.post(url, payload);
      if (resp.data && resp.data.code === 1000) {
        message.success("操作成功");
        setModalOpen(false);
        setEditing(null);
        loadAll();
      } else {
        message.error(resp.data?.msg || "操作失败");
      }
    } catch (e) {
      if ((e as any).errorFields) return;
    }
  }

  return (
    <Card
      title="应用管理"
      extra={
        canCreate && (
          <Button type="primary" onClick={openCreate}>
            注册应用
          </Button>
        )
      }
    >
      <Table<ApplicationRow>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={list}
        pagination={{ pageSize: 10 }}
      />
      <Modal
        open={modalOpen}
        title={editing ? "编辑应用" : "注册应用"}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={handleSubmit}
      >
        <Form<AppFormValues> layout="vertical" form={form}>
          <Form.Item
            label="应用名称"
            name="appName"
            rules={[{ required: true, message: "请输入应用名称" }]}
          >
            <Input placeholder="2-16字符" />
          </Form.Item>
          <Form.Item
            label="应用前缀"
            name="appCodePrefix"
            rules={[{ required: true, message: "请输入应用前缀" }]}
          >
            <Input placeholder="2-16位字母数字下划线" />
          </Form.Item>
          <Form.Item label="应用描述" name="appDesc">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item label="管理员" name="appManagers">
            <Checkbox.Group style={{ width: "100%" }}>
              <Row gutter={16}>
                {userCheckboxes}
              </Row>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

export default ApplicationManagement;

