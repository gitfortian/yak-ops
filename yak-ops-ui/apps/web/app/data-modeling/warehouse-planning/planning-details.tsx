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
    width: 130,
    align: "right",
    render: (_, row) => (
      <div className="flex items-center justify-end gap-1">
        <Button
          size="small"
          variant="ghost"
          className="px-0.5 text-xs font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-color-primary)]"
          onClick={() => props.onEdit(row)}
        >
          编辑
        </Button>
        <Button
          size="small"
          variant="ghost"
          className="px-0.5 text-xs font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-danger-text)]"
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
    width: applicationLayer ? "32%" : "26%",
    render: (_, row) => (
      <Button size="small" variant="ghost" onClick={() => props.onSelect(row)}>
        {row.name}
      </Button>
    ),
  };
  const attribution: TableColumn<PlanningNode>[] = applicationLayer
    ? [{ title: "英文名", dataIndex: "enName", width: "28%" }]
    : [
        { title: "英文编码", dataIndex: "code", width: "18%" },
        {
          title: "负责人",
          key: "owner",
          width: "18%",
          render: (_, row) => row.ownerName ?? row.ownerId ?? "—",
        },
      ];
  const columns: TableColumns<PlanningNode> = [
    name,
    ...attribution,
    { title: "描述", dataIndex: "description", ellipsis: true },
    actions,
  ];

  const prop = (label: string, value: string, copy: boolean, full = false) => (
    <div className={"group/prop flex min-w-0 items-baseline gap-3 py-1 " + (full ? "w-full" : "")}>
      <dt className="w-[76px] shrink-0 text-xs text-[var(--yak-color-text-secondary)]">{label}</dt>
      {value ? (
        <dd className="flex min-w-0 items-baseline text-sm [overflow-wrap:anywhere]">
          {value}
          {copy && <CopyButton value={value} />}
        </dd>
      ) : (
        <dd className="text-sm text-[var(--yak-components-muted-text)]">—</dd>
      )}
    </div>
  );

  const create = (
    <Button size="small" variant="primary" onClick={() => props.onCreateChild(node)}>
      {child?.action}
    </Button>
  );

  return (
    <section className="min-w-0 space-y-5">
      <section
        aria-label={`${node.name}详情`}
        className="rounded-lg border border-[var(--yak-components-panel-border)] p-5"
      >
        <nav aria-label="层级路径" className="flex h-5 items-center gap-1.5 text-xs">
          <span className="text-[var(--yak-components-muted-text)]">{ROOT_LABEL[node.kind]}</span>
          {ancestors.map((ancestor) => (
            <span key={nodeKey(ancestor)} className="flex items-center gap-1.5">
              <span aria-hidden="true" className="text-[var(--yak-components-muted-text)]">
                /
              </span>
              <button
                type="button"
                className="rounded text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-color-primary)] hover:underline hover:underline-offset-2"
                onClick={() => props.onSelect(ancestor)}
              >
                {ancestor.name}
              </button>
            </span>
          ))}
        </nav>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
          <h2 className="min-w-0 break-words text-base font-semibold">{node.name}</h2>
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
        <dl className="mt-4 flex flex-wrap items-baseline gap-x-6">
          {applicationLayer && prop("英文名", node.enName ?? "", true)}
          {prop("英文编码", node.code, true)}
          {node.kind !== "SUBJECT" &&
            prop(
              "负责人",
              `${node.ownerName ?? node.ownerId ?? ""}${!node.ownerActive && node.ownerId ? "（已不可选）" : ""}`,
              false,
            )}
          {prop("描述", node.description ?? "", false, true)}
        </dl>
      </section>
      {node.kind === "PROCESS" ? (
        <ProcessAssets key={node.id} processId={node.id} />
      ) : child ? (
        <div>
          <div className="flex h-8 items-center justify-between gap-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold">
              {child.title}
              {node.children.length > 0 && (
                <span className="text-xs font-normal text-[var(--yak-components-muted-text)]">
                  {node.children.length} 个
                </span>
              )}
            </h3>
            {node.children.length > 0 && create}
          </div>
          {node.children.length > 0 ? (
            <div className="mt-3">
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
            </div>
          ) : (
            <Empty
              icon={<Folder size={26} strokeWidth={1.5} />}
              title={`还没有${child.title}`}
              description="添加后可在左侧目录中查看并继续维护下级节点。"
              action={create}
            />
          )}
        </div>
      ) : null}
    </section>
  );
}
