import type {
  ConceptDimension,
  LogicalModel,
} from "@/service/data-modeling/dimension-modeling/types";
import type { PublicationStatus } from "@/service/data-modeling/dimension-modeling/publication";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type {
  CategoryNode,
  DatamartNode,
  DomainNode,
  PlanningTree,
  ProcessNode,
} from "@/service/data-modeling/warehouse-planning/types";

/** The three ways the workbench organizes the same model set. */
export const WORKBENCH_VIEWS = ["layer", "biz", "publish"] as const;
export type WorkbenchView = (typeof WORKBENCH_VIEWS)[number];

export const WORKBENCH_VIEW_LABEL: Record<WorkbenchView, string> = {
  layer: "分层视图",
  biz: "业务视图",
  publish: "发布视图",
};

/** Reads the {@code view} query parameter; anything unknown falls back to the layer view. */
export function parseWorkbenchView(value: string | null): WorkbenchView {
  return (WORKBENCH_VIEWS as readonly string[]).includes(value ?? "")
    ? (value as WorkbenchView)
    : "layer";
}

/**
 * One node the left panel can describe. The layer and publication kinds exist only in their own
 * views; the business kinds are shared with the tree the page has always shown.
 */
export type Selection =
  | { kind: "ROOT" }
  | { kind: "LAYER"; layer: Layer }
  | { kind: "CATEGORY"; category: CategoryNode }
  | { kind: "DOMAIN"; category: CategoryNode; domain: DomainNode }
  | { kind: "DIMENSION"; dimension: ConceptDimension }
  | { kind: "PROCESS"; category: CategoryNode; domain: DomainNode; process: ProcessNode }
  | { kind: "DATAMART"; datamart: DatamartNode }
  | { kind: "PUBLICATION"; status: PublicationStatus | null };

/** Stable string form of a node for the {@code node} query parameter. */
export function nodeKeyOf(selection: Selection): string {
  switch (selection.kind) {
    case "ROOT":
      return "root";
    case "LAYER":
      return `layer:${selection.layer.id}`;
    case "CATEGORY":
      return `category:${selection.category.id}`;
    case "DOMAIN":
      return `domain:${selection.domain.id}`;
    case "DIMENSION":
      return `dimension:${selection.dimension.id}`;
    case "PROCESS":
      return `process:${selection.process.id}`;
    case "DATAMART":
      return `datamart:${selection.datamart.id}`;
    case "PUBLICATION":
      return `pub:${selection.status ?? "none"}`;
  }
}

/** Node kinds each view can render, so a stale link lands on a node its panel actually shows. */
function nodeInView(key: string, view: WorkbenchView): boolean {
  if (key === "root") return true;
  const kind = key.split(":", 1)[0];
  if (view === "layer") return kind === "layer";
  if (view === "biz")
    return ["category", "domain", "process", "dimension", "datamart"].includes(kind);
  return kind === "pub";
}

/**
 * Restores a node from its key.
 *
 * <p>Resolution needs the catalog, so a key the loaded data cannot name — a deleted process, a
 * removed layer, a typo — returns null and the page falls back to the root. An empty or absent key
 * is the root, not a failure.
 */
export function resolveNode(
  key: string | null,
  tree: PlanningTree,
  dimensions: ConceptDimension[],
  layers: Layer[],
): Selection | null {
  if (!key || key === "root") return { kind: "ROOT" };
  const separator = key.indexOf(":");
  if (separator < 0) return null;
  const kind = key.slice(0, separator);
  const id = key.slice(separator + 1);
  if (!id) return null;
  switch (kind) {
    case "layer": {
      const layer = layers.find((item) => item.id === id);
      return layer ? { kind: "LAYER", layer } : null;
    }
    case "category": {
      const category = tree.categories.find((item) => item.id === id);
      return category ? { kind: "CATEGORY", category } : null;
    }
    case "domain":
      for (const category of tree.categories) {
        const domain = category.children.find((item) => item.id === id);
        if (domain) return { kind: "DOMAIN", category, domain };
      }
      return null;
    case "process":
      for (const category of tree.categories) {
        for (const domain of category.children) {
          const process = domain.children.find((item) => item.id === id);
          if (process) return { kind: "PROCESS", category, domain, process };
        }
      }
      return null;
    case "dimension": {
      const dimension = dimensions.find((item) => item.id === id);
      return dimension ? { kind: "DIMENSION", dimension } : null;
    }
    case "datamart": {
      const datamart = tree.datamarts.find((item) => item.id === id);
      return datamart ? { kind: "DATAMART", datamart } : null;
    }
    case "pub": {
      if (id === "none") return { kind: "PUBLICATION", status: null };
      const known: readonly string[] = ["PENDING", "SUCCEEDED", "FAILED", "UNKNOWN"];
      return known.includes(id) ? { kind: "PUBLICATION", status: id as PublicationStatus } : null;
    }
    default:
      return null;
  }
}

