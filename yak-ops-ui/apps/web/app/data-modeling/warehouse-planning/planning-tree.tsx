import { Button } from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, FileText, Folder, Plus } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type {
  CategoryNode,
  DomainNode,
  NodeKey,
  PlanningNode,
} from "@/service/data-modeling/warehouse-planning/types";
import { ancestorKeys, nodeKey } from "./state";

interface Props {
  nodes: readonly CategoryNode[];
  selectedKey: NodeKey | null;
  disabled: boolean;
  onSelect: (key: NodeKey) => void;
  onCreateChild: (parent: CategoryNode | DomainNode) => void;
}

export default function PlanningTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<NodeKey>>(() => new Set());

  useEffect(() => {
    const ancestors = new Set(ancestorKeys(props.nodes, props.selectedKey) ?? []);
    setCollapsed((previous) => new Set([...previous].filter((key) => !ancestors.has(key))));
  }, [props.nodes, props.selectedKey]);

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
        const childLabel = node.kind === "CATEGORY" ? "数据域" : "业务过程";
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
                {node.kind === "PROCESS" ? <FileText size={14} /> : <Folder size={14} />}
                <span className="truncate">{node.name}</span>
              </Button>
              {node.kind !== "PROCESS" && (
                <Button
                  size="small"
                  variant="ghost"
                  disabled={props.disabled}
                  className="opacity-100 md:opacity-0 md:group-hover/row:opacity-100 md:group-focus-within/row:opacity-100"
                  aria-label={`在${node.name}下新建${childLabel}`}
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

  return <nav aria-label="数仓规划目录">{renderNodes(props.nodes)}</nav>;
}
