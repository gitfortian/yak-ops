import type { DataType } from "@/service/data-modeling/data-standards/types";

export const MODEL_TYPES = ["DWD", "DIM", "DWS", "ADS"] as const;
export type ModelType = (typeof MODEL_TYPES)[number];

export const MODEL_TYPE_LABEL: Record<ModelType, string> = {
  DWD: "明细表",
  DIM: "维度表",
  DWS: "汇总表",
  ADS: "应用表",
};

export const MODEL_TYPE_HINT: Record<ModelType, string> = {
  DWD: "挂业务过程，沉淀该过程最细粒度的事实",
  DIM: "描述一个业务概念，归属数据域或数据集市",
  DWS: "挂业务过程，按维度聚合后的汇总结果，至少一个度量",
  ADS: "挂数据集市，直接服务报表与应用，可不带度量",
};

export const AGGREGATE_TYPES = ["SUM", "COUNT", "AVG", "MAX", "MIN"] as const;
export type AggregateType = (typeof AGGREGATE_TYPES)[number];

export const AGGREGATE_LABEL: Record<AggregateType, string> = {
  SUM: "求和 SUM",
  COUNT: "计数 COUNT",
  AVG: "平均 AVG",
  MAX: "最大 MAX",
  MIN: "最小 MIN",
};

/**
 * Whether an aggregate may be applied to a logical type.
 *
 * <p>Mirrors the server-side matrix so the editor never offers a pair the API rejects: `SUM` and
 * `AVG` need arithmetic, while `COUNT` / `MAX` / `MIN` accept all five logical types.
 */
export function aggregateSupports(aggregate: AggregateType, dataType: DataType): boolean {
  return aggregate === "SUM" || aggregate === "AVG"
    ? dataType === "BIGINT" || dataType === "DECIMAL"
    : true;
}

/** Types whose fields may carry a measure. */
export const AGGREGATE_MODEL_TYPES: ReadonlySet<ModelType> = new Set<ModelType>(["DWS", "ADS"]);

export const STORAGE_POLICIES = ["DAILY_INCREMENTAL", "DAILY_FULL"] as const;
export type StoragePolicy = (typeof STORAGE_POLICIES)[number];

export const STORAGE_POLICY_LABEL: Record<StoragePolicy, string> = {
  DAILY_INCREMENTAL: "每日增量",
  DAILY_FULL: "每日全量",
};

export const DIMENSION_SCOPES = ["PUBLIC", "APPLICATION"] as const;
export type DimensionScope = (typeof DIMENSION_SCOPES)[number];

export const DIMENSION_SCOPE_LABEL: Record<DimensionScope, string> = {
  PUBLIC: "公有层",
  APPLICATION: "应用层",
};

/** One ordered model field as returned by a detail read. */
export interface ModelField {
  id: string;
  ordinal: number;
  enCode: string;
  cnName: string;
  dataType: DataType;
  lengthPrecision: string | null;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  isPartition: boolean;
  aggregateType: AggregateType | null;
  standardId: string | null;
  standardEnCode: string | null;
  description: string | null;
}

/**
 * A logical model. {@code type} is null when the stored row carries a kind this build does not
 * know: the row stays readable but refuses edits. {@code fields} is null in list reads.
 */
export interface LogicalModel {
  id: string;
  type: ModelType | null;
  name: string;
  cnName: string;
  layerId: string;
  layerName: string;
  categoryId: string;
  categoryName: string;
  processId: string | null;
  processName: string | null;
  domainId: string | null;
  domainName: string | null;
  datamartId: string | null;
  datamartName: string | null;
  dimensionId: string | null;
  dimensionName: string | null;
  storagePolicy: StoragePolicy;
  lifecycleDays: number;
  ownerId: string;
  description: string | null;
  fieldCount: number;
  revision: number;
  fields: ModelField[] | null;
}

export interface ModelPage {
  bizData: LogicalModel[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}

export interface ModelQuery {
  type: ModelType | "ALL";
  layerId: string;
  keyword: string;
  pageNo: number;
  pageSize: number;
  /** Optional attribute filters; omitted when the search bar leaves them at "all". */
  ownerId?: string;
  domainId?: string;
  datamartId?: string;
}

export interface ModelFieldInput {
  enCode: string;
  cnName: string;
  dataType: DataType | null;
  lengthPrecision: string | null;
  isPrimaryKey: boolean;
  isNotNull: boolean;
  isPartition: boolean;
  aggregateType: AggregateType | null;
  standardId: string | null;
  description: string | null;
}

/** Submit shape; the table name is sent as a suffix because the layer owns the prefix. */
export interface ModelInput {
  type: ModelType;
  layerId: string;
  categoryId: string;
  processId: string | null;
  domainId: string | null;
  datamartId: string | null;
  dimensionId: string | null;
  nameSuffix: string;
  cnName: string;
  storagePolicy: StoragePolicy;
  lifecycleDays: number;
  ownerId: string;
  description: string | null;
  fields: ModelFieldInput[];
}

export interface ConceptDimension {
  id: string;
  scope: DimensionScope;
  domainId: string | null;
  domainName: string | null;
  datamartId: string | null;
  datamartName: string | null;
  name: string;
  enName: string;
  code: string;
  description: string | null;
  revision: number;
}

export interface DimensionInput {
  scope: DimensionScope;
  domainId: string | null;
  datamartId: string | null;
  name: string;
  enName: string;
  code: string;
  description: string | null;
}

/** Scope filter; {@code scopeId} is a data domain or data mart id and is ignored for ALL. */
export interface DimensionFilter {
  scope: DimensionScope | "ALL";
  scopeId: string;
}

/** Surface that has to explain a rejected model request. */
export type ModelFailure =
  | { kind: "field"; field: "nameSuffix" | "layer" | "ownership" | "fields"; message: string }
  | { kind: "conflict" }
  | { kind: "missing" }
  | { kind: "unknown-type" }
  | { kind: "unknown"; message: string };

/** Surface that has to explain a rejected dimension request. */
export type DimensionFailure =
  | { kind: "field"; field: "code" | "scope"; message: string }
  | { kind: "conflict" }
  | { kind: "referenced" }
  | { kind: "missing" }
  | { kind: "unknown"; message: string };
