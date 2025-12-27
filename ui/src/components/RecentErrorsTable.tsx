import { Button, Table, Tag, message } from "antd";
import type { TableProps } from "antd";
import dayjs from "dayjs";
import { useMemo, useState } from "react";
import ErrorDetailModal from "./ErrorDetailModal";

export type RecentErrorItem = {
  ID?: number;
  APP_CODE?: string;
  APP_NAME?: string;
  ERROR_CODE?: string;
  MESSAGE?: string;
  PAYLOAD?: string;
  SEVERITY?: string;
  REQUEST_URI?: string;
  CREATED_AT?: string | number;
};

function parsePayload(raw?: string) {
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function getPayloadField(row: RecentErrorItem, field: string) {
  const payload = parsePayload(row.PAYLOAD);
  if (payload && typeof payload === "object" && field in payload) {
    const v = (payload as any)[field];
    if (v == null) return "";
    return typeof v === "string" ? v : JSON.stringify(v);
  }
  return "";
}

function renderSeverity(severity?: string) {
  if (!severity) return <Tag>未分级</Tag>;
  const upper = severity.toUpperCase();
  if (upper.includes("CRIT") || upper.includes("FATAL") || upper.includes("SEV"))
    return <Tag color="red">{severity}</Tag>;
  if (upper.includes("WARN")) return <Tag color="gold">{severity}</Tag>;
  if (upper.includes("INFO")) return <Tag color="blue">{severity}</Tag>;
  return <Tag>{severity}</Tag>;
}

export default function RecentErrorsTable(props: {
  data: RecentErrorItem[];
  loading?: boolean;
  showApp?: boolean;
  onAppClick?: (appCode: string) => void;
  fetchPayload?: (row: RecentErrorItem) => Promise<string | null | undefined>;
  currentAppCode?: string;
  tableProps?: Omit<TableProps<RecentErrorItem>, "columns" | "dataSource">;
}) {
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailRaw, setDetailRaw] = useState<string | null>(null);

  const columns = useMemo(() => {
    const cols: any[] = [
      {
        title: "时间戳",
        dataIndex: "CREATED_AT",
        width: 180,
        render: (v: any) => (v ? dayjs(v).format("YYYY-MM-DD HH:mm:ss") : "--"),
      },
    ];

    if (props.showApp) {
      cols.push({
        title: "应用",
        dataIndex: "APP_CODE",
        width: 220,
        render: (_: any, row: RecentErrorItem) =>
          row.APP_CODE ? (
            <a
              onClick={() => {
                if (props.onAppClick) props.onAppClick(row.APP_CODE as string);
              }}
            >
              {`${row.APP_NAME || row.APP_CODE} (${row.APP_CODE})`}
            </a>
          ) : (
            "--"
          ),
      });
    }

    cols.push(
      {
        title: "错误代码",
        dataIndex: "ERROR_CODE",
        width: 160,
        render: (_: any, row: RecentErrorItem) =>
          getPayloadField(row, "eventId") || row.ERROR_CODE || "--",
      },
      {
        title: "消息",
        dataIndex: "MESSAGE",
        ellipsis: { showTitle: false },
        render: (_: any, row: RecentErrorItem) =>
          getPayloadField(row, "errMessage") || row.MESSAGE || "--",
      },
      {
        title: "严重程度",
        dataIndex: "SEVERITY",
        width: 120,
        render: (v: any) => renderSeverity(v),
      },
      {
        title: "页面",
        dataIndex: "REQUEST_URI",
        width: 240,
        ellipsis: true,
        render: (_: any, row: RecentErrorItem) =>
          getPayloadField(row, "requestUri") || row.REQUEST_URI || "--",
      },
      {
        title: "操作",
        key: "action",
        width: 120,
        render: (_: any, row: RecentErrorItem) => (
          <Button
            type="link"
            onClick={async () => {
              const localRaw = row.PAYLOAD;
              setDetailRaw(localRaw || null);
              setDetailOpen(true);
              if (localRaw) return;
              if (!props.fetchPayload) {
                message.info("暂无错误详情");
                return;
              }
              try {
                setDetailLoading(true);
                const raw = await props.fetchPayload(row);
                if (raw) setDetailRaw(String(raw));
                else message.info("暂无错误详情");
              } catch {
                message.error("查询错误详情失败");
              } finally {
                setDetailLoading(false);
              }
            }}
          >
            错误详情
          </Button>
        ),
      }
    );

    return cols;
  }, [props.fetchPayload, props.onAppClick, props.showApp]);

  return (
    <>
      <Table
        rowKey={(row) =>
          String(row.ID || `${row.APP_CODE || props.currentAppCode}-${row.CREATED_AT}`)
        }
        loading={props.loading}
        columns={columns}
        dataSource={props.data}
        {...props.tableProps}
      />
      <ErrorDetailModal
        open={detailOpen}
        loading={detailLoading}
        raw={detailRaw}
        onClose={() => {
          setDetailOpen(false);
          setDetailRaw(null);
        }}
      />
    </>
  );
}
