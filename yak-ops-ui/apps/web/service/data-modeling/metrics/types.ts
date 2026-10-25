export const DATA_TYPES = ["STRING", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP"] as const;
export type DataType = (typeof DATA_TYPES)[number];
export const AGGREGATE_TYPES = ["SUM", "COUNT", "AVG", "MAX", "MIN"] as const;
export type AggregateType = (typeof AGGREGATE_TYPES)[number];
export const AGGREGATE_LABEL: Record<AggregateType, string> = {
  SUM: "求和",
  COUNT: "计数",
  AVG: "平均",
  MAX: "最大",
  MIN: "最小",
};
export const METRIC_KINDS = ["period", "modifier", "atomic", "derived", "composite"] as const;
export type MetricKind = (typeof METRIC_KINDS)[number];
export const METRIC_KIND_LABEL: Record<MetricKind, string> = {
  period: "时间周期",
  modifier: "修饰词",
  atomic: "原子指标",
  derived: "派生指标",
  composite: "复合指标",
};
export const METRIC_KIND_ORDER: MetricKind[] = [
  "period",
  "modifier",
  "atomic",
  "derived",
  "composite",
];
export const METRIC_CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;
export interface MetricTimePeriodInput {
  enCode: string;
  cnName: string;
  description: string | null;
}
export interface MetricTimePeriod extends MetricTimePeriodInput {
  id: string;
  updateTime: string;
  revision: number;
}
export interface MetricModifierInput {
  domainId: string;
  enCode: string;
  cnName: string;
  description: string | null;
}
export interface MetricModifier extends MetricModifierInput {
  id: string;
  domainName: string;
  updateTime: string;
  revision: number;
}
export interface MetricAtomicInput {
  processId: string;
  enCode: string;
  cnName: string;
  dataType: DataType;
  aggregateType: AggregateType;
  measureUnitId: string | null;
  ownerId: string | null;
  description: string | null;
}
export interface MetricAtomic {
  id: string;
  processId: string;
  processName: string;
  enCode: string;
  cnName: string;
  dataType: DataType;
  aggregateType: AggregateType;
  measureUnitId: string | null;
  measureUnitEnCode: string | null;
  ownerId: string;
  description: string | null;
  updateTime: string;
  revision: number;
}
export interface MetricDerivedInput {
  atomicMetricId: string;
  periodId: string;
  modifierIds: string[];
  ownerId: string | null;
  description: string | null;
}
export interface MetricDerivedPart {
  modifierId: string;
  modifierEnCode: string;
  modifierCnName: string;
}
export interface MetricDerived {
  id: string;
  enCode: string;
  cnName: string;
  atomicMetricId: string;
  atomicEnCode: string;
  atomicCnName: string;
  periodId: string;
  periodEnCode: string;
  periodCnName: string;
  modifiers: MetricDerivedPart[];
  ownerId: string;
  description: string | null;
  updateTime: string;
  revision: number;
}
export interface MetricCompositeInput {
  enCode: string;
  cnName: string;
  expression: string;
  ownerId: string | null;
  description: string | null;
}
export interface MetricCompositeReference {
  derivedMetricId: string;
  derivedEnCode: string;
  derivedCnName: string;
}
export interface MetricComposite extends Omit<MetricCompositeInput, "ownerId"> {
  id: string;
  references: MetricCompositeReference[];
  ownerId: string;
  updateTime: string;
  revision: number;
}
export interface MetricReferenceItem {
  kind: "DERIVED_METRIC" | "COMPOSITE_METRIC" | "MODEL_FIELD";
  id: string;
  name: string;
  path: string;
}
export const METRIC_REFERENCE_KIND_LABEL: Record<MetricReferenceItem["kind"], string> = {
  DERIVED_METRIC: "派生指标",
  COMPOSITE_METRIC: "复合指标",
  MODEL_FIELD: "模型字段",
};
export interface MetricReferences {
  availability: "NOT_ENABLED" | "AVAILABLE";
  referenced: boolean | null;
  count: number | null;
  items: MetricReferenceItem[] | null;
}
export interface Pagination {
  pageNo: number;
  pageSize: number;
  total: number;
  pages: number;
}
export interface Paged<T> {
  bizData: T[];
  pagination: Pagination;
}
export interface SavedMetric {
  id: string;
  revision: number;
}
/** One delete target across the four deletable kinds; a composite has no references panel. */
export type MetricDeleteTarget =
  | { kind: "period"; node: MetricTimePeriod }
  | { kind: "modifier"; node: MetricModifier }
  | { kind: "atomic"; node: MetricAtomic }
  | { kind: "derived"; node: MetricDerived }
  | { kind: "composite"; node: MetricComposite };
