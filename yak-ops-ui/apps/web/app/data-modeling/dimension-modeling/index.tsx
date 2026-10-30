import { Alert, Button, Empty, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getModels } from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  LogicalModel,
} from "@/service/data-modeling/dimension-modeling/types";
import { getOwners } from "@/service/data-modeling/warehouse-planning";
import { getLayers, type Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type { OwnerOption } from "@/service/data-modeling/warehouse-planning/types";
import DimensionDeleteDialog from "../dimensions/delete-dialog";
import DimensionDialog from "../dimensions/dimension-dialog";
import ModelDeleteDialog from "../logical-models/delete-dialog";
import PublishDialog from "../logical-models/publish-dialog";
import ModelPanel from "./model-panel";
import {
  EMPTY_MODEL_SEARCH,
  isModelSearching,
  matchesModelFilters,
  type ModelSearchFilters,
} from "./filters";
import ModelSearchBar from "./search";
import { useDimensionModelingCatalog } from "./state";
import { BizTreePanel, LayerTreePanel, nodeHeading, PublishTreePanel } from "./workbench-panels";
import {
  modelsOfNode,
  nodeKeyForView,
  nodeKeyOf,
  parseWorkbenchView,
  pinnedScopeOf,
  resolveNode,
  WORKBENCH_VIEW_LABEL,
  WORKBENCH_VIEWS,
  type Selection,
  type WorkbenchView,
} from "./workbench-views";

type DimensionDraft =
  | { mode: "create"; scope: "PUBLIC" | "APPLICATION"; scopeId: string }
  | { mode: "edit"; dimension: ConceptDimension };

export default function DimensionModelingPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const catalog = useDimensionModelingCatalog();
  // The view and the selected node are shareable list state, so both live in the URL; the filters
  // stay page state, matching the search bar they feed.
  const view = parseWorkbenchView(searchParams.get("view"));
  const nodeParam = searchParams.get("node");
  const [dimensionDraft, setDimensionDraft] = useState<DimensionDraft | null>(null);
  const [deletingDimension, setDeletingDimension] = useState<ConceptDimension | null>(null);
  const [deletingModel, setDeletingModel] = useState<LogicalModel | null>(null);
  const [publishingModel, setPublishingModel] = useState<LogicalModel | null>(null);
  const [filters, setFilters] = useState<ModelSearchFilters>(EMPTY_MODEL_SEARCH);
  const [searchPage, setSearchPage] = useState(1);
  const [searchPageSize, setSearchPageSize] = useState(20);
  const [searchResult, setSearchResult] = useState<{
    items: LogicalModel[];
    total: number;
  } | null>(null);
  const [searchStatus, setSearchStatus] = useState<"idle" | "loading" | "ready" | "failed">("idle");
  const [searchVersion, setSearchVersion] = useState(0);
  const [searchOptions, setSearchOptions] = useState<{
    status: "loading" | "ready" | "failed";
    layers: Layer[];
    owners: OwnerOption[];
  }>({ status: "loading", layers: [], owners: [] });
  const filtered = isModelSearching(filters);

  const optionsReady = searchOptions.status === "ready";
  const selected = useMemo<Selection>(() => {
    // A node key can only resolve once the data it names exists; until then the page shows the
    // root instead of half a panel.
    if (catalog.status !== "ready" || !optionsReady) return { kind: "ROOT" };
    const key = nodeKeyForView(nodeParam, view);
    return (
      resolveNode(key, catalog.tree, catalog.dimensions, searchOptions.layers) ?? {
        kind: "ROOT",
      }
    );
  }, [
    nodeParam,
    view,
    catalog.status,
    catalog.tree,
    catalog.dimensions,
    optionsReady,
    searchOptions.layers,
  ]);

  // The root is the only node without a scope, so only there does a filter become a query over the
  // whole Workspace: a node already holds its models and narrows them in place.
  const searching = filtered && selected.kind === "ROOT";

  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([getLayers(controller.signal), getOwners(controller.signal)])
      .then(([layers, owners]) => {
        if (!controller.signal.aborted) setSearchOptions({ status: "ready", layers, owners });
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setSearchOptions((previous) => ({ ...previous, status: "failed" }));
        }
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!searching) {
      setSearchResult(null);
      setSearchStatus("idle");
      return;
    }
    const controller = new AbortController();
    setSearchStatus("loading");
    void getModels(
      {
        type: filters.type,
        layerId: filters.layerId,
        keyword: filters.keyword.trim(),
        ownerId: filters.ownerId,
        domainId: filters.domainId,
        datamartId: filters.datamartId,
        pageNo: searchPage,
        pageSize: searchPageSize,
      },
      controller.signal,
    )
      .then((page) => {
        if (controller.signal.aborted) return;
        setSearchResult({ items: page.bizData, total: page.pagination.total });
        setSearchStatus("ready");
      })
      .catch(() => {
        if (!controller.signal.aborted) setSearchStatus("failed");
      });
    return () => controller.abort();
  }, [filters, searchPage, searchPageSize, searching, searchVersion]);

  const changeFilters = (next: ModelSearchFilters) => {
    setFilters(next);
    setSearchPage(1);
  };

  // Deleting the last row of the last page leaves a page the server no longer has.
  useEffect(() => {
    if (!searchResult) return;
    const last = Math.max(1, Math.ceil(searchResult.total / searchPageSize));
    if (searchPage > last) setSearchPage(last);
  }, [searchResult, searchPage, searchPageSize]);

  const patchParams = (mutate: (params: URLSearchParams) => void) => {
    const params = new URLSearchParams(searchParams);
    mutate(params);
    setSearchParams(params, { replace: true });
  };
  const setView = (next: WorkbenchView) =>
    patchParams((params) => {
      params.set("view", next);
      // A node the new view cannot render would highlight nothing, so the switch lands on root.
      params.set("node", nodeKeyForView(params.get("node"), next));
    });
  const select = (next: Selection) => {
    patchParams((params) => params.set("node", nodeKeyOf(next)));
    // A global domain or data mart filter would contradict the new node's own ownership, so those
    // two are cleared; keyword, type, layer and owner keep narrowing within the node.
    setFilters((current) =>
      current.domainId || current.datamartId
        ? { ...current, domainId: "", datamartId: "" }
        : current,
    );
  };

  const openEditor = (params: Record<string, string>) => {
    const query = new URLSearchParams(params);
    navigate(`/data-modeling/logical-models/new?${query.toString()}`);
  };
  const editModel = (model: LogicalModel) =>
    navigate(`/data-modeling/logical-models/${encodeURIComponent(model.id)}`);

  const nodeModels = useMemo(
    () => modelsOfNode(catalog.models, selected),
    [catalog.models, selected],
  );
  const visibleModels = filtered
    ? nodeModels.filter((model) => matchesModelFilters(model, filters))
    : nodeModels;
  const panel = nodeHeading(selected);
  const pinned = useMemo(() => pinnedScopeOf(selected), [selected]);

  const actions = (() => {
    switch (selected.kind) {
      case "PROCESS":
        return (
          <>
            <Button
              size="small"
              variant="primary"
              onClick={() =>
                openEditor({
                  type: "DWD",
                  categoryId: selected.category.id,
                  domainId: selected.domain.id,
                  processId: selected.process.id,
                })
              }
            >
              <Plus size={14} />
              新建明细表
            </Button>
            <Button
              size="small"
              onClick={() =>
                openEditor({
                  type: "DWS",
                  categoryId: selected.category.id,
                  domainId: selected.domain.id,
                  processId: selected.process.id,
                })
              }
            >
              <Plus size={14} />
              新建汇总表
            </Button>
          </>
        );
      case "DIMENSION":
        return (
          <>
            <Button
              size="small"
              variant="primary"
              onClick={() =>
                openEditor({
                  type: "DIM",
                  categoryId: selected.dimension.domainId
                    ? (catalog.tree.categories.find((category) =>
                        category.children.some(
                          (domain) => domain.id === selected.dimension.domainId,
                        ),
                      )?.id ?? "")
                    : (catalog.tree.categories[0]?.id ?? ""),
                  scope: selected.dimension.scope,
                  domainId: selected.dimension.domainId ?? "",
                  datamartId: selected.dimension.datamartId ?? "",
                  dimensionId: selected.dimension.id,
                  locked: "1",
                })
              }
            >
              <Plus size={14} />
              新建维度表
            </Button>
            <Button
              size="small"
              onClick={() => setDimensionDraft({ mode: "edit", dimension: selected.dimension })}
            >
              编辑维度
            </Button>
            <Button
              size="small"
              variant="danger"
              onClick={() => setDeletingDimension(selected.dimension)}
            >
              删除维度
            </Button>
          </>
        );
      case "DATAMART":
        return (
          <>
            <Button
              size="small"
              variant="primary"
              onClick={() =>
                openEditor({
                  type: "DIM",
                  scope: "APPLICATION",
                  categoryId: catalog.tree.categories[0]?.id ?? "",
                  datamartId: selected.datamart.id,
                })
              }
            >
              <Plus size={14} />
              新建维度表
            </Button>
            <Button
              size="small"
              onClick={() =>
                openEditor({
                  type: "ADS",
                  categoryId: catalog.tree.categories[0]?.id ?? "",
                  datamartId: selected.datamart.id,
                })
              }
            >
              <Plus size={14} />
              新建应用表
            </Button>
            <Button
              size="small"
              onClick={() =>
                setDimensionDraft({
                  mode: "create",
                  scope: "APPLICATION",
                  scopeId: selected.datamart.id,
                })
              }
            >
              新建维度
            </Button>
          </>
        );
      case "DOMAIN":
        return (
          <>
            <Button
              size="small"
              variant="primary"
              onClick={() =>
                openEditor({
                  type: "DWD",
                  categoryId: selected.category.id,
                  domainId: selected.domain.id,
                })
              }
            >
              <Plus size={14} />
              新建明细表
            </Button>
            <Button
              size="small"
              onClick={() =>
                openEditor({
                  type: "DWS",
                  categoryId: selected.category.id,
                  domainId: selected.domain.id,
                })
              }
            >
              <Plus size={14} />
              新建汇总表
            </Button>
            <Button
              size="small"
              onClick={() =>
                setDimensionDraft({
                  mode: "create",
                  scope: "PUBLIC",
                  scopeId: selected.domain.id,
                })
              }
            >
              新建维度
            </Button>
          </>
        );
      default:
        return (
          <Button
            size="small"
            variant="primary"
            onClick={() =>
              selected.kind === "CATEGORY"
                ? openEditor({ categoryId: selected.category.id })
                : navigate("/data-modeling/logical-models/new")
            }
          >
            <Plus size={14} />
            新建模型
          </Button>
        );
    }
  })();

  const resetSelectionAfterDelete = () => {
    if (selected.kind === "DIMENSION") select({ kind: "ROOT" });
  };

  const treeEmpty =
    catalog.status === "ready" &&
    catalog.tree.categories.length === 0 &&
    catalog.tree.datamarts.length === 0;

  const leftPanel = (() => {
    if (catalog.status === "loading") {
      return (
        <div className="flex h-32 items-center justify-center">
          <Spinner />
        </div>
      );
    }
    if (view === "layer") {
      if (searchOptions.status === "loading") {
        return (
          <div className="flex h-32 items-center justify-center">
            <Spinner />
          </div>
        );
      }
      if (searchOptions.status === "failed") {
        return <Empty description="分层加载失败，请刷新重试" />;
      }
      return (
        <LayerTreePanel
          layers={searchOptions.layers}
          models={catalog.models}
          selected={selected}
          onSelect={select}
        />
      );
    }
    if (view === "biz") {
      if (treeEmpty) {
        return (
          <Empty description="还没有业务分类或数据集市，请先到「数仓规划」建立分层与业务树。" />
        );
      }
      return (
        <BizTreePanel
          tree={catalog.tree}
          dimensions={catalog.dimensions}
          models={catalog.models}
          selected={selected}
          onSelect={select}
        />
      );
    }
    // The publish view groups by publication outcome, which exists even without planning data.
    return <PublishTreePanel models={catalog.models} selected={selected} onSelect={select} />;
  })();

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="维度建模"
        description="设计维度模型与事实表，并维护字段映射关系。"
        className="px-6 pb-5 pt-5 max-md:px-4"
        extra={
          <div className="flex items-center gap-2">
            <Button
              size="small"
              onClick={() => navigate("/data-modeling/dimension-modeling/reverse")}
            >
              逆向建模
            </Button>
            <Button size="small" disabled={catalog.status === "loading"} onClick={catalog.reload}>
              刷新
            </Button>
          </div>
        }
      />
      {catalog.status === "failed" && (
        <div className="px-6 pt-4 max-md:px-4">
          <Alert>
            规划树、概念维度或逻辑模型加载失败
            <Button size="small" onClick={catalog.reload}>
              重试
            </Button>
          </Alert>
        </div>
      )}
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-6 px-6 pb-6 max-md:px-4 md:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="min-h-0 overflow-auto rounded-xl border border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] p-2 shadow-[0_1px_2px_0_rgb(16_24_40/4%)]">
          <div className="mb-1 flex gap-1 rounded-lg bg-[var(--yak-color-surface-muted)] p-1">
            {WORKBENCH_VIEWS.map((item) => (
              <button
                key={item}
                type="button"
                aria-pressed={view === item}
                onClick={() => setView(item)}
                className={[
                  "flex-1 rounded-md py-1.5 text-center text-xs transition-colors duration-150",
                  view === item
                    ? "bg-[var(--yak-components-panel-bg)] font-medium text-[var(--yak-color-text-primary)] shadow-[0_1px_2px_0_rgb(16_24_40/6%)]"
                    : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-text-primary)]",
                ].join(" ")}
              >
                {WORKBENCH_VIEW_LABEL[item]}
              </button>
            ))}
          </div>
          {leftPanel}
        </aside>
        <div className="flex min-h-0 min-w-0 flex-col gap-3">
          <ModelSearchBar
            filters={filters}
            layers={searchOptions.layers}
            owners={searchOptions.owners}
            tree={catalog.tree}
            pinnedDomain={pinned.domain}
            pinnedDatamart={pinned.datamart}
            optionsFailed={searchOptions.status === "failed"}
            onChange={changeFilters}
            onResetNode={() => select({ kind: "ROOT" })}
          />
          {searching ? (
            <ModelPanel
              title="检索结果"
              models={searchResult?.items ?? []}
              loading={searchStatus === "loading"}
              emptyText="没有匹配的逻辑模型"
              error={
                searchStatus === "failed" ? (
                  <span className="flex items-center gap-2">
                    检索失败
                    <Button size="small" onClick={() => setSearchVersion((value) => value + 1)}>
                      重试
                    </Button>
                  </span>
                ) : undefined
              }
              paging={{
                pageNo: searchPage,
                pageSize: searchPageSize,
                total: searchResult?.total ?? 0,
                onChange: (page, size) => {
                  setSearchPage(page);
                  setSearchPageSize(size);
                },
              }}
              onEdit={editModel}
              onDelete={setDeletingModel}
              onPublish={setPublishingModel}
            />
          ) : (
            <ModelPanel
              title={panel.title}
              description={panel.description}
              tags={panel.tags}
              models={visibleModels}
              loading={catalog.status === "loading"}
              truncated={catalog.truncated}
              actions={actions}
              emptyText={filtered ? "没有匹配的逻辑模型" : undefined}
              onEdit={editModel}
              onDelete={setDeletingModel}
              onPublish={setPublishingModel}
            />
          )}
        </div>
      </div>
      {dimensionDraft && (
        <DimensionDialog
          existing={dimensionDraft.mode === "edit" ? dimensionDraft.dimension : null}
          initial={
            dimensionDraft.mode === "create"
              ? { scope: dimensionDraft.scope, scopeId: dimensionDraft.scopeId }
              : undefined
          }
          tree={catalog.tree}
          onClose={() => setDimensionDraft(null)}
          onSaved={() => {
            setDimensionDraft(null);
            catalog.reload();
          }}
        />
      )}
      {deletingDimension && (
        <DimensionDeleteDialog
          dimension={deletingDimension}
          boundModelCount={
            catalog.models.filter((model) => model.dimensionId === deletingDimension.id).length
          }
          onClose={() => setDeletingDimension(null)}
          onDeleted={() => {
            setDeletingDimension(null);
            resetSelectionAfterDelete();
            catalog.reload();
          }}
        />
      )}
      {deletingModel && (
        <ModelDeleteDialog
          model={deletingModel}
          onClose={() => setDeletingModel(null)}
          onDeleted={() => {
            setDeletingModel(null);
            catalog.reload();
            setSearchVersion((value) => value + 1);
          }}
        />
      )}
      {publishingModel && (
        <PublishDialog
          model={publishingModel}
          onClose={() => setPublishingModel(null)}
          onPublished={catalog.reload}
        />
      )}
    </div>
  );
}
