import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@yak-ops/yak-ui";
import { ChevronRight, Folder, FolderOpen, MoreHorizontal, Plus } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type { StandardCategory } from "@/service/data-modeling/data-standards/types";
import { STANDARD_KIND_LABEL } from "@/service/data-modeling/data-standards/types";
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
    <ul className="space-y-0.5">
      {nodes.map((node) => {
        const expanded = !collapsed.has(node.id);
        const selected = props.selectedId === node.id;
        const path = categoryPath(props.nodes, node.id);
        const parent = path?.at(-2) ?? null;
        const depthLimit = (path?.length ?? 0) >= 32;
        const hasChildren = node.children.length > 0;
        return (
          <li key={node.id}>
            <div
              className={
                "group/row mx-1 flex min-w-0 items-center gap-0.5 rounded-lg pr-1 transition-colors duration-150 " +
                (selected
                  ? "bg-[var(--yak-color-primary-subtle)]"
                  : "hover:bg-[var(--yak-color-surface-muted)]")
              }
            >
              {hasChildren ? (
                <button
                  type="button"
                  disabled={props.disabled}
                  aria-label={(expanded ? "折叠" : "展开") + node.name}
                  aria-expanded={expanded}
                  className="grid h-8 w-6 shrink-0 place-items-center rounded-md text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)] disabled:opacity-45"
                  onClick={() => toggle(node.id)}
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
                onClick={() => props.onSelect(node.id)}
              >
                {hasChildren && expanded ? (
                  <FolderOpen
                    size={15}
                    strokeWidth={1.75}
                    className="shrink-0 text-[var(--yak-color-primary)]"
                  />
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
                {node.nodeType === "STANDARD_SET" && (
                  <span className="ml-0.5 shrink-0 rounded-full bg-[var(--yak-color-surface-muted)] px-1.5 text-[10px] leading-4 font-normal text-[var(--yak-components-muted-text)]">
                    {node.standardKind ? STANDARD_KIND_LABEL[node.standardKind] : "标准集"}
                  </span>
                )}
              </button>
              <div className="flex shrink-0 opacity-100 transition-opacity md:opacity-0 md:group-hover/row:opacity-100 md:group-focus-within/row:opacity-100">
                <button
                  type="button"
                  disabled={props.disabled || depthLimit}
                  aria-label={"在" + node.name + "下新建子目录"}
                  className="grid h-6 w-6 place-items-center rounded-md text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)] disabled:opacity-35"
                  onClick={() => props.onCreate(node)}
                >
                  <Plus size={13} />
                </button>
                <DropdownMenu>
                  <DropdownMenuTrigger
                    disabled={props.disabled}
                    aria-label={node.name + "目录操作"}
                    className="grid h-6 w-6 cursor-pointer place-items-center rounded-md text-[var(--yak-components-muted-text)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)] focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary-ring)]"
                  >
                    <MoreHorizontal size={13} />
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
            {expanded && hasChildren && (
              <div className="ml-[13px] space-y-0.5 border-l border-[var(--yak-components-control-border)] pl-[7px]">
                {render(node.children)}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
  return <nav aria-label="标准分类目录">{render(props.nodes)}</nav>;
}
