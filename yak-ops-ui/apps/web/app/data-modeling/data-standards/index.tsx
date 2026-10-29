import { Alert, Button, PageHeader } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useMemo, useState } from "react";
import type {
  CategoryDraft,
  DeleteTarget,
  SavedStandard,
  StandardCategory,
  StandardKind,
} from "@/service/data-modeling/data-standards/types";
import CategoryDialog from "./category-dialog";
import CategoryTree from "./category-tree";
import DeleteDialog from "./delete-dialog";
import FieldSection from "./field-section";
import NamingEntrySection from "./naming-entry-section";
import LookupSection from "./lookup-section";
import UnitSection from "./unit-section";
import { categoryPath, pathLabel, useCatalog } from "./state";

const TABS = [
  { key: "field", label: "字段标准" },
  { key: "lookup", label: "标准代码" },
  { key: "unit", label: "度量单位" },
  { key: "naming", label: "命名词典" },
] as const;
type TabKey = (typeof TABS)[number]["key"];

/** The kind a standard set holds, or null for a directory that accepts both. */
function lockedKind(node: StandardCategory | null): StandardKind | null {
  return node?.nodeType === "STANDARD_SET" ? (node.standardKind ?? "FIELD") : null;
}

export default function DataStandardsPage() {
  const catalog = useCatalog();
  const [tab, setTab] = useState<TabKey>("field");
  const [version, setVersion] = useState(0);
  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft | null>(null);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  const unavailable = catalog.loading || catalog.failed;
  const selected = useMemo(() => {
    const path = categoryPath(catalog.roots, catalog.selectedId);
    return path?.at(-1) ?? null;
  }, [catalog.roots, catalog.selectedId]);
  const selectedPath = pathLabel(catalog.roots, catalog.selectedId);
  const kind = lockedKind(selected);
  const changed = () => {
    setVersion((value) => value + 1);
    catalog.refresh();
  };
  const createCategory = (parent: StandardCategory | null) =>
    setCategoryDraft({
      mode: "create",
      parent,
      parentPath: pathLabel(catalog.roots, parent?.id ?? null),
    });
  const categorySaved = (saved: SavedStandard) => {
    if (categoryDraft?.mode === "create") {
      catalog.refresh(saved.id);
      setVersion((value) => value + 1);
    } else changed();
    setCategoryDraft(null);
  };
  const deleted = () => {
    if (!deleting) return;
    if (deleting.kind === "category") {
      catalog.refresh(deleting.node.id === catalog.selectedId ? deleting.node.parentId : undefined);
      setVersion((value) => value + 1);
    } else changed();
    setDeleting(null);
  };
  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数据标准"
        bordered
        className="bg-[var(--yak-color-background)] px-6 max-md:px-4"
        extra={
          <Button size="small" disabled={catalog.loading} onClick={changed}>
            刷新
          </Button>
        }
      />
      {catalog.failed && (
        <Alert>
          标准分类加载失败
          <Button size="small" onClick={() => catalog.refresh()}>
            重试
          </Button>
        </Alert>
      )}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 px-6 pb-4 pt-5 max-md:px-4 md:grid-cols-[250px_minmax(0,1fr)]">
        <aside
          className="min-h-0 overflow-auto border-r border-[var(--yak-components-panel-border)] pr-3"
          aria-busy={catalog.loading}
        >
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-medium">标准分类</h2>
            <Button
              size="small"
              variant="ghost"
              disabled={unavailable}
              aria-label="新建根目录"
              onClick={() => createCategory(null)}
            >
              <Plus size={14} />
              新建目录
            </Button>
          </div>
          <CategoryTree
            nodes={catalog.roots}
            selectedId={catalog.selectedId}
            disabled={unavailable}
            onSelect={catalog.select}
            onCreate={createCategory}
            onRename={(category) => {
              const parent = categoryPath(catalog.roots, category.id)?.at(-2);
              setCategoryDraft({
                mode: "rename",
                category,
                parentPath: pathLabel(catalog.roots, parent?.id ?? null),
              });
            }}
            onDelete={(node) => setDeleting({ kind: "category", node })}
          />
          {!catalog.loading && !catalog.failed && catalog.roots.length === 0 && (
            <p className="p-3 text-sm text-[var(--yak-color-text-secondary)]">暂无分类</p>
          )}
        </aside>
        <section className="flex min-h-0 min-w-0 flex-col gap-3">
          <div
            role="tablist"
            aria-label="标准种类"
            className="flex flex-wrap items-center gap-1 border-b border-[var(--yak-components-panel-border)]"
          >
            {TABS.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={tab === item.key}
                className={
                  "relative -mb-px border-b-2 px-3 py-2 text-sm transition-colors " +
                  (tab === item.key
                    ? "border-[var(--yak-color-primary)] font-medium [color:var(--yak-color-primary)]"
                    : "border-transparent text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-field-label)]")
                }
                onClick={() => setTab(item.key)}
              >
                {item.label}
              </button>
            ))}
          </div>
          {tab === "field" && (
            <FieldSection
              selectedId={catalog.selectedId}
              selectedPath={selectedPath}
              unavailable={unavailable}
              kindBlocked={kind !== null && kind !== "FIELD"}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
              onCatalogStale={() => catalog.refresh()}
            />
          )}
          {tab === "lookup" && (
            <LookupSection
              selectedId={catalog.selectedId}
              selectedPath={selectedPath}
              unavailable={unavailable}
              kindBlocked={kind !== null && kind !== "LOOKUP"}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {tab === "unit" && (
            <UnitSection
              unavailable={unavailable}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {tab === "naming" && (
            <NamingEntrySection
              unavailable={unavailable}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
        </section>
      </div>
      {categoryDraft && (
        <CategoryDialog
          draft={categoryDraft}
          onClose={() => setCategoryDraft(null)}
          onSaved={categorySaved}
        />
      )}
      {deleting && (
        <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onDeleted={deleted} />
      )}
    </div>
  );
}
