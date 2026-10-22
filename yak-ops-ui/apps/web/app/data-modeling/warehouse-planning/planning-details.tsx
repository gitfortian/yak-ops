import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type {
  CategoryNode,
  DomainNode,
  PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";
import ProcessAssets from "./process-assets";
import { nodeKey } from "./state";

interface Props {
  node: PlanningNode;
  parent: CategoryNode | DomainNode | null;
  onSelect: (node: PlanningNode) => void;
  onCreateChild: (parent: CategoryNode | DomainNode) => void;
  onEdit: (node: PlanningNode) => void;
  onDelete: (node: PlanningNode) => void;
}

export default function PlanningDetails(props: Props) {
  const { node, parent } = props;
  const columns: TableColumns<PlanningNode> = [
    {
      title: "名称",
      key: "name",
      render: (_, row) => (
        <Button size="small" variant="ghost" onClick={() => props.onSelect(row)}>
          {row.name}
        </Button>
      ),
    },
    { title: "英文编码", dataIndex: "code" },
    { title: "负责人", key: "owner", render: (_, row) => row.ownerName ?? row.ownerId },
    { title: "描述", dataIndex: "description", ellipsis: true },
    {
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
    },
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
          <dt>英文编码</dt>
          <dd>{node.code}</dd>
          {parent && (
            <>
              <dt>{node.kind === "PROCESS" ? "所属数据域" : "所属业务分类"}</dt>
              <dd>{parent.name}</dd>
            </>
          )}
          <dt>负责人</dt>
          <dd>
            {node.ownerName ?? node.ownerId}
            {!node.ownerActive && "（已不可选）"}
          </dd>
          <dt>描述</dt>
          <dd className="whitespace-pre-wrap break-words">{node.description || "—"}</dd>
        </dl>
      </section>
      {node.kind === "PROCESS" ? (
        <ProcessAssets key={node.id} processId={node.id} />
      ) : (
        <div>
          <div className="mb-3 flex items-center justify-between">
            <h3>{node.kind === "CATEGORY" ? "数据域" : "业务过程"}</h3>
            <Button size="small" variant="primary" onClick={() => props.onCreateChild(node)}>
              {node.kind === "CATEGORY" ? "新建数据域" : "新建业务过程"}
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
      )}
    </section>
  );
}
