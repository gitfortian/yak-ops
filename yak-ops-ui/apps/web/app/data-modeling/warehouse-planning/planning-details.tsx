import { Button, Table, type TableColumn, type TableColumns } from "@yak-ops/yak-ui";
import type { NodeKind, PlanningNode } from "@/service/data-modeling/warehouse-planning/types";
import ProcessAssets from "./process-assets";
import { nodeKey } from "./state";

interface Props {
  node: PlanningNode;
  parent: PlanningNode | null;
  onSelect: (node: PlanningNode) => void;
  onCreateChild: (parent: PlanningNode) => void;
  onEdit: (node: PlanningNode) => void;
  onDelete: (node: PlanningNode) => void;
}

/** Child collection offered by each level. */
const CHILD_TITLE: Partial<Record<NodeKind, { title: string; action: string }>> = {
  CATEGORY: { title: "数据域", action: "新建数据域" },
  DOMAIN: { title: "业务过程", action: "新建业务过程" },
  DATASET: { title: "主题域", action: "新建主题域" },
};

const PARENT_LABEL: Partial<Record<NodeKind, string>> = {
  DOMAIN: "所属业务分类",
  PROCESS: "所属数据域",
  SUBJECT: "所属数据集市",
};

export default function PlanningDetails(props: Props) {
  const { node, parent } = props;
  const child = CHILD_TITLE[node.kind];
  const applicationLayer = node.kind === "DATASET" || node.kind === "SUBJECT";

  const actions: TableColumn<PlanningNode> = {
    title: "操作",
    key: "actions",
    width: 120,
    fixed: "right",
    align: "center",
    render: (_, row) => (
      <div className="flex items-center justify-center gap-1">
        <Button
          size="small"
          variant="ghost"
          className="px-0.5 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
          onClick={() => props.onEdit(row)}
        >
          编辑
        </Button>
        <span aria-hidden="true" className="h-3 w-px shrink-0 bg-[#e4e7ec]" />
        <Button
          size="small"
          variant="ghost"
          className="px-0.5 text-xs font-normal text-[#667085] hover:text-[#d92d20]"
          onClick={() => props.onDelete(row)}
        >
          删除
        </Button>
      </div>
    ),
  };

  const name: TableColumn<PlanningNode> = {
    title: "名称",
    key: "name",
    render: (_, row) => (
      <Button size="small" variant="ghost" onClick={() => props.onSelect(row)}>
        {row.name}
      </Button>
    ),
  };
  const attribution: TableColumn<PlanningNode>[] = applicationLayer
    ? [{ title: "英文名", dataIndex: "enName" }]
    : [
        { title: "英文编码", dataIndex: "code" },
        {
          title: "负责人",
          key: "owner",
          render: (_, row) => row.ownerName ?? row.ownerId ?? "—",
        },
      ];
  const columns: TableColumns<PlanningNode> = [
    name,
    ...attribution,
    { title: "描述", dataIndex: "description", ellipsis: true },
    actions,
  ];

  return (
    <section className="min-w-0 space-y-4">
      <section
        aria-label={`${node.name}详情`}
        className="rounded border border-[var(--yak-components-panel-border)] p-4"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="min-w-0 break-words font-medium">{node.name}</h2>
          <div className="flex gap-2">
            <Button size="small" onClick={() => props.onEdit(node)}>
              编辑
            </Button>
            <Button size="small" variant="danger" onClick={() => props.onDelete(node)}>
              删除
            </Button>
          </div>
        </div>
        <dl className="mt-3 grid grid-cols-1 gap-2 text-sm sm:grid-cols-[104px_minmax(0,1fr)] [&>dd]:min-w-0 [&>dd]:break-words">
          {node.enName && (
            <>
              <dt>英文名</dt>
              <dd>{node.enName}</dd>
            </>
          )}
          <dt>英文编码</dt>
          <dd>{node.code}</dd>
          {parent && PARENT_LABEL[node.kind] && (
            <>
              <dt>{PARENT_LABEL[node.kind]}</dt>
              <dd>{parent.name}</dd>
            </>
          )}
          {node.kind !== "SUBJECT" && (
            <>
              <dt>负责人</dt>
              <dd>
                {node.ownerName ?? node.ownerId ?? "—"}
                {!node.ownerActive && node.ownerId && "（已不可选）"}
              </dd>
            </>
          )}
          <dt>描述</dt>
          <dd className="whitespace-pre-wrap break-words">{node.description || "—"}</dd>
        </dl>
      </section>
      {node.kind === "PROCESS" ? (
        <ProcessAssets key={node.id} processId={node.id} />
      ) : child ? (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <h3>{child.title}</h3>
            <Button size="small" variant="primary" onClick={() => props.onCreateChild(node)}>
              {child.action}
            </Button>
          </div>
          <Table<PlanningNode>
            key={nodeKey(node)}
            rowKey={nodeKey}
            columns={columns}
            dataSource={node.children}
            pagination={{ pageSize: 20 }}
            size="small"
            emptyText="暂无数据"
          />
        </div>
      ) : null}
    </section>
  );
}
