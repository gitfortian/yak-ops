import { Input } from "@yak-ops/yak-ui";
import { ChevronRight, FileText, Folder, FolderOpen, FolderPlus, Plus, Search, X } from "lucide-react";
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
    <ul className="space-y-0.5">
      {nodes.map((node) => {
        const key = nodeKey(node);
        if (filtering && !hits.has(key)) return null;
        const expanded = filtering || !collapsed.has(key);
        const selected = props.selectedKey === key;
        const child = CHILD[node.kind];
        const leaf = isLeaf(node.kind);
        const open = !leaf && expanded && node.children.length > 0;
        return (
          <li key={key}>
            <div
              className={
                "group/row mx-1 flex min-w-0 items-center gap-0.5 rounded-lg pr-1 transition-colors duration-150 " +
                (selected
                  ? "bg-[var(--yak-color-primary-subtle)]"
                  : "hover:bg-[var(--yak-color-surface-muted)]")
              }
            >
              {node.children.length > 0 ? (
                <button
                  type="button"
                  disabled={props.disabled}
                  aria-label={`${expanded ? "折叠" : "展开"}${node.name}`}
                  aria-expanded={expanded}
                  className="grid h-8 w-6 shrink-0 place-items-center rounded-md text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)] disabled:opacity-45"
                  onClick={() => toggle(key)}
                >
                  <ChevronRight
                    size={13}
                    strokeWidth={1.75}
                    className={"transition-transform duration-150 " + (expanded ? "rotate-90" : "")}
                  />
                </button>
              ) : (
                <span className="w-6 shrink-0" />
              )}
              <button
                type="button"
                disabled={props.disabled}
                aria-current={selected ? true : undefined}
                className={
                  "flex h-8 min-w-0 flex-1 items-center gap-2 rounded-md px-1 text-left text-[13px] transition-colors disabled:cursor-not-allowed disabled:opacity-45 " +
                  (selected
                    ? "font-medium text-[var(--yak-color-primary)]"
                    : "text-[var(--yak-color-text-primary)]")
                }
                onClick={() => props.onSelect(key)}
              >
                {leaf ? (
                  <FileText
                    size={15}
                    strokeWidth={1.75}
                    className={
                      "shrink-0 " +
                      (selected
                        ? "text-[var(--yak-color-primary)]"
                        : "text-[var(--yak-components-muted-text)]")
                    }
                  />
                ) : open ? (
                  <FolderOpen size={15} strokeWidth={1.75} className="shrink-0 text-[var(--yak-color-primary)]" />
                ) : (
                  <Folder
                    size={15}
                    strokeWidth={1.75}
                    className={
                      "shrink-0 " +
                      (selected
                        ? "text-[var(--yak-color-primary)]"
                        : "text-[var(--yak-components-muted-text)]")
                    }
                  />
                )}
                <span className="truncate">{node.name}</span>
              </button>
              {child && (
                <button
                  type="button"
                  disabled={props.disabled}
                  aria-label={`在${node.name}下新建${child.label}`}
                  className="grid h-6 w-6 shrink-0 place-items-center rounded-md text-[var(--yak-components-muted-text)] opacity-0 transition-all hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)] focus-visible:opacity-100 disabled:opacity-0 md:group-hover/row:opacity-100"
                  onClick={() => props.onCreateChild(node)}
                >
                  <Plus size={13} />
                </button>
              )}
            </div>
            {expanded && node.children.length > 0 && (
              <div className="ml-[13px] space-y-0.5 border-l border-[var(--yak-components-control-border)] pl-[7px]">
                {renderNodes(node.children)}
              </div>
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
        className="mt-4 border-t border-[var(--yak-components-control-border)] pt-3 first:mt-0 first:border-t-0 first:pt-0"
      >
        <p className="flex h-5 items-center px-2 text-[11px] font-medium tracking-[0.04em] text-[var(--yak-components-muted-text)]">
          {label}
        </p>
        <div className="mt-1.5">{visible.length > 0 ? renderNodes(visible) : empty}</div>
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
          <div className="relative mt-3 [&_input]:h-8 [&_input]:text-[13px]">
            <Search
              size={14}
              aria-hidden="true"
              className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
            />
            <Input
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
          <div className="mx-1 flex flex-col items-center gap-2 rounded-lg bg-[var(--yak-color-surface-muted)] px-3 py-5 text-center">
            <FolderPlus
              size={16}
              strokeWidth={1.6}
              className="text-[var(--yak-components-muted-text)]"
            />
            <p className="text-[12px] leading-4 text-[var(--yak-components-muted-text)]">
              暂无业务分类
            </p>
          </div>,
        )}
        {group(
          "应用层 · 数据集市",
          props.tree.datamarts,
          <div className="mx-1 flex flex-col items-center gap-2 rounded-lg bg-[var(--yak-color-surface-muted)] px-3 py-5 text-center">
            <FolderPlus
              size={16}
              strokeWidth={1.6}
              className="text-[var(--yak-components-muted-text)]"
            />
            <p className="text-[12px] leading-4 text-[var(--yak-components-muted-text)]">
              暂无数据集市
            </p>
            <button
              type="button"
              className="text-[12px] font-medium text-[var(--yak-color-primary)] transition-opacity hover:opacity-70"
              onClick={props.onCreateDatamart}
            >
              新建数据集市
            </button>
          </div>,
        )}
        {zero && (
          <div className="mx-1 mt-3 flex flex-col items-center gap-2 rounded-lg bg-[var(--yak-color-surface-muted)] px-3 py-5 text-center">
            <Search size={16} strokeWidth={1.6} className="text-[var(--yak-components-muted-text)]" />
            <p className="text-[12px] leading-4 text-[var(--yak-components-muted-text)]">
              没有匹配「{keyword.trim()}」的节点
            </p>
            <button
              type="button"
              className="text-[12px] font-medium text-[var(--yak-color-primary)] transition-opacity hover:opacity-70"
              onClick={() => setKeyword("")}
            >
              清除筛选
            </button>
          </div>
        )}
      </div>
    </nav>
  );
}
