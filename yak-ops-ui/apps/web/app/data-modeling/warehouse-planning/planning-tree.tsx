import { Button, Input } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, FileText, Folder, Plus } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
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

/** A node plus the children that survived the filter; the tree is rendered from these. */
interface VisibleNode {
  node: PlanningNode;
  children: VisibleNode[];
}

const matchesKeyword = (node: PlanningNode, term: string) =>
  [node.name, node.enName ?? "", node.code].some((value) => value.toLowerCase().includes(term));

const unfiltered = (nodes: readonly PlanningNode[]): VisibleNode[] =>
  nodes.map((node) => ({ node, children: unfiltered(node.children) }));

/**
 * Keeps every matching node and the ancestors leading to it, so filtering never breaks the
 * hierarchy. A node that matches by name is shown with its whole subtree, because the user is
 * looking for the node, not for its children.
 */
function filterNodes(nodes: readonly PlanningNode[], term: string): VisibleNode[] {
  if (!term) return unfiltered(nodes);
  const filtered: VisibleNode[] = [];
  for (const node of nodes) {
    if (matchesKeyword(node, term)) {
      filtered.push({ node, children: unfiltered(node.children) });
      continue;
    }
    const children = filterNodes(node.children, term);
    if (children.length > 0) filtered.push({ node, children });
  }
  return filtered;
}

export default function PlanningTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<NodeKey>>(() => new Set());
  const [keyword, setKeyword] = useState("");
  const term = keyword.trim().toLowerCase();
  const filtering = term.length > 0;
  const visible = useMemo(
    () => ({
      categories: filterNodes(props.tree.categories, term),
      datamarts: filterNodes(props.tree.datamarts, term),
    }),
    [props.tree, term],
  );

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

  const renderNodes = (nodes: readonly VisibleNode[]): ReactNode => (
    <ul className="space-y-1">
      {nodes.map(({ node, children }) => {
        const key = nodeKey(node);
        // While filtering, every surviving branch is open: the point of the search is to reveal the
        // match, and a match hidden inside a collapsed branch would look like no result at all.
        const expanded = filtering || !collapsed.has(key);
        const child = CHILD[node.kind];
        return (
          <li key={key}>
            <div className="group/row flex min-w-0 items-center gap-1">
              {children.length > 0 ? (
                <Button
                  size="small"
                  variant="ghost"
                  disabled={props.disabled || filtering}
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
            {expanded && children.length > 0 && <div className="ml-5">{renderNodes(children)}</div>}
          </li>
        );
      })}
    </ul>
  );

  return (
    <nav aria-label="数仓规划目录" className="space-y-4">
      <Input
        size="small"
        variant="outlined"
        value={keyword}
        maxLength={64}
        aria-label="搜索数仓规划"
        placeholder="搜索名称或编码"
        onChange={(event) => setKeyword(event.target.value)}
      />
      <section aria-label="公共层业务分类">
        <p className="mb-1 px-1 text-xs text-[var(--yak-color-text-secondary)]">
          公共层 · 业务分类
        </p>
        {visible.categories.length > 0 ? (
          renderNodes(visible.categories)
        ) : (
          <p className="px-1 text-xs text-[var(--yak-components-muted-text)]">
            {filtering ? "无匹配的业务分类" : "暂无业务分类"}
          </p>
        )}
      </section>
      <section aria-label="应用层数据集市">
        <p className="mb-1 px-1 text-xs text-[var(--yak-color-text-secondary)]">
          应用层 · 数据集市
        </p>
        {visible.datamarts.length > 0 ? (
          renderNodes(visible.datamarts)
        ) : (
          <p className="px-1 text-xs text-[var(--yak-components-muted-text)]">
            {filtering ? "无匹配的数据集市" : "暂无数据集市"}
          </p>
        )}
      </section>
    </nav>
  );
}