/**
 * Keeps a URL node across a view switch only when the new view renders that kind; otherwise the
 * switch lands on the root instead of showing a selection no panel highlights.
 */
export function nodeKeyForView(key: string | null, view: WorkbenchView): string {
  return key && nodeInView(key, view) ? key : "root";
}

/** True when two selections name the same node, so panels can mark the active row. */
export function sameNode(left: Selection, right: Selection): boolean {
  if (left.kind !== right.kind) return false;
  switch (left.kind) {
    case "ROOT":
      return true;
    case "LAYER":
      return right.kind === "LAYER" && left.layer.id === right.layer.id;
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
    case "PUBLICATION":
      return right.kind === "PUBLICATION" && left.status === right.status;
  }
}

/** Ownership the selected node already fixes for the search bar's pinned scope selects. */
export function pinnedScopeOf(selection: Selection): {
  domain: { id: string; name: string } | null;
  datamart: { id: string; name: string } | null;
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

/** Models the selected node owns. A layer node selects by the layer a model is filed under. */
export function modelsOfNode(models: LogicalModel[], selection: Selection): LogicalModel[] {
  switch (selection.kind) {
    case "ROOT":
      return models;
    case "LAYER":
      return models.filter((model) => model.layerId === selection.layer.id);
    case "CATEGORY":
      return models.filter((model) => model.categoryId === selection.category.id);
    case "DOMAIN":
      return models.filter((model) => model.domainId === selection.domain.id);
    case "DIMENSION":
      return models.filter((model) => model.dimensionId === selection.dimension.id);
    case "PROCESS":
      return models.filter((model) => model.processId === selection.process.id);
    case "DATAMART":
      return models.filter((model) => model.datamartId === selection.datamart.id);
    case "PUBLICATION":
      return models.filter((model) => model.latestPublicationStatus === selection.status);
  }
}

/** Model count per layer id, so the layer view can show how each physical tier is filled. */
export function layerCounts(layers: Layer[], models: LogicalModel[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const layer of layers) counts.set(layer.id, 0);
  for (const model of models) counts.set(model.layerId, (counts.get(model.layerId) ?? 0) + 1);
  return counts;
}

/** Model count per planning node id; the caller decides which id space it reads. */
export function countByModelKey(
  models: LogicalModel[],
  keyOf: (model: LogicalModel) => string | null,
): Map<string, number> {
  const counts = new Map<string, number>();
  for (const model of models) {
    const key = keyOf(model);
    if (!key) continue;
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return counts;
}

const PUBLICATION_STATUSES: readonly PublicationStatus[] = [
  "PENDING",
  "SUCCEEDED",
  "FAILED",
  "UNKNOWN",
];

/**
 * Models grouped by publication outcome for the publish view.
 *
 * <p>{@code none} holds models never published; a status this build does not know also counts as
 * unpublished, mirroring how the list column renders an unknown status.
 */
export function publicationCounts(models: LogicalModel[]): {
  none: number;
  byStatus: Map<PublicationStatus, number>;
} {
  const byStatus = new Map<PublicationStatus, number>();
  let none = 0;
  for (const model of models) {
    const status = model.latestPublicationStatus;
    if (status && PUBLICATION_STATUSES.includes(status))
      byStatus.set(status, (byStatus.get(status) ?? 0) + 1);
    else none += 1;
  }
  return { none, byStatus };
}
