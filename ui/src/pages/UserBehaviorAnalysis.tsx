import { useState } from "react";
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Table,
  Timeline,
  Typography,
  message
} from "antd";
import type { ColumnsType } from "antd/es/table";
import UAParser from "ua-parser-js";
import client from "../api/client";

type SessionRow = {
  SESSION_ID: string;
  USER_NAME?: string;
  IPADDR?: string;
  OPEN_ID?: string;
  VISIT_TIME?: string;
  NAVIGATOR_USERAGENT?: string;
  VENDOR?: string;
  PLATFORM?: string;
  SCREEN?: string;
  DEVICE_ID?: string;
  VERSION_ID?: string;
};

type BehaviorItem = {
  VISIT_TIME?: string;
  RECORD_ID?: string;
  TRIGGER_PAGE_URL?: string;
  EVENT_TYPE?: string;
  DURATION_TIME?: number;
  ELEMENT_PATH?: string;
  LOCATION?: string;
  EVENT_INFO?: unknown;
};

type DeviceInfo = {
  vendor?: string;
  model?: string;
  osName?: string;
  osVersion?: string;
  webkit?: string;
  app?: string;
  appVersion?: string;
  platform?: string;
  screenWidth?: string;
  screenHeight?: string;
  deviceId?: string;
  location?: string;
  visitTime?: string;
  versionLabel?: string;
  debugUrl?: string;
};

type SearchFormValues = {
  userId?: string;
  time?: any[];
};

