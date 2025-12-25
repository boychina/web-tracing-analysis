import {
  Button,
  Col,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Spin,
  Table,
  Typography,
  message,
} from "antd";
import dayjs from "dayjs";
import JsonView from "@uiw/react-json-view";
import { githubLightTheme } from "@uiw/react-json-view/githubLight";
import { useCallback, useEffect, useMemo, useState } from "react";
import client from "../api/client";
import ErrorDetailModal from "./ErrorDetailModal";

type VerifyFormValues = {
  eventType: string;
  requestUri: string;
  sdkUserUuid: string;
  sessionId: string;
};

type RecentEventItem = {
  ID?: number;
  EVENT_TYPE?: string;
  APP_CODE?: string;
  SESSION_ID?: string;
  CREATED_AT?: string | number;
  PAYLOAD?: string;
};

function tryParseJson(raw?: string | null) {
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export default function BuriedPointVerifyModal(props: {
  open: boolean;
  app: { appCode: string; appName: string } | null;
  onClose: () => void;
}) {
  const [verifySubmitting, setVerifySubmitting] = useState(false);
  const [recentLoading, setRecentLoading] = useState(false);
  const [recentEvents, setRecentEvents] = useState<RecentEventItem[]>([]);
  const [verifyForm] = Form.useForm<VerifyFormValues>();
  const [lastRequest, setLastRequest] = useState<any>(null);
  const [lastResponse, setLastResponse] = useState<any>(null);
  const [payloadOpen, setPayloadOpen] = useState(false);
  const [payloadRaw, setPayloadRaw] = useState<string | null>(null);

  const title = useMemo(() => {
    if (!props.app) return "埋点验证";
    return `埋点验证 - ${props.app.appName} (${props.app.appCode})`;
  }, [props.app]);

  const loadRecentByApp = useCallback(async () => {
    if (!props.app) return;
    setRecentLoading(true);
    try {
      const resp = await client.get("/webTrack/events/recentByApp", {
        params: { appCode: props.app.appCode, limit: 10 },
      });
      if (resp.data?.code === 1000) {
        setRecentEvents((resp.data.data || []) as RecentEventItem[]);
      } else {
        message.error(resp.data?.msg || "查询最近埋点失败");
      }
    } catch {
      message.error("查询最近埋点失败");
    } finally {
      setRecentLoading(false);
    }
  }, [props.app]);

  useEffect(() => {
    if (!props.open) return;
    setRecentEvents([]);
    setLastRequest(null);
    setLastResponse(null);
    setPayloadOpen(false);
    setPayloadRaw(null);
    verifyForm.setFieldsValue({
      eventType: "PV",
      requestUri: "/verify/ping",
      sdkUserUuid: "verify-user",
      sessionId: `verify-${Date.now()}`,
    });
    loadRecentByApp();
  }, [loadRecentByApp, props.open, verifyForm]);

  async function submitVerify() {
    if (!props.app) return;
    setVerifySubmitting(true);
    try {
      const values = await verifyForm.validateFields();
      const event = {
        eventType: values.eventType,
        requestUri: values.requestUri,
        sdkUserUuid: values.sdkUserUuid,
        sessionId: values.sessionId,
        timestamp: Date.now(),
      };
      const payload = {
        baseInfo: {
          appCode: props.app.appCode,
          appName: props.app.appName,
          APP_CODE: props.app.appCode,
          APP_NAME: props.app.appName,
        },
        eventInfo: [event],
      };
      setLastRequest(payload);
      const resp = await client.post("/trackweb", payload);
      setLastResponse(resp.data);
      await loadRecentByApp();
      message.success("已发送测试埋点");
    } catch (e) {
      if ((e as any).errorFields) return;
      message.error("发送测试埋点失败");
    } finally {
      setVerifySubmitting(false);
    }
  }

  return (
    <Modal
      open={props.open}
      title={title}
      width={920}
      onCancel={props.onClose}
      footer={null}
      destroyOnClose
    >
      <Row gutter={16}>
        <Col span={12}>
          <Form<VerifyFormValues> layout="vertical" form={verifyForm}>
            <Form.Item
              label="事件类型"
              name="eventType"
              rules={[{ required: true, message: "请选择事件类型" }]}
            >
              <Input placeholder="例如 PV / CLICK / ERROR" />
            </Form.Item>
            <Form.Item
              label="requestUri"
              name="requestUri"
              rules={[{ required: true, message: "请输入 requestUri" }]}
            >
              <Input placeholder="/home" />
            </Form.Item>
            <Form.Item
              label="sdkUserUuid"
              name="sdkUserUuid"
              rules={[{ required: true, message: "请输入 sdkUserUuid" }]}
            >
              <Input placeholder="user-001" />
            </Form.Item>
            <Form.Item
              label="sessionId"
              name="sessionId"
              rules={[{ required: true, message: "请输入 sessionId" }]}
            >
              <Input placeholder="session-001" />
            </Form.Item>
            <Space>
              <Button
                type="primary"
                loading={verifySubmitting}
                onClick={submitVerify}
              >
                发送测试埋点
              </Button>
              <Button
                disabled={!props.app}
                loading={recentLoading}
                onClick={loadRecentByApp}
              >
                刷新最近数据
              </Button>
            </Space>
          </Form>

          <div style={{ marginTop: 16 }}>
            <Typography.Text type="secondary">上报 payload</Typography.Text>
            <div style={{ maxHeight: 220, overflow: "auto", marginTop: 8 }}>
              <JsonView
                value={(lastRequest ?? {}) as any}
                indentWidth={28}
                style={githubLightTheme}
              />
            </div>
          </div>

          <div style={{ marginTop: 16 }}>
            <Typography.Text type="secondary">接口响应</Typography.Text>
            <div style={{ maxHeight: 220, overflow: "auto", marginTop: 8 }}>
              <JsonView
                value={(lastResponse ?? {}) as any}
                indentWidth={28}
                style={githubLightTheme}
              />
            </div>
          </div>
        </Col>

        <Col span={12}>
          <Spin spinning={recentLoading}>
            <Table<RecentEventItem>
              size="small"
              rowKey={(r) => String(r.ID || `${r.CREATED_AT}-${r.SESSION_ID}`)}
              pagination={false}
              dataSource={recentEvents}
              columns={[
                {
                  title: "时间",
                  dataIndex: "CREATED_AT",
                  width: 170,
                  render: (v: any) =>
                    v ? dayjs(v).format("YYYY-MM-DD HH:mm:ss") : "--",
                },
                { title: "类型", dataIndex: "EVENT_TYPE", width: 90 },
                {
                  title: "requestUri",
                  width: 160,
                  render: (_: any, row: RecentEventItem) => {
                    const parsed = tryParseJson(row.PAYLOAD || null) as any;
                    return parsed?.requestUri || "--";
                  },
                },
                {
                  title: "操作",
                  width: 90,
                  render: (_: any, row: RecentEventItem) => (
                    <Button
                      type="link"
                      size="small"
                      onClick={() => {
                        setPayloadRaw(row.PAYLOAD ? String(row.PAYLOAD) : "");
                        setPayloadOpen(true);
                      }}
                    >
                      载荷
                    </Button>
                  ),
                },
              ]}
            />
          </Spin>
        </Col>
      </Row>

      <ErrorDetailModal
        open={payloadOpen}
        raw={payloadRaw}
        title="埋点载荷"
        onClose={() => {
          setPayloadOpen(false);
          setPayloadRaw(null);
        }}
      />
    </Modal>
  );
}
