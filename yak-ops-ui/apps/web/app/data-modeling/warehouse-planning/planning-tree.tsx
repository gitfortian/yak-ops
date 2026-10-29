import { Button, Input } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, FileText, Folder, Plus, Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
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
  /** Shows the node search; the layer view navigates by tab only. */
  filterable: boolean;
  /** Renders the tree read-only and de-emphasized, e.g. while the layer view owns the pane. */
  muted: boolean;
  onSelect: (key: NodeKey) => void;
  onCreateChild: (parent: PlanningNode) => void;
  onCreateDatamart: () => void;
}

/** Child level offered by each level's inline add button. */
const CHILD: Partial<Record<NodeKind, { kind: NodeKind; label: string }>> = {
  CATEGORY: { kind: "DOMAIN", label: "数据域" },
  DOMAIN: { kind: "PROCESS", label: "业务过程" },
  DATASET: { kind: "SUBJECT", label: "主题域" },
};

const isLeaf = (kind: NodeKind) => kind === "PROCESS" || kind === "SUBJECT";

/** Marks every node that matches the keyword, plus the ancestors that lead to it. */
const collectHits = (
  nodes: readonly PlanningNode[],
  needle: string,
  hits: Set<NodeKey>,
): boolean => {
  let matched = false;
  for (const node of nodes) {
    const childMatched = collectHits(node.children, needle, hits);
    if (childMatched || node.name.toLowerCase().includes(needle)) {
      hits.add(nodeKey(node));
      matched = true;
    }
  }
  return matched;
};

export default function PlanningTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<NodeKey>>(() => new Set());
  const [keyword, setKeyword] = useState("");
  const treeRef = useRef<HTMLDivElement>(null);
  const needle = props.filterable ? keyword.trim().toLowerCase() : "";
  const hits = useMemo(() => {
    const next = new Set<NodeKey>();
    if (needle) {
      collectHits(props.tree.categories, needle, next);
      collectHits(props.tree.datamarts, needle, next);
    }
    return next;
  }, [needle, props.tree]);
  const filtering = needle.length > 0;
  const zero = filtering && hits.size === 0;

  useEffect(() => {
    const ancestors = new Set([
      ...(ancestorKeys(props.tree.categories, props.selectedKey) ?? []),
      ...(ancestorKeys(props.tree.datamarts, props.selectedKey) ?? []),
    ]);
    setCollapsed((previous) => new Set([...previous].filter((key) => !ancestors.has(key))));
  }, [props.tree, props.selectedKey]);

  useEffect(() => {
    treeRef.current
      ?.querySelector<HTMLElement>('[aria-current="true"]')
      ?.scrollIntoView({ block: "nearest" });
  }, [props.selectedKey, props.tree]);

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
        if (filtering && !hits.has(key)) return null;
        const expanded = filtering || !collapsed.has(key);
        const selected = props.selectedKey === key;
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
                variant="ghost"
                className="min-w-0 flex-1 justify-start font-normal"
                style={{
                  backgroundColor: selected ? "var(--yak-color-primary-subtle)" : undefined,
                  color: selected ? "var(--yak-color-primary)" : "var(--yak-color-text-primary)",
                }}
                aria-current={selected ? true : undefined}
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

  const group = (label: string, roots: readonly PlanningNode[], empty: ReactNode) => {
    const visible = roots.filter((root) => !filtering || hits.has(nodeKey(root)));
    if (filtering && visible.length === 0) return null;
    return (
      <section
        aria-label={label}
        className="mt-4 border-t border-[var(--yak-components-panel-border)] pt-4 first:mt-0 first:border-t-0 first:pt-0"
      >
        <p className="flex h-5 items-center px-1.5 text-xs font-semibold text-[var(--yak-color-text-secondary)]">
          {label}
        </p>
        {visible.length > 0 ? renderNodes(visible) : empty}
      </section>
    );
  };

  return (
    <nav
      aria-label="数仓规划目录"
      aria-disabled={props.muted || undefined}
      className={
        "flex min-h-0 flex-1 flex-col " + (props.muted ? "pointer-events-none opacity-60" : "")
      }
    >
      {props.filterable && (
        <>
          <div className="relative mt-3">
            <Search
              size={14}
              aria-hidden="true"
              className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
            />
            <Input
              size="small"
              variant="outlined"
              value={keyword}
              className="pl-8 pr-8"
              placeholder="搜索节点"
              aria-label="搜索节点"
              onChange={(event) => setKeyword(event.target.value)}
            />
            {keyword && (
              <button
                type="button"
                aria-label="清空搜索"
                className="absolute right-1 top-1/2 grid h-5 w-5 -translate-y-1/2 place-items-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)]"
                onClick={() => setKeyword("")}
              >
                <X size={12} />
              </button>
            )}
          </div>
          {filtering && !zero && (
            <div className="mt-2 flex h-5 items-center">
              <span className="inline-flex h-5 items-center gap-0.5 rounded-full bg-[var(--yak-color-primary-subtle)] pl-2 pr-0.5 text-xs text-[var(--yak-color-text-secondary)]">
                已筛选：
                <b className="font-medium text-[var(--yak-color-text-primary)]">{keyword.trim()}</b>
                <button
                  type="button"
                  aria-label="清除筛选"
                  className="grid h-4 w-4 place-items-center rounded-full hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)]"
                  onClick={() => setKeyword("")}
                >
                  <X size={10} />
                </button>
              </span>
            </div>
          )}
        </>
      )}
      <div
        ref={treeRef}
        className={"min-h-0 flex-1 overflow-auto " + (props.filterable ? "mt-4" : "mt-3")}
      >
        {group(
          "公共层 · 业务分类",
          props.tree.categories,
          <p className="px-1.5 text-xs leading-7 text-[var(--yak-components-muted-text)]">
            暂无业务分类
          </p>,
        )}
        {group(
          "应用层 · 数据集市",
          props.tree.datamarts,
          <p className="flex items-center gap-2 px-1.5 text-xs leading-7 text-[var(--yak-color-text-secondary)]">
            暂无数据集市
            <Button
              size="small"
              variant="ghost"
              className="h-auto px-0 text-xs font-normal"
              style={{ color: "var(--yak-color-primary)" }}
              onClick={props.onCreateDatamart}
            >
              新建数据集市
            </Button>
          </p>,
        )}
        {zero && (
          <div className="mt-4 px-1.5">
            <p className="text-xs text-[var(--yak-color-text-secondary)]">
              没有匹配「{keyword.trim()}」的节点
            </p>
            <Button size="small" className="mt-2" onClick={() => setKeyword("")}>
              清除筛选
            </Button>
          </div>
        )}
      </div>
    </nav>
  );
}