function formatDateTime(d: Date) {
  const y = d.getFullYear();
  const m = `${d.getMonth() + 1}`.padStart(2, "0");
  const day = `${d.getDate()}`.padStart(2, "0");
  const hh = `${d.getHours()}`.padStart(2, "0");
  const mm = `${d.getMinutes()}`.padStart(2, "0");
  const ss = `${d.getSeconds()}`.padStart(2, "0");
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`;
}

function getDateFromPicker(value: any) {
  if (!value) return null;
  if (typeof value === "string" || value instanceof Date) {
    return new Date(value);
  }
  if (typeof value.toDate === "function") {
    return value.toDate();
  }
  return null;
}

function mapVersion(versionId?: string | null) {
  if (!versionId) {
    return { label: "", prefix: "" };
  }
  const map: Record<string, { label: string; prefix: string }> = {
    "1": { label: "微信公众号", prefix: "20240730001@622001@" },
    "2": { label: "支付宝生活号", prefix: "20240730002@622001@" },
    "3": { label: "微信小程序", prefix: "20240730003@622001@" },
    "4": { label: "支付宝小程序", prefix: "20240730004@622001@" },
    "5": { label: "企业微信", prefix: "20240730005@622001@" },
    "6": { label: "企业微信小程序", prefix: "20240730006@622001@" },
    "7": { label: "钉钉", prefix: "20240730007@622001@" },
    "8": { label: "钉钉小程序", prefix: "20240730008@622001@" },
    "9": { label: "百度小程序", prefix: "20240730009@622001@" },
    "10": { label: "字节跳动小程序", prefix: "20240730010@622001@" },
    "11": { label: "快手小程序", prefix: "20240730011@622001@" },
    "12": { label: "QQ小程序", prefix: "20240730012@622001@" },
    "13": { label: "头条小程序", prefix: "20240730013@622001@" }
  };
  return map[versionId] || { label: "", prefix: "" };
}

function UserBehaviorAnalysis() {
  const [form] = Form.useForm<SearchFormValues>();
  const [loading, setLoading] = useState(false);
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [behaviors, setBehaviors] = useState<BehaviorItem[]>([]);
  const [device, setDevice] = useState<DeviceInfo | null>(null);

  const sessionColumns: ColumnsType<SessionRow> = [
    {
      title: "会话ID",
      dataIndex: "SESSION_ID",
      width: 200
    },
    {
      title: "用户名",
      dataIndex: "USER_NAME",
      width: 140
    },
    {
      title: "访问IP",
      dataIndex: "IPADDR",
      width: 140
    },
    {
      title: "用户标识",
      dataIndex: "OPEN_ID",
      width: 200
    },
    {
      title: "访问时间",
      dataIndex: "VISIT_TIME",
      width: 200
    }
  ];

  async function handleSearch(values: SearchFormValues) {
    const userId = values.userId;
    const range = values.time;
    if (!userId) {
      message.warning("请输入用户ID");
      return;
    }
    if (!range || range.length !== 2) {
      message.warning("请输入时间范围");
      return;
    }
    const start = getDateFromPicker(range[0]);
    const end = getDateFromPicker(range[1]);
    if (!start || !end) {
      message.warning("时间格式不正确");
      return;
    }
    const startDate = formatDateTime(start);
    const endDate = formatDateTime(end);
    setLoading(true);
    try {
      const resp = await client.post("/webTrack/queryUserSessionRecord", {
        openId: userId,
        startDate,
        endDate
      });
      if (resp.data && resp.data.code === 1000) {
        const payload = resp.data.data;
        const list: SessionRow[] = payload
          ? payload.sessionArray || payload
          : [];
        setSessions(list);
        setBehaviors([]);
        setDevice(null);
        if (list.length > 0) {
          await handleSelectSession(list[0]);
        }
      } else {
        setSessions([]);
        setBehaviors([]);
        setDevice(null);
        message.error(resp.data?.msg || "查询会话记录失败");
      }
    } catch {
      message.error("查询会话记录失败");
      setSessions([]);
      setBehaviors([]);
      setDevice(null);
    } finally {
      setLoading(false);
    }
  }

  async function handleSelectSession(row: SessionRow) {
    const ua = row.NAVIGATOR_USERAGENT || "";
    const parser = new UAParser(ua);
    const result = parser.getResult();
    const info: DeviceInfo = {
      vendor: result.device.vendor || "",
      model: result.device.model || "",
      osName: result.os.name || "",
      osVersion: result.os.version || "",
      webkit: row.VENDOR || "",
      app: result.browser.name || "",
      appVersion: result.browser.version || "",
      platform: row.PLATFORM || "",
      screenWidth: row.SCREEN
        ? row.SCREEN.split("*")[0]
        : undefined,
      screenHeight: row.SCREEN
        ? row.SCREEN.split("*")[1]
        : undefined,
      deviceId: row.DEVICE_ID || "",
      visitTime: row.VISIT_TIME || ""
    };
    const amapKey = (import.meta as any).env.VITE_AMAP_KEY as string | undefined;
    if (amapKey && row.IPADDR) {
      try {
        const resp = await fetch(
          `https://restapi.amap.com/v3/ip?key=${amapKey}&ip=${encodeURIComponent(
            row.IPADDR
          )}`
        );
        const data = await resp.json();
        if (data && String(data.status) === "1") {
          info.location = `${data.province || ""}-${data.city || ""}`;
        }
      } catch {
      }
    }
    const version = mapVersion(row.VERSION_ID || null);
    if (version.label) {
      info.versionLabel = version.label;
      if (row.OPEN_ID) {
        info.debugUrl = `${version.prefix}${row.OPEN_ID}`;
      }
    }
    setDevice(info);
    if (row.SESSION_ID && row.VISIT_TIME) {
      const queryDate = row.VISIT_TIME.substring(0, 10);
      try {
        const resp = await client.post("/webTrack/queryUserBehavior", {
          sessionId: row.SESSION_ID,
          queryDate
        });
        if (resp.data && resp.data.code === 1000) {
          const list: BehaviorItem[] =
            resp.data.data?.actionArray || resp.data.data || [];
          setBehaviors(list);
        } else {
          setBehaviors([]);
          message.error(resp.data?.msg || "查询行为数据失败");
        }
      } catch {
        setBehaviors([]);
        message.error("查询行为数据失败");
      }
    } else {
      setBehaviors([]);
    }
  }

  function showEventInfo(item: BehaviorItem) {
    if (!item.EVENT_INFO) {
      message.info("暂无事件详情");
      return;
    }
    let content = "";
    try {
      if (typeof item.EVENT_INFO === "string") {
        content = JSON.stringify(JSON.parse(item.EVENT_INFO), null, 2);
      } else {
        content = JSON.stringify(item.EVENT_INFO, null, 2);
      }
    } catch {
      content = String(item.EVENT_INFO);
    }
    Modal.info({
      title: "上报信息",
      width: 520,
      content: (
        <pre
          style={{
            maxHeight: 400,
            overflow: "auto"
          }}
        >
          {content}
        </pre>
      )
    });
  }

  async function showRequestInfo(item: BehaviorItem) {
    if (!item.VISIT_TIME || !item.RECORD_ID) {
      message.info("缺少请求索引信息");
      return;
    }
    const queryDate = item.VISIT_TIME.substring(0, 10);
    try {
      const resp = await client.get("/webTrack/queryPageRequest", {
        params: {
          queryDate,
          recordId: item.RECORD_ID
        }
      });
      if (resp.data && resp.data.code === 1000) {
        const content = JSON.stringify(resp.data.data, null, 2);
        Modal.info({
          title: "接口信息",
          width: 520,
          content: (
            <pre
              style={{
                maxHeight: 400,
                overflow: "auto"
              }}
            >
              {content}
            </pre>
          )
        });
      } else {
        message.error(resp.data?.msg || "查询接口信息失败");
      }
    } catch {
      message.error("查询接口信息失败");
    }
  }

  return (
    <div>
      <Row gutter={16}>
        <Col xs={24} md={18}>
          <Row gutter={16}>
            <Col span={24}>
              <Card title="查询条件" style={{ marginBottom: 16 }}>
                <Form<SearchFormValues>
                  form={form}
                  layout="inline"
                  onFinish={handleSearch}
                >
                  <Form.Item
                    label="用户标识"
                    name="userId"
                    rules={[{ required: true, message: "请输入用户ID" }]}
                  >
                    <Input
                      placeholder="请输入用户ID"
                      style={{ width: 260 }}
                    />
                  </Form.Item>
                  <Form.Item
                    label="时间范围"
                    name="time"
                    rules={[{ required: true, message: "请选择时间范围" }]}
                  >
                    <DatePicker.RangePicker
                      showTime
                      style={{ width: 320 }}
                    />
                  </Form.Item>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      loading={loading}
                    >
                      查询
                    </Button>
                  </Form.Item>
                </Form>
              </Card>
            </Col>
            <Col span={24}>
              <Card title="会话记录" style={{ marginBottom: 16 }}>
                <Table<SessionRow>
                  rowKey="SESSION_ID"
                  columns={sessionColumns}
                  dataSource={sessions}
                  loading={loading}
                  pagination={{ pageSize: 10 }}
                  onRow={(record) => ({
                    onClick: () => {
                      handleSelectSession(record);
                    }
                  })}
                />
              </Card>
            </Col>
            <Col span={24}>
              <Card title="设备信息">
                {device ? (
                  <Descriptions bordered column={2} size="small">
                    <Descriptions.Item label="手机品牌">
                      {device.vendor || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="手机型号">
                      {device.model || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="操作系统">
                      {device.osName || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="系统版本">
                      {device.osVersion || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="浏览器内核">
                      {device.webkit || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="应用名称">
                      {device.app || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="应用版本">
                      {device.appVersion || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="平台">
                      {device.platform || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="屏幕宽度">
                      {device.screenWidth || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="屏幕高度">
                      {device.screenHeight || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="设备ID">
                      {device.deviceId || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="IP归属地">
                      {device.location || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="访问时间">
                      {device.visitTime || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="版本标识">
                      {device.versionLabel || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="调试链接" span={2}>
                      {device.debugUrl || "-"}
                    </Descriptions.Item>
                  </Descriptions>
                ) : (
                  <Typography.Text type="secondary">
                    请选择一个会话记录查看设备信息
                  </Typography.Text>
                )}
              </Card>
            </Col>
          </Row>
        </Col>
        <Col xs={24} md={6}>
          <Card title="时序行为分析">
            {behaviors.length === 0 ? (
              <Typography.Text type="secondary">
                请选择一个会话记录查看数据
              </Typography.Text>
            ) : (
              <div
                style={{
                  maxHeight: 640,
                  overflowY: "auto"
                }}
              >
                <Timeline>
                  {behaviors.map((item, index) => (
                    <Timeline.Item key={index}>
                      <Typography.Text strong style={{ color: "#01AAED" }}>
                        {item.VISIT_TIME}
                      </Typography.Text>
                      <div>
                        索引记录: {item.RECORD_ID}
                      </div>
                      <div>
                        页面URL: {item.TRIGGER_PAGE_URL}
                      </div>
                      <div>
                        页面动作: {item.EVENT_TYPE}
                        <Button
                          type="link"
                          size="small"
                          onClick={() => showEventInfo(item)}
                        >
                          详情
                        </Button>
                      </div>
                      <div>
                        访问时间: {item.VISIT_TIME}
                      </div>
                      {item.EVENT_TYPE === "CLICK" && (
                        <>
                          <div>
                            元素路径: {item.ELEMENT_PATH}
                          </div>
                          <div>
                            坐标位置: {item.LOCATION}
                          </div>
                        </>
                      )}
                      <div>
                        驻停时间: {item.DURATION_TIME} ms
                      </div>
                      <div>
                        请求接口:
                        <Button
                          type="link"
                          size="small"
                          onClick={() => showRequestInfo(item)}
                        >
                          点击查看
                        </Button>
                      </div>
                    </Timeline.Item>
                  ))}
                </Timeline>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default UserBehaviorAnalysis;

