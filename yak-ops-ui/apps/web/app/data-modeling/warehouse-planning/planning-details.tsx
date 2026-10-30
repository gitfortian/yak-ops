import { Button, Empty, Table, toast, type TableColumn, type TableColumns } from "@yak-ops/yak-ui";
import { Check, Copy, Folder } from "lucide-react";
import { useState } from "react";
import type { NodeKind, PlanningNode } from "@/service/data-modeling/warehouse-planning/types";
import ProcessAssets from "./process-assets";
import { nodeKey } from "./state";

interface Props {
  node: PlanningNode;
  /** Ancestors from the tree root, used by the breadcrumb. */
  ancestors: readonly PlanningNode[];
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

const ROOT_LABEL: Partial<Record<NodeKind, string>> = {
  CATEGORY: "公共层",
  DOMAIN: "公共层",
  PROCESS: "公共层",
  DATASET: "应用层",
  SUBJECT: "应用层",
};

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      aria-label={`复制${value}`}
      className={
        "ml-1 grid h-5 w-5 shrink-0 place-items-center rounded hover:bg-[var(--yak-color-hover)] " +
        (copied
          ? "[color:var(--yak-color-primary)]"
          : "text-[var(--yak-components-muted-text)] opacity-0 hover:[color:var(--yak-color-primary)] focus-visible:opacity-100 group-hover/prop:opacity-100")
      }
      onClick={() => {
        void navigator.clipboard
          .writeText(value)
          .then(() => {
            setCopied(true);
            window.setTimeout(() => setCopied(false), 1200);
          })
          .catch(() => toast.error("复制失败"));
      }}
    >
      {copied ? <Check size={13} /> : <Copy size={13} />}
    </button>
  );
}

