import { Button, Empty, PageHeader, Spinner, Tabs, TabsList, TabsTab } from "@yak-ops/yak-ui";
import { Folder, FolderPlus, TriangleAlert } from "lucide-react";
import { useState } from "react";
import type {
  EditorDraft,
  PlanningNode,
  SavedNode,
} from "@/service/data-modeling/warehouse-planning/types";
import DeleteDialog from "./delete-dialog";
import LayerPanel from "./layer-panel";
import NodeDialog from "./node-dialog";
import PlanningDetails from "./planning-details";
import PlanningTree from "./planning-tree";
import { findParent, nodeKey, pathToNode, usePlanning } from "./state";

type View = "plan" | "layer";

export default function WarehousePlanningPage() {
  const planning = usePlanning();
  const [draft, setDraft] = useState<EditorDraft | null>(null);
  const [deleting, setDeleting] = useState<PlanningNode | null>(null);
  const [view, setView] = useState<View>("plan");
  const selected = planning.selected;
  const disabled = planning.loading || planning.failed;

  const createChild = (parent: PlanningNode) => {
    switch (parent.kind) {
      case "CATEGORY":
        setDraft({ mode: "create", kind: "DOMAIN", parent });
        return;
      case "DOMAIN":
        setDraft({ mode: "create", kind: "PROCESS", parent });
        return;
      case "DATASET":
        setDraft({ mode: "create", kind: "SUBJECT", parent });
        return;
      default:
        return;
    }
  };
  const createDatamart = () => setDraft({ mode: "create", kind: "DATASET", parent: null });
  const saved = (node: SavedNode) => {
    setDraft(null);
    planning.refresh(draft?.mode === "edit" ? undefined : nodeKey(node));
  };

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <div className="flex min-h-0 flex-1 flex-col px-6 pt-5 max-md:px-4">
        <PageHeader
          title="数仓规划"
          description="维护公共层与应用层的业务分类、数据域、业务过程与数据集市。"
          className="pb-5"
          extra={
            view === "plan" ? (
              <>
                <Button
                  size="small"
                  variant="ghost"
                  disabled={planning.loading}
                  onClick={() => planning.refresh()}
                >
                  刷新
                </Button>
                <Button size="small" disabled={disabled} onClick={createDatamart}>
                  新建数据集市
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
            ) : null
          }
        />
        <div
          aria-busy={planning.loading}
          className="grid min-h-0 flex-1 grid-cols-1 gap-6 pb-8 md:grid-cols-[280px_minmax(0,1fr)]"
        >
          <aside className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] shadow-[0_1px_2px_0_rgb(16_24_40/4%)]">
            <div className="border-b border-[var(--yak-components-control-border)] px-3 py-2">
              <Tabs value={view} onValueChange={(value) => setView(value as View)}>
                <TabsList>
                  <TabsTab value="plan">规划</TabsTab>
                  <TabsTab value="layer">分层</TabsTab>
                </TabsList>
              </Tabs>
            </div>
            <div className="flex min-h-0 flex-1 flex-col px-3 pb-3">
              {planning.failed ? (
                <div className="mt-3 flex flex-col items-start gap-2 rounded-lg bg-[var(--yak-color-surface-muted)] p-3.5 text-[13px] text-[var(--yak-color-text-secondary)]">
                  <span>无法加载数仓规划</span>
                  <Button size="small" variant="primary" onClick={() => planning.refresh()}>
                    重试
                  </Button>
                </div>
              ) : (
                <PlanningTree
                  tree={planning.tree}
                  selectedKey={view === "plan" ? planning.selectedKey : null}
                  disabled={disabled || view === "layer"}
                  filterable={view === "plan"}
                  muted={view === "layer"}
                  onSelect={(key) => {
                    setView("plan");
                    planning.select(key);
                  }}
                  onCreateChild={createChild}
                  onCreateDatamart={createDatamart}
                />
              )}
            </div>
          </aside>
          <section
            aria-label={view === "layer" ? "数仓分层" : "规划详情"}
            className="min-h-0 min-w-0 overflow-auto"
          >
            {view === "layer" ? (
              <LayerPanel />
            ) : planning.loading ? (
              <Spinner />
            ) : planning.failed ? (
              <Empty
                icon={<TriangleAlert size={26} strokeWidth={1.5} />}
                title="数仓规划加载失败"
                description="请检查网络连接后重试。"
                action={
                  <Button size="small" variant="primary" onClick={() => planning.refresh()}>
                    重试
                  </Button>
                }
              />
            ) : selected ? (
              <PlanningDetails
                node={selected}
                ancestors={pathToNode(planning.tree, planning.selectedKey)}
                onSelect={(node) => planning.select(nodeKey(node))}
                onCreateChild={createChild}
                onDelete={setDeleting}
                onEdit={(node) =>
                  setDraft({
                    mode: "edit",
                    node,
                    parent: findParent(planning.tree, node),
                  })
                }
              />
            ) : (
              <Empty
                icon={<Folder size={26} strokeWidth={1.5} />}
                title="还没有业务分类"
                description="业务分类是公共层的起点，其下依次是数据域与业务过程。"
                action={
                  <div className="flex gap-2">
                    <Button
                      size="small"
                      variant="primary"
                      onClick={() => setDraft({ mode: "create", kind: "CATEGORY", parent: null })}
                    >
                      新建业务分类
                    </Button>
                    <Button size="small" onClick={createDatamart}>
                      <FolderPlus size={14} />
                      新建数据集市
                    </Button>
                  </div>
                }
              />
            )}
          </section>
        </div>
      </div>
      {draft && <NodeDialog draft={draft} onClose={() => setDraft(null)} onSaved={saved} />}
      {deleting && (
        <DeleteDialog
          node={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            const parent = findParent(planning.tree, deleting);
            setDeleting(null);
            planning.refresh(parent ? nodeKey(parent) : undefined);
          }}
        />
      )}
    </div>
  );
}
