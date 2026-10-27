import type { LogicalModel, ModelType } from "@/service/data-modeling/dimension-modeling/types";

/** Sentinel used by the selects; an empty filter field means "no restriction". */
export const ALL = "ALL";

/**
 * Filters accepted by the model list.
 *
 * <p>An empty string means "all" so the request builder can omit the parameter, while the selects
 * use {@code ALL} because a {@code Select} treats an empty value as "nothing selected".
 */
export interface ModelSearchFilters {
  keyword: string;
  type: ModelType | "ALL";
  layerId: string;
  ownerId: string;
  domainId: string;
  datamartId: string;
}

export const EMPTY_MODEL_SEARCH: ModelSearchFilters = {
  keyword: "",
  type: ALL,
  layerId: "",
  ownerId: "",
  domainId: "",
  datamartId: "",
};

/** True when at least one condition is set, so the list is narrowed by the filter bar. */
export function isModelSearching(filters: ModelSearchFilters) {
  return Boolean(
    filters.keyword.trim() ||
    filters.type !== ALL ||
    filters.layerId ||
    filters.ownerId ||
    filters.domainId ||
    filters.datamartId,
  );
}

/**
 * True when a model satisfies the filters the bar holds.
 *
 * <p>Used to narrow the models of the selected tree node: the node owns the scope, so the bar
 * refines it instead of replacing it. Keyword matching mirrors the server's `LIKE` over table name
 * and Chinese name.
 */
export function matchesModelFilters(model: LogicalModel, filters: ModelSearchFilters) {
  const keyword = filters.keyword.trim().toLowerCase();
  if (keyword && !`${model.name} ${model.cnName}`.toLowerCase().includes(keyword)) {
    return false;
  }
  if (filters.type !== ALL && model.type !== filters.type) return false;
  if (filters.layerId && model.layerId !== filters.layerId) return false;
  if (filters.ownerId && model.ownerId !== filters.ownerId) return false;
  if (filters.domainId && model.domainId !== filters.domainId) return false;
  if (filters.datamartId && model.datamartId !== filters.datamartId) return false;
  return true;
}
