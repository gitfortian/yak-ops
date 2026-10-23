import { Button } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, FileText, Folder, Plus } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type {
  NodeKey,
  NodeKind,
  PlanningNode,
  PlanningTree,
} from "@/service/data-modeling/warehouse-planning/types";
import { ancestorKeys, nodeKey } from "./state";

interface Props {
  tree: PlanningTree;
  selectedKey: NodeKey | null;
  disabled: boolean;
  onSelect: (key: NodeKey) => void;
  onCreateChild: (parent: PlanningNode) => void;
}

/** Child level offered by each level's inline add button. */
const CHILD: Partial<Record<NodeKind, { kind: NodeKind; label: string }>> = {
  CATEGORY: { kind: "DOMAIN", label: "数据域" },
  DOMAIN: { kind: "PROCESS", label: "业务过程" },
  DATASET: { kind: "SUBJECT", label: "主题域" },
};

const isLeaf = (kind: NodeKind) => kind === "PROCESS" || kind === "SUBJECT";

export default function PlanningTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<NodeKey>>(() => new Set());

  useEffect(() => {
    const ancestors = new Set([
      ...(ancestorKeys(props.tree.categories, props.selectedKey) ?? []),
      ...(ancestorKeys(props.tree.datamarts, props.selectedKey) ?? []),
    ]);
    setCollapsed((previous) => new Set([...previous].filter((key) => !ancestors.has(key))));
  }, [props.tree, props.selectedKey]);

  const toggle = (key: NodeKey) =>
    setCollapsed((previous) => {
      const next = new Set(previous);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const renderNodes = (nodes: readonly PlanningNode[]): ReactNode => (
    <ul className="space-y-1">
      {nodes.map((node) => {
        const key = nodeKey(node);
        const expanded = !collapsed.has(key);
        const child = CHILD[node.kind];
        return (
          <li key={key}>
            <div className="group/row flex min-w-0 items-center gap-1">
              {node.children.length > 0 ? (
                <Button
                  size="small"
                  variant="ghost"
                  disabled={props.disabled}
                  aria-label={`${expanded ? "折叠" : "展开"}${node.name}`}
                  aria-expanded={expanded}
                  onClick={() => toggle(key)}
                >
                  {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                </Button>
              ) : (
                <span className="w-7 shrink-0" />
              )}
              <Button
                size="small"
                disabled={props.disabled}
                variant={props.selectedKey === key ? "secondary" : "ghost"}
                className="min-w-0 flex-1 justify-start"
                aria-current={props.selectedKey === key ? true : undefined}
                onClick={() => props.onSelect(key)}
              >
                {isLeaf(node.kind) ? <FileText size={14} /> : <Folder size={14} />}
                <span className="truncate">{node.name}</span>
              </Button>
              {child && (
                <Button
                  size="small"
                  variant="ghost"
                  disabled={props.disabled}
                  className="opacity-100 md:opacity-0 md:group-hover/row:opacity-100 md:group-focus-within/row:opacity-100"
                  aria-label={`在${node.name}下新建${child.label}`}
                  onClick={() => props.onCreateChild(node)}
                >
                  <Plus size={14} />
                </Button>
              )}
            </div>
            {expanded && node.children.length > 0 && (
              <div className="ml-5">{renderNodes(node.children)}</div>
            )}
          </li>
        );
      })}
    </ul>
  );

  return (
    <nav aria-label="数仓规划目录" className="space-y-4">
      <section aria-label="公共层业务分类">
        <p className="mb-1 px-1 text-xs text-[#667085]">公共层 · 业务分类</p>
        {props.tree.categories.length > 0 ? (
          renderNodes(props.tree.categories)
        ) : (
          <p className="px-1 text-xs text-[#98a2b3]">暂无业务分类</p>
        )}
      </section>
      <section aria-label="应用层数据集市">
        <p className="mb-1 px-1 text-xs text-[#667085]">应用层 · 数据集市</p>
        {props.tree.datamarts.length > 0 ? (
          renderNodes(props.tree.datamarts)
        ) : (
          <p className="px-1 text-xs text-[#98a2b3]">暂无数据集市</p>
        )}
      </section>
    </nav>
  );
}
