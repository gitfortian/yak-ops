import { Alert, Button, Empty, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { Box, Folder, Layers, Plus, Table2, Workflow } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { getModels } from "@/service/data-modeling/dimension-modeling";
import type {
  ConceptDimension,
  LogicalModel,
} from "@/service/data-modeling/dimension-modeling/types";
import { getOwners } from "@/service/data-modeling/warehouse-planning";
import { getLayers, type Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type {
  CategoryNode,
  DatamartNode,
  DomainNode,
  OwnerOption,
  ProcessNode,
} from "@/service/data-modeling/warehouse-planning/types";
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
import ModelSearchBar, { type PinnedScope } from "./search";
import { useDimensionModelingCatalog } from "./state";

type Selection =
  | { kind: "ROOT" }
  | { kind: "CATEGORY"; category: CategoryNode }
  | { kind: "DOMAIN"; category: CategoryNode; domain: DomainNode }
  | { kind: "DIMENSION"; dimension: ConceptDimension }
  | { kind: "PROCESS"; category: CategoryNode; domain: DomainNode; process: ProcessNode }
  | { kind: "DATAMART"; datamart: DatamartNode };

type DimensionDraft =
  | { mode: "create"; scope: "PUBLIC" | "APPLICATION"; scopeId: string }
  | { mode: "edit"; dimension: ConceptDimension };

function sameNode(left: Selection, right: Selection) {
  if (left.kind !== right.kind) return false;
  switch (left.kind) {
    case "ROOT":
      return true;
    case "CATEGORY":
      return right.kind === "CATEGORY" && left.category.id === right.category.id;
    case "DOMAIN":
      return right.kind === "DOMAIN" && left.domain.id === right.domain.id;
    case "DIMENSION":
      return right.kind === "DIMENSION" && left.dimension.id === right.dimension.id;
    case "PROCESS":
      return right.kind === "PROCESS" && left.process.id === right.process.id;
    case "DATAMART":
      return right.kind === "DATAMART" && left.datamart.id === right.datamart.id;
  }
}

type TreeIcon = "root" | "folder" | "dimension" | "process" | "model";

const TREE_ICONS = {
  root: Layers,
  folder: Folder,
  dimension: Box,
  process: Workflow,
  model: Table2,
} as const;

function TreeItem(props: {
  label: string;
  meta?: string;
  level: number;
  active?: boolean;
  icon?: TreeIcon;
  onClick: () => void;
}) {
  const Icon = TREE_ICONS[props.icon ?? "folder"];
  return (
    <button
      type="button"
      onClick={props.onClick}
      style={{ paddingLeft: 8 + props.level * 10 }}
      className={[
        "mx-1 flex h-8 w-[calc(100%-0.5rem)] items-center gap-2 rounded-lg pr-2 text-left text-[13px] transition-colors duration-150",
        props.active
          ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
          : "text-[var(--yak-color-text-primary)] hover:bg-[var(--yak-color-surface-muted)]",
      ].join(" ")}
    >
      <Icon
        size={15}
        strokeWidth={1.75}
        className={
          "shrink-0 " +
          (props.active
            ? "text-[var(--yak-color-primary)]"
            : "text-[var(--yak-components-muted-text)]")
        }
      />
      <span className="truncate">{props.label}</span>
      {props.meta && (
        <span className="ml-auto shrink-0 text-xs font-normal text-[var(--yak-components-muted-text)]">
          {props.meta}
        </span>
      )}
    </button>
  );
}

function GroupLabel({ children, level }: { children: ReactNode; level: number }) {
  return (
    <div
      className="pb-1 pt-2.5 text-[11px] font-medium tracking-[0.04em] text-[var(--yak-components-muted-text)]"
      style={{ paddingLeft: 8 + level * 12 }}
    >
      {children}
    </div>
  );
}

/**
 * Ownership the selected node already fixes.
 *
 * <p>A model owns at most one of a data domain or a data mart, so a node pins one of them and never
 * both; root and category nodes pin nothing.
 */
function pinnedScopeOf(selection: Selection): {
  domain: PinnedScope | null;
  datamart: PinnedScope | null;
} {
  switch (selection.kind) {
    case "DOMAIN":
    case "PROCESS":
      return { domain: { id: selection.domain.id, name: selection.domain.name }, datamart: null };
    case "DATAMART":
      return {
        domain: null,
        datamart: { id: selection.datamart.id, name: selection.datamart.name },
      };
    case "DIMENSION":
      return selection.dimension.domainId
        ? {
            domain: {
              id: selection.dimension.domainId,
              name: selection.dimension.domainName ?? selection.dimension.domainId,
            },
            datamart: null,
          }
        : {
            domain: null,
            datamart: selection.dimension.datamartId
              ? {
                  id: selection.dimension.datamartId,
                  name: selection.dimension.datamartName ?? selection.dimension.datamartId,
                }
              : null,
          };
    default:
      return { domain: null, datamart: null };
  }
}

/** What the right-hand panel says about the selected node. */
interface PanelContent {
  title: string;
  /** Owner labels the header shows as tags; only the dimension node has any. */
  tags?: string[];
  description: string;
  models: LogicalModel[];
}

export default function DimensionModelingPage() {
  const navigate = useNavigate();
  const catalog = useDimensionModelingCatalog();
  const [selected, setSelected] = useState<Selection>({ kind: "ROOT" });
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

  const dimensionsByOwner = useMemo(() => {
    const grouped = new Map<string, ConceptDimension[]>();
    for (const dimension of catalog.dimensions) {
      const key = dimension.domainId ?? dimension.datamartId ?? "";
      grouped.set(key, [...(grouped.get(key) ?? []), dimension]);
    }
    return grouped;
  }, [catalog.dimensions]);
  const modelsByDimension = useMemo(() => {
    const grouped = new Map<string, LogicalModel[]>();
    for (const model of catalog.models) {
      if (!model.dimensionId) continue;
      grouped.set(model.dimensionId, [...(grouped.get(model.dimensionId) ?? []), model]);
    }
    return grouped;
  }, [catalog.models]);
  const modelsByProcess = useMemo(() => {
    const grouped = new Map<string, LogicalModel[]>();
    for (const model of catalog.models) {
      if (!model.processId) continue;
      grouped.set(model.processId, [...(grouped.get(model.processId) ?? []), model]);
    }
    return grouped;
  }, [catalog.models]);

  const openEditor = (params: Record<string, string>) => {
    const query = new URLSearchParams(params);
    navigate(`/data-modeling/logical-models/new?${query.toString()}`);
  };
  const editModel = (model: LogicalModel) =>
    navigate(`/data-modeling/logical-models/${encodeURIComponent(model.id)}`);

  const panel = useMemo<PanelContent>(() => {
    const all = catalog.models;
    switch (selected.kind) {
      case "ROOT":
        return {
          title: "全部逻辑模型",
          description: `当前工作空间共 ${all.length} 个逻辑模型，选择左侧节点可按归属查看。`,
          models: all,
        };
      case "CATEGORY":
        return {
          title: selected.category.name,
          description: "该业务分类下的全部逻辑模型",
          models: all.filter((model) => model.categoryId === selected.category.id),
        };
      case "DOMAIN":
        return {
          title: `${selected.category.name} / ${selected.domain.name}`,
          description: "该数据域下的明细表、汇总表与维度表",
          models: all.filter((model) => model.domainId === selected.domain.id),
        };
      case "DIMENSION": {
        const dimension = selected.dimension;
        return {
          title: dimension.name,
          tags: [dimension.domainName ?? dimension.datamartName, dimension.code].filter(
            (tag): tag is string => Boolean(tag),
          ),
          description: dimension.description ?? "",
          models: all.filter((model) => model.dimensionId === dimension.id),
        };
      }
      case "PROCESS":
        return {
          title: `${selected.domain.name} / ${selected.process.name}`,
          description: "该业务过程下的明细表与汇总表",
          models: all.filter((model) => model.processId === selected.process.id),
        };
      case "DATAMART":
        return {
          title: selected.datamart.name,
          description: "该数据集市下的维度表与应用表",
          models: all.filter((model) => model.datamartId === selected.datamart.id),
        };
    }
  }, [selected, catalog.models]);

  const pinned = useMemo(() => pinnedScopeOf(selected), [selected]);
  const visibleModels = filtered
    ? panel.models.filter((model) => matchesModelFilters(model, filters))
    : panel.models;

  /**
   * Switches the node the panel describes.
   *
   * <p>A global domain or data mart filter left over from the root would contradict the new node's
   * own ownership, so those two are cleared; keyword, type, layer and owner keep narrowing within
   * the node.
   */
  const select = (next: Selection) => {
    setSelected(next);
    setFilters((current) =>
      current.domainId || current.datamartId
        ? { ...current, domainId: "", datamartId: "" }
        : current,
    );
  };

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
            <Plus size={14} />
            新建维度
          </Button>
        );
      default:
        return (
          <Button
            size="small"
            variant="primary"
            onClick={() => navigate("/data-modeling/logical-models/new")}
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
          {catalog.status === "loading" ? (
            <div className="flex h-32 items-center justify-center">
              <Spinner />
            </div>
          ) : treeEmpty ? (
            <Empty description="还没有业务分类或数据集市，请先到「数仓规划」建立分层与业务树。" />
          ) : (
            <nav aria-label="维度建模目录">
              {/* The root is the only scope that lets the filter bar query the whole Workspace. */}
              <TreeItem
                label="全部逻辑模型"
                icon="root"
                level={0}
                active={selected.kind === "ROOT"}
                onClick={() => select({ kind: "ROOT" })}
              />
              <GroupLabel level={0}>公共层</GroupLabel>
              {catalog.tree.categories.map((category) => {
                return (
                  <div key={category.id}>
                    <TreeItem
                      label={category.name}
                      level={0}
                      active={sameNode(selected, { kind: "CATEGORY", category })}
                      onClick={() => select({ kind: "CATEGORY", category })}
                    />
                    {category.children.map((domain) => (
                      <div key={domain.id}>
                        <TreeItem
                          label={domain.name}
                          level={1}
                          active={sameNode(selected, { kind: "DOMAIN", category, domain })}
                          onClick={() => select({ kind: "DOMAIN", category, domain })}
                        />
                        <GroupLabel level={2}>维度</GroupLabel>
                        {(dimensionsByOwner.get(domain.id) ?? []).map((dimension) => (
                          <div key={dimension.id}>
                            <TreeItem
                              label={dimension.name}
                              meta={
                                modelsByDimension.get(dimension.id)?.length
                                  ? `${modelsByDimension.get(dimension.id)?.length} 表`
                                  : undefined
                              }
                              icon="dimension"
                              level={2}
                              active={sameNode(selected, { kind: "DIMENSION", dimension })}
                              onClick={() => select({ kind: "DIMENSION", dimension })}
                            />
                            {(modelsByDimension.get(dimension.id) ?? []).map((model) => (
                              <TreeItem
                                key={model.id}
                                label={`${model.name}（${model.type === "DIM" ? "维度表" : "未知"}）`}
                                icon="model"
                                level={3}
                                onClick={() => editModel(model)}
                              />
                            ))}
                          </div>
                        ))}
                        {(dimensionsByOwner.get(domain.id) ?? []).length === 0 && (
                          <p
                            className="pb-1 text-xs text-[var(--yak-components-muted-text)]"
                            style={{ paddingLeft: 8 + 2 * 12 }}
                          >
                            暂无维度
                          </p>
                        )}
                        <GroupLabel level={2}>业务过程</GroupLabel>
                        {domain.children.map((process) => (
                          <div key={process.id}>
                            <TreeItem
                              label={process.name}
                              meta={
                                modelsByProcess.get(process.id)?.length
                                  ? `${modelsByProcess.get(process.id)?.length} 表`
                                  : undefined
                              }
                              icon="process"
                              level={2}
                              active={sameNode(selected, {
                                kind: "PROCESS",
                                category,
                                domain,
                                process,
                              })}
                              onClick={() => select({ kind: "PROCESS", category, domain, process })}
                            />
                            {(modelsByProcess.get(process.id) ?? []).map((model) => (
                              <TreeItem
                                key={model.id}
                                label={`${model.name}（明细表）`}
                                icon="model"
                                level={3}
                                onClick={() => editModel(model)}
                              />
                            ))}
                          </div>
                        ))}
                      </div>
                    ))}
                  </div>
                );
              })}
              <GroupLabel level={0}>应用层</GroupLabel>
              {catalog.tree.datamarts.map((datamart) => (
                <div key={datamart.id}>
                  <TreeItem
                    label={datamart.name}
                    level={0}
                    active={sameNode(selected, { kind: "DATAMART", datamart })}
                    onClick={() => select({ kind: "DATAMART", datamart })}
                  />
                  <GroupLabel level={1}>维度</GroupLabel>
                  {(dimensionsByOwner.get(datamart.id) ?? []).map((dimension) => (
                    <div key={dimension.id}>
                      <TreeItem
                        label={dimension.name}
                        meta={
                          modelsByDimension.get(dimension.id)?.length
                            ? `${modelsByDimension.get(dimension.id)?.length} 表`
                            : undefined
                        }
                        icon="dimension"
                        level={1}
                        active={sameNode(selected, { kind: "DIMENSION", dimension })}
                        onClick={() => select({ kind: "DIMENSION", dimension })}
                      />
                      {(modelsByDimension.get(dimension.id) ?? []).map((model) => (
                        <TreeItem
                          key={model.id}
                          label={`${model.name}（维度表）`}
                          icon="model"
                          level={2}
                          onClick={() => editModel(model)}
                        />
                      ))}
                    </div>
                  ))}
                  {(dimensionsByOwner.get(datamart.id) ?? []).length === 0 && (
                    <p
                      className="pb-1 text-xs text-[var(--yak-components-muted-text)]"
                      style={{ paddingLeft: 8 + 12 }}
                    >
                      暂无维度
                    </p>
                  )}
                </div>
              ))}
            </nav>
          )}
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
          />
          {searching ? (
            <ModelPanel
              title="检索结果"
              description={`匹配 ${searchResult?.total ?? 0} 个逻辑模型`}
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
          boundModelCount={modelsByDimension.get(deletingDimension.id)?.length ?? 0}
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
