import { Alert, Button, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { useState } from "react";
import type {
  CategoryNode,
  DomainNode,
  EditorDraft,
  PlanningNode,
  SavedNode,
} from "@/service/data-modeling/warehouse-planning/types";
import DeleteDialog from "./delete-dialog";
import NodeDialog from "./node-dialog";
import PlanningDetails from "./planning-details";
import PlanningTree from "./planning-tree";
import { findParent, nodeKey, usePlanning } from "./state";

export default function WarehousePlanningPage() {
  const planning = usePlanning();
  const [draft, setDraft] = useState<EditorDraft | null>(null);
  const [deleting, setDeleting] = useState<PlanningNode | null>(null);
  const selected = planning.selected;
  const disabled = planning.loading || planning.failed;

  const createChild = (parent: CategoryNode | DomainNode) => {
    setDraft(
      parent.kind === "CATEGORY"
        ? { mode: "create", kind: "DOMAIN", parent }
        : { mode: "create", kind: "PROCESS", parent },
    );
  };
  const saved = (node: SavedNode) => {
    setDraft(null);
    planning.refresh(draft?.mode === "edit" ? undefined : nodeKey(node));
  };

  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数仓规划"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            <Button size="small" disabled={planning.loading} onClick={() => planning.refresh()}>
              刷新
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={disabled}
              onClick={() => setDraft({ mode: "create", kind: "CATEGORY", parent: null })}
            >
              新建业务分类
            </Button>
          </>
        }
      />
      {planning.failed && (
        <Alert>
          数仓规划加载失败
          <Button size="small" onClick={() => planning.refresh()}>
            重试
          </Button>
        </Alert>
      )}
      <div
        aria-busy={planning.loading}
        className="grid min-h-0 flex-1 grid-cols-1 gap-4 px-6 pb-4 pt-5 max-md:px-4 md:grid-cols-[250px_minmax(0,1fr)]"
      >
        <aside className="min-h-0 overflow-auto border-r border-[var(--yak-components-panel-border)] pr-3">
          <PlanningTree
            nodes={planning.roots}
            selectedKey={planning.selectedKey}
            disabled={disabled}
            onSelect={planning.select}
            onCreateChild={createChild}
          />
        </aside>
        <section aria-label="规划详情" className="min-h-0 min-w-0 overflow-auto">
          {planning.loading ? (
            <Spinner />
          ) : planning.failed ? null : selected ? (
            <PlanningDetails
              node={selected}
              parent={findParent(planning.roots, selected)}
              onSelect={(node) => planning.select(nodeKey(node))}
              onCreateChild={createChild}
              onDelete={setDeleting}
              onEdit={(node) =>
                setDraft({
                  mode: "edit",
                  node,
                  parent: findParent(planning.roots, node),
                })
              }
            />
          ) : (
            <p className="p-4 text-sm">暂无业务分类</p>
          )}
        </section>
      </div>
      {draft && <NodeDialog draft={draft} onClose={() => setDraft(null)} onSaved={saved} />}
      {deleting && (
        <DeleteDialog
          node={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            const parent = findParent(planning.roots, deleting);
            setDeleting(null);
            planning.refresh(parent ? nodeKey(parent) : undefined);
          }}
        />
      )}
    </div>
  );
}