export default function PlanningDetails(props: Props) {
  const { node, ancestors } = props;
  const child = CHILD_TITLE[node.kind];
  const applicationLayer = node.kind === "DATASET" || node.kind === "SUBJECT";

  const actions: TableColumn<PlanningNode> = {
    title: "操作",
    key: "actions",
    width: 120,
    align: "right",
    render: (_, row) => (
      <div className="flex items-center justify-end gap-0.5 whitespace-nowrap">
        <button
          type="button"
          className="rounded-md px-2 py-1 text-xs text-[var(--yak-color-text-secondary)] transition-colors hover:bg-[var(--yak-color-surface-muted)] hover:text-[var(--yak-color-primary)]"
          onClick={() => props.onEdit(row)}
        >
          编辑
        </button>
        <button
          type="button"
          className="rounded-md px-2 py-1 text-xs text-[var(--yak-color-text-secondary)] transition-colors hover:bg-[var(--yak-components-button-danger-bg)] hover:text-[var(--yak-components-danger-text)]"
          onClick={() => props.onDelete(row)}
        >
          删除
        </button>
      </div>
    ),
  };

  const name: TableColumn<PlanningNode> = {
    title: "名称",
    key: "name",
    width: "26%",
    render: (_, row) => (
      <button
        type="button"
        className="max-w-full truncate rounded text-left text-[13px] font-medium text-[var(--yak-color-text-primary)] transition-colors hover:text-[var(--yak-color-primary)]"
        onClick={() => props.onSelect(row)}
      >
        {row.name}
      </button>
    ),
  };
  const attribution: TableColumn<PlanningNode>[] = applicationLayer
    ? [{ title: "英文名", dataIndex: "enName", width: "28%" }]
    : [
        { title: "英文编码", dataIndex: "code", width: "18%" },
        {
          title: "负责人",
          key: "owner",
          width: "16%",
          render: (_, row) => row.ownerName ?? row.ownerId ?? "—",
        },
      ];
  const columns: TableColumns<PlanningNode> = [
    name,
    ...attribution,
    { title: "描述", dataIndex: "description", ellipsis: true },
    actions,
  ];

  const prop = (label: string, value: string, copy: boolean) => (
    <div className="group/prop grid grid-cols-[96px_minmax(0,1fr)] items-center gap-4 border-b border-[var(--yak-components-control-border)] py-2.5 last:border-b-0">
      <dt className="text-xs text-[var(--yak-components-muted-text)]">{label}</dt>
      <dd className="flex min-w-0 items-center gap-1 text-sm text-[var(--yak-color-text-primary)] [overflow-wrap:anywhere]">
        {value || <span className="text-[var(--yak-components-muted-text)]">—</span>}
        {value && copy ? <CopyButton value={value} /> : null}
      </dd>
    </div>
  );

  const create = (
    <Button size="small" variant="primary" onClick={() => props.onCreateChild(node)}>
      {child?.action}
    </Button>
  );

  return (
    <section className="min-w-0 space-y-6">
      <section
        aria-label={`${node.name}详情`}
        className="rounded-xl border border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] p-6 shadow-[0_1px_2px_0_rgb(16_24_40/4%)]"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <nav aria-label="层级路径" className="flex min-w-0 items-center gap-1.5 text-xs">
            <span className="text-[var(--yak-components-muted-text)]">{ROOT_LABEL[node.kind]}</span>
            {ancestors.map((ancestor) => (
              <span key={nodeKey(ancestor)} className="flex min-w-0 items-center gap-1.5">
                <span aria-hidden="true" className="text-[var(--yak-color-border)]">
                  /
                </span>
                <button
                  type="button"
                  className="truncate rounded text-[var(--yak-color-text-secondary)] transition-colors hover:text-[var(--yak-color-primary)]"
                  onClick={() => props.onSelect(ancestor)}
                >
                  {ancestor.name}
                </button>
              </span>
            ))}
          </nav>
          <div className="flex shrink-0 gap-2">
            <Button size="small" onClick={() => props.onEdit(node)}>
              编辑
            </Button>
            <Button
              size="small"
              variant="ghost"
              className="hover:bg-[var(--yak-components-button-danger-bg)]! hover:[color:var(--yak-components-danger-text)]"
              onClick={() => props.onDelete(node)}
            >
              删除
            </Button>
          </div>
        </div>
        <h2 className="mt-3 min-w-0 break-words text-xl leading-7 font-semibold tracking-[-0.01em] text-[var(--yak-color-text-primary)]">
          {node.name}
        </h2>
        <dl className="mt-5 border-t border-[var(--yak-components-control-border)]">
          {applicationLayer && prop("英文名", node.enName ?? "", true)}
          {prop("英文编码", node.code, true)}
          {node.kind !== "SUBJECT" &&
            prop(
              "负责人",
              `${node.ownerName ?? node.ownerId ?? ""}${!node.ownerActive && node.ownerId ? "（已不可选）" : ""}`,
              false,
            )}
          {prop("描述", node.description ?? "", false)}
        </dl>
      </section>
      {node.kind === "PROCESS" ? (
        <ProcessAssets key={node.id} processId={node.id} />
      ) : child ? (
        <div className="overflow-hidden rounded-xl border border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] shadow-[0_1px_2px_0_rgb(16_24_40/4%)]">
          <div className="flex min-h-14 items-center justify-between gap-3 border-b border-[var(--yak-components-control-border)] px-4">
            <h3 className="flex items-center gap-2 text-[15px] font-semibold tracking-[-0.01em]">
              {child.title}
              {node.children.length > 0 && (
                <span className="rounded-full bg-[var(--yak-color-surface-muted)] px-2 py-0.5 text-[11px] font-medium tabular-nums text-[var(--yak-color-text-secondary)]">
                  {node.children.length}
                </span>
              )}
            </h3>
            {node.children.length > 0 && create}
          </div>
          {node.children.length > 0 ? (
            <Table<PlanningNode>
              key={nodeKey(node)}
              rowKey={nodeKey}
              columns={columns}
              dataSource={node.children}
              pagination={node.children.length > 20 ? { pageSize: 20 } : false}
              scroll={{ x: applicationLayer ? 640 : 720 }}
              size="small"
              emptyText="暂无数据"
            />
          ) : (
            <div className="p-3">
              <Empty
                icon={<Folder size={26} strokeWidth={1.5} />}
                title={`还没有${child.title}`}
                description="添加后可在左侧目录中查看并继续维护下级节点。"
                action={create}
              />
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}
