import { Modal, Table, Space, Button, message } from "antd";
import { useEffect, useState } from "react";
import client from "../api/client";
import dayjs from "dayjs";
import UAParser from "ua-parser-js";

type DeviceRow = {
  id: number;
  deviceId: string;
  ip?: string;
  userAgent?: string;
  createdAt?: string;
  lastRefreshAt?: string;
  expiresAt?: string;
  revoked?: boolean;
};

type Props = {
  open: boolean;
  onClose: () => void;
};

function MyDevicesModal({ open, onClose }: Props) {
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<DeviceRow[]>([]);

  useEffect(() => {
    if (!open) return;
    load();
  }, [open]);

  async function load() {
    setLoading(true);
    try {
      const resp = await client.get("/auth/devices");
      if (resp.data && resp.data.code === 1000) {
        setList(resp.data.data || []);
      }
    } finally {
      setLoading(false);
    }
  }

  async function kick(tokenId: number) {
    try {
      const resp = await client.post("/auth/kick", { tokenId });
      if (resp.data && resp.data.code === 1000) {
        message.success("已登出该设备");
        await load();
      } else {
        message.error(resp.data?.msg || "操作失败");
      }
    } catch {
      message.error("操作失败");
    }
  }

  function fmtTime(v?: string) {
    if (!v) return "-";
    const d = dayjs(v);
    return d.isValid() ? d.format("YYYY-MM-DD HH:mm:ss") : "-";
  }
  function uaSummary(v?: string) {
    if (!v) return "-";
    const r = new UAParser(v).getResult();
    const b = r.browser.name || "Browser";
    const bv = r.browser.version ? ` ${r.browser.version}` : "";
    const os = r.os.name || "OS";
    return `${b}${bv} / ${os}`;
  }

  return (
    <Modal
      open={open}
      title="我的设备"
      onCancel={onClose}
      footer={null}
      width={900}
    >
      <Table<DeviceRow>
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={{ pageSize: 6 }}
        columns={[
          { title: "ID", dataIndex: "id", width: 80 },
          { title: "设备ID", dataIndex: "deviceId" },
          { title: "IP", dataIndex: "ip", width: 140 },
          {
            title: "UA",
            dataIndex: "userAgent",
            render: (_: any, row: any) => (
              <span title={row.userAgent}>{uaSummary(row.userAgent)}</span>
            ),
          },
          {
            title: "最近刷新",
            dataIndex: "lastRefreshAt",
            width: 180,
            render: (v: string) => fmtTime(v),
          },
          {
            title: "过期时间",
            dataIndex: "expiresAt",
            width: 180,
            render: (v: string) => fmtTime(v),
          },
          {
            title: "操作",
            width: 160,
            render: (_: any, row: DeviceRow) => (
              <Space>
                {!row.revoked && (
                  <Button type="link" size="small" onClick={() => kick(row.id)}>
                    登出
                  </Button>
                )}
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  );
}

export default MyDevicesModal;
