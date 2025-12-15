import { useMemo, useState } from "react";
import { Button, Card, Space, Table, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";

type UserRecord = {
  id: number;
  username: string;
  sex: "男" | "女";
  city: string;
  sign: string;
  experience: number;
  score: number;
  classify: string;
  wealth: number;
};

function createMockData() {
  const cities = ["北京", "上海", "广州", "深圳", "杭州"];
  const signs = ["人生如逆旅，我亦是行人", "不负光阴", "热爱生活", "保持好奇", "持续成长"];
  const classes = ["作家", "工程师", "设计师", "产品经理", "运营"];
  const list: UserRecord[] = [];
  for (let i = 1; i <= 50; i += 1) {
    const city = cities[i % cities.length];
    const sign = signs[i % signs.length];
    const cls = classes[i % classes.length];
    list.push({
      id: i,
      username: `用户${i}`,
      sex: i % 2 === 0 ? "男" : "女",
      city,
      sign,
      experience: 1000 + i * 3,
      score: 80 + (i % 20),
      classify: cls,
      wealth: 100000 + i * 256
    });
  }
  return list;
}

function ListingTableDemo() {
  const [data, setData] = useState<UserRecord[]>(() => createMockData());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const columns: ColumnsType<UserRecord> = useMemo(
    () => [
      {
        title: "ID",
        dataIndex: "id",
        width: 80,
        sorter: (a, b) => a.id - b.id,
        defaultSortOrder: "ascend"
      },
      {
        title: "用户",
        dataIndex: "username",
        width: 120
      },
      {
        title: "性别",
        dataIndex: "sex",
        width: 80,
        filters: [
          { text: "男", value: "男" },
          { text: "女", value: "女" }
        ],
        onFilter: (value, record) => record.sex === value
      },
      {
        title: "城市",
        dataIndex: "city",
        width: 120
      },
      {
        title: "签名",
        dataIndex: "sign",
        ellipsis: true
      },
      {
        title: "积分",
        dataIndex: "experience",
        width: 120,
        sorter: (a, b) => a.experience - b.experience
      },
      {
        title: "评分",
        dataIndex: "score",
        width: 100,
        sorter: (a, b) => a.score - b.score
      },
      {
        title: "职业",
        dataIndex: "classify",
        width: 140,
        render: (value: string) => <Tag color="blue">{value}</Tag>
      },
      {
        title: "财富",
        dataIndex: "wealth",
        width: 160,
        sorter: (a, b) => a.wealth - b.wealth,
        render: (value: number) =>
          value.toLocaleString("zh-CN", {
            style: "currency",
            currency: "CNY",
            maximumFractionDigits: 0
          })
      },
      {
        title: "操作",
        key: "action",
        fixed: "right",
        width: 180,
        render: (_, record) => (
          <Space size="small">
            <Button
              type="link"
              size="small"
              onClick={() => {
                message.info(`编辑：${record.username}`);
              }}
            >
              编辑
            </Button>
            <Button
              type="link"
              size="small"
              danger
              onClick={() => handleDelete(record.id)}
            >
              删除
            </Button>
          </Space>
        )
      }
    ],
    []
  );

  function handleDelete(id: number) {
    setData((prev) => prev.filter((item) => item.id !== id));
    setSelectedRowKeys((prev) => prev.filter((key) => key !== id));
    message.success("删除成功");
  }

  function handleBatchDelete() {
    if (!selectedRowKeys.length) {
      message.info("请先选择数据");
      return;
    }
    setData((prev) =>
      prev.filter((item) => !selectedRowKeys.includes(item.id))
    );
    setSelectedRowKeys([]);
    message.success("批量删除成功");
  }

  function handleRefresh() {
    setData(createMockData());
    setSelectedRowKeys([]);
    message.success("已刷新数据");
  }

  return (
    <div>
      <Card
        title="列表示例"
        extra={
          <Space>
            <Button onClick={handleRefresh}>刷新</Button>
            <Button danger onClick={handleBatchDelete}>
              批量删除
            </Button>
          </Space>
        }
      >
        <Table<UserRecord>
          rowKey="id"
          columns={columns}
          dataSource={data}
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 10 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys
          }}
        />
      </Card>
    </div>
  );
}

export default ListingTableDemo;

