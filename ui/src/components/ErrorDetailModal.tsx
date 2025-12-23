import { Modal, Spin, Typography } from "antd";
import JsonView from "@uiw/react-json-view";
import { useMemo } from "react";

function tryParseJson(raw?: string | null) {
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export default function ErrorDetailModal(props: {
  open: boolean;
  loading?: boolean;
  raw?: string | null;
  onClose: () => void;
  title?: string;
}) {
  const parsed = useMemo(() => tryParseJson(props.raw), [props.raw]);

  return (
    <Modal
      title={props.title || "错误详情"}
      open={props.open}
      width={720}
      onCancel={props.onClose}
      footer={null}
      destroyOnClose
    >
      <Spin spinning={Boolean(props.loading)}>
        {parsed != null ? (
          <div style={{ maxHeight: 520, overflow: "auto" }}>
            <JsonView
              value={parsed as any}
              indentWidth={28}
              shortenTextAfterLength={400}
            />
          </div>
        ) : props.raw ? (
          <Typography.Paragraph
            style={{ maxHeight: 520, overflow: "auto", marginBottom: 0 }}
            copyable
          >
            {props.raw}
          </Typography.Paragraph>
        ) : (
          <Typography.Text type="secondary">暂无错误详情</Typography.Text>
        )}
      </Spin>
    </Modal>
  );
}
