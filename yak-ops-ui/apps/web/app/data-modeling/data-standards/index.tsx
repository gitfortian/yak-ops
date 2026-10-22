import { Alert, Button, Form, Input, PageHeader } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type {
  CategoryDraft,
  DeleteTarget,
  SavedStandard,
  StandardCategory,
  StandardDraft,
  StandardQuery,
} from "@/service/data-modeling/data-standards/types";
import CategoryDialog from "./category-dialog";
import CategoryTree from "./category-tree";
import DeleteDialog from "./delete-dialog";
import StandardDialog from "./standard-dialog";
import StandardTable from "./standard-table";
import { categoryPath, pathLabel, useCatalog, useStandardPage } from "./state";

export default function DataStandardsPage() {
  const catalog = useCatalog();
  const [searchText, setSearchText] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [version, setVersion] = useState(0);
  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft | null>(null);
  const [fieldDraft, setFieldDraft] = useState<StandardDraft | null>(null);
  const [deleting, setDeleting] = useState<DeleteTarget | null>(null);
  const unavailable = catalog.loading || catalog.failed;
  const query = useMemo<StandardQuery | null>(() => {
    if (unavailable) return null;
    if (keyword) return { scope: "ALL", keyword, pageNo, pageSize };
    if (catalog.selectedId)
      return { scope: "CATEGORY", categoryId: catalog.selectedId, pageNo, pageSize };
    return null;
  }, [unavailable, keyword, pageNo, pageSize, catalog.selectedId]);
  const list = useStandardPage(query, version);
  useEffect(() => {
    if (list.page && !list.loading) {
      const last = Math.max(1, list.page.pagination.pages);
      if (pageNo > last) setPageNo(last);
    }
  }, [list.page, list.loading, pageNo]);
  const clearSearch = () => {
    setSearchText("");
    setKeyword("");
    setPageNo(1);
  };
  const createCategory = (parent: StandardCategory | null) =>
    setCategoryDraft({
      mode: "create",
      parent,
      parentPath: pathLabel(catalog.roots, parent?.id ?? null),
    });
  const categorySaved = (saved: SavedStandard) => {
    if (categoryDraft?.mode === "create") {
      clearSearch();
      catalog.refresh(saved.id);
    } else catalog.refresh();
    setCategoryDraft(null);
    setVersion((value) => value + 1);
  };
  const deleted = () => {
    if (!deleting) return;
    if (deleting.kind === "category") {
      catalog.refresh(deleting.node.id === catalog.selectedId ? deleting.node.parentId : undefined);
    }
    setDeleting(null);
    setVersion((value) => value + 1);
  };
  return (
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数据标准"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Button
            size="small"
            disabled={catalog.loading}
            onClick={() => {
              catalog.refresh();
              setVersion((value) => value + 1);
            }}
          >
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
            onSelect={(id) => {
              clearSearch();
              catalog.select(id);
            }}
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
            <p className="p-3 text-sm text-[#667085]">暂无分类</p>
          )}
        </aside>
        <section aria-label="字段标准" className="flex min-h-0 min-w-0 flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Form
              className="flex max-w-full items-center gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                setKeyword(searchText.trim());
                setPageNo(1);
              }}
            >
              <Input
                size="small"
                variant="outlined"
                value={searchText}
                maxLength={128}
                disabled={unavailable}
                aria-label="搜索全部分类中的标准"
                placeholder="中文名称或英文编码"
                onChange={(event) => setSearchText(event.target.value)}
              />
              <Button size="small" type="submit" disabled={unavailable}>
                搜索
              </Button>
              {(searchText || keyword) && (
                <Button size="small" disabled={unavailable} onClick={clearSearch}>
                  清空
                </Button>
              )}
            </Form>
            <Button
              size="small"
              variant="primary"
              disabled={unavailable || !catalog.selectedId}
              onClick={() => {
                if (catalog.selectedId)
                  setFieldDraft({
                    mode: "create",
                    category: {
                      id: catalog.selectedId,
                      path: pathLabel(catalog.roots, catalog.selectedId),
                    },
                  });
              }}
            >
              新建标准
            </Button>
          </div>
          {list.failed && (
            <Alert>
              字段标准加载失败
              <Button size="small" onClick={() => setVersion((value) => value + 1)}>
                重试
              </Button>
            </Alert>
          )}
          <div className="min-h-0 min-w-0 flex-1 overflow-auto">
            <StandardTable
              page={list.failed ? null : list.page}
              pageNo={pageNo}
              pageSize={pageSize}
              loading={unavailable || list.loading}
              searching={Boolean(keyword)}
              onEdit={(standard) => setFieldDraft({ mode: "edit", standard })}
              onDelete={(node) => setDeleting({ kind: "field", node })}
              onPageChange={(page, size) => {
                setPageSize(size);
                setPageNo(size === pageSize ? page : 1);
              }}
            />
          </div>
        </section>
      </div>
      {categoryDraft && (
        <CategoryDialog
          draft={categoryDraft}
          onClose={() => setCategoryDraft(null)}
          onSaved={categorySaved}
        />
      )}
      {fieldDraft && (
        <StandardDialog
          draft={fieldDraft}
          onClose={() => setFieldDraft(null)}
          onSaved={() => {
            setFieldDraft(null);
            setVersion((value) => value + 1);
          }}
        />
      )}
      {deleting && (
        <DeleteDialog target={deleting} onClose={() => setDeleting(null)} onDeleted={deleted} />
      )}
    </div>
  );
}
