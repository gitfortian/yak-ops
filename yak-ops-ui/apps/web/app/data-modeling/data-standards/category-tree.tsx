import {
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight, Folder, MoreHorizontal, Plus } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type { StandardCategory } from "@/service/data-modeling/data-standards/types";
import { categoryPath } from "./state";

interface Props {
  nodes: readonly StandardCategory[];
  selectedId: string | null;
  disabled: boolean;
  onSelect: (id: string) => void;
  onCreate: (parent: StandardCategory | null) => void;
  onRename: (node: StandardCategory) => void;
  onDelete: (node: StandardCategory) => void;
}
export default function CategoryTree(props: Props) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(() => new Set());
  useEffect(() => {
    const path = new Set(categoryPath(props.nodes, props.selectedId)?.map((node) => node.id) ?? []);
    setCollapsed((previous) => new Set([...previous].filter((id) => !path.has(id))));
  }, [props.nodes, props.selectedId]);
  const toggle = (id: string) =>
    setCollapsed((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const render = (nodes: readonly StandardCategory[]): ReactNode => (
    <ul className="space-y-1">
      {nodes.map((node) => {
        const expanded = !collapsed.has(node.id);
        const selected = props.selectedId === node.id;
        const path = categoryPath(props.nodes, node.id);
        const parent = path?.at(-2) ?? null;
        const depthLimit = (path?.length ?? 0) >= 32;
        return (
          <li key={node.id}>
            <div className="group/row flex min-w-0 items-center gap-0.5">
              {node.children.length > 0 ? (
                <Button
                  variant="ghost"
                  size="small"
                  disabled={props.disabled}
                  aria-label={(expanded ? "折叠" : "展开") + node.name}
                  aria-expanded={expanded}
                  onClick={() => toggle(node.id)}
                >
                  {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                </Button>
              ) : (
                <span className="w-7 shrink-0" />
              )}
              <Button
                variant="ghost"
                size="small"
                disabled={props.disabled}
                aria-current={selected ? true : undefined}
                className={
                  "min-w-0 flex-1 justify-start " +
                  (selected
                    ? "bg-[var(--yak-color-primary-subtle)] [color:var(--yak-color-primary)] hover:bg-[var(--yak-color-primary-subtle)] hover:[color:var(--yak-color-primary)]"
                    : "")
                }
                onClick={() => props.onSelect(node.id)}
              >
                <Folder size={14} />
                <span className="truncate">{node.name}</span>
              </Button>
              <div className="flex opacity-100 md:opacity-0 md:group-hover/row:opacity-100 md:group-focus-within/row:opacity-100">
                <Button
                  variant="ghost"
                  size="small"
                  disabled={props.disabled || depthLimit}
                  aria-label={"在" + node.name + "下新建子目录"}
                  onClick={() => props.onCreate(node)}
                >
                  <Plus size={14} />
                </Button>
                <DropdownMenu>
                  <DropdownMenuTrigger
                    disabled={props.disabled}
                    aria-label={node.name + "目录操作"}
                    className="flex h-7 w-6 cursor-pointer items-center justify-center rounded outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
                  >
                    <MoreHorizontal size={14} />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent side="bottom" align="end">
                    <DropdownMenuItem onClick={() => props.onCreate(parent)}>
                      新建同级目录
                    </DropdownMenuItem>
                    <DropdownMenuItem disabled={depthLimit} onClick={() => props.onCreate(node)}>
                      新建子目录
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => props.onRename(node)}>重命名</DropdownMenuItem>
                    <DropdownMenuItem tone="danger" onClick={() => props.onDelete(node)}>
                      删除
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
            {expanded && node.children.length > 0 && (
              <div className="ml-4">{render(node.children)}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
  return <nav aria-label="标准分类目录">{render(props.nodes)}</nav>;
}
