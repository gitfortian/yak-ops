export const DATA_TYPES = ["STRING", "BIGINT", "DECIMAL", "DATE", "TIMESTAMP"] as const;
export type DataType = (typeof DATA_TYPES)[number];
export const SECURITY_LEVELS = ["PUBLIC", "INTERNAL", "SENSITIVE", "CONFIDENTIAL"] as const;
export type SecurityLevel = (typeof SECURITY_LEVELS)[number];
export const SECURITY_LABEL: Record<SecurityLevel, string> = {
  PUBLIC: "公开",
  INTERNAL: "内部",
  SENSITIVE: "敏感",
  CONFIDENTIAL: "机密",
};
export const NODE_TYPES = ["DIRECTORY", "STANDARD_SET"] as const;
export type NodeType = (typeof NODE_TYPES)[number];
export const NODE_TYPE_LABEL: Record<NodeType, string> = {
  DIRECTORY: "目录",
  STANDARD_SET: "标准集",
};
export const STANDARD_KINDS = ["FIELD", "LOOKUP"] as const;
export type StandardKind = (typeof STANDARD_KINDS)[number];
export const STANDARD_KIND_LABEL: Record<StandardKind, string> = {
  FIELD: "字段标准",
  LOOKUP: "标准代码",
};
export const MEASURE_UNIT_CATEGORIES = ["CURRENCY", "QUANTIFIER", "RATIO", "RANK", "TIME"] as const;
export type MeasureUnitCategory = (typeof MEASURE_UNIT_CATEGORIES)[number];
export const MEASURE_UNIT_LABEL: Record<MeasureUnitCategory, string> = {
  CURRENCY: "货币",
  QUANTIFIER: "量词",
  RATIO: "比率",
  RANK: "排名",
  TIME: "时间",
};
export interface StandardCategory {
  id: string;
  parentId: string | null;
  name: string;
  nodeType: NodeType;
  standardKind: StandardKind | null;
  recordCount: number;
  revision: number;
  children: StandardCategory[];
}
export interface StandardInput {
  cnName: string;
  enCode: string;
  dataType: DataType;
  lengthPrecision: string | null;
  isNotNull: boolean;
  securityLevel: SecurityLevel;
  ownerId: string;
  businessCaliber: string | null;
  defaultValue: string | null;
  parentStandardId: string | null;
  lookupId: string | null;
  measureUnitId: string | null;
}
export interface StandardField extends StandardInput {
  id: string;
  categoryId: string;
  categoryPath: string;
  ownerName: string | null;
  ownerActive: boolean;
  parentStandardEnCode: string | null;
  lookupEnCode: string | null;
  lookupCnName: string | null;
  measureUnitEnCode: string | null;
  measureUnitCnName: string | null;
  updateTime: string;
  revision: number;
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
export type StandardPage = Paged<StandardField>;
export type StandardQuery =
  | { scope: "CATEGORY"; categoryId: string; pageNo: number; pageSize: number }
  | { scope: "ALL"; keyword: string; pageNo: number; pageSize: number };
export type KindQuery =
  | { scope: "CATEGORY"; categoryId: string; pageNo: number; pageSize: number }
  | { scope: "KEYWORD"; keyword: string; pageNo: number; pageSize: number };
export type StandardDraft =
  | { mode: "create"; category: { id: string; path: string } }
  | { mode: "edit"; standard: StandardField };
export type CategoryDraft =
  | { mode: "create"; parent: StandardCategory | null; parentPath: string }
  | { mode: "rename"; category: StandardCategory; parentPath: string };
export interface StandardLookupValueInput {
  valueCode: string;
  cnName: string | null;
  enName: string | null;
  meaning: string | null;
}
export interface StandardLookupValue extends StandardLookupValueInput {
  ordinal: number;
}
export interface StandardLookupInput {
  enCode: string;
  cnName: string;
  enName: string;
  description: string | null;
  values: StandardLookupValueInput[];
}
export interface StandardLookup {
  id: string;
  categoryId: string;
  categoryPath: string;
  enCode: string;
  cnName: string;
  enName: string;
  description: string | null;
  valueCount: number;
  values: StandardLookupValue[] | null;
  updateTime: string;
  revision: number;
}
export interface StandardMeasureUnitInput {
  category: MeasureUnitCategory;
  enCode: string;
  enName: string;
  cnName: string;
  description: string | null;
}
export interface StandardMeasureUnit extends StandardMeasureUnitInput {
  id: string;
  updateTime: string;
  revision: number;
}
export interface StandardNamingEntryInput {
  enCode: string;
  enName: string;
  cnName: string;
  description: string | null;
}
export interface StandardNamingEntry extends StandardNamingEntryInput {
  id: string;
  updateTime: string;
  revision: number;
}
export interface ReferenceItem {
  kind: "MODEL_FIELD" | "FIELD_STANDARD" | "MODEL" | "ATOMIC_METRIC";
  id: string;
  name: string;
  path: string;
}
export const REFERENCE_KIND_LABEL: Record<ReferenceItem["kind"], string> = {
  MODEL_FIELD: "模型字段",
  FIELD_STANDARD: "字段标准",
  MODEL: "模型",
  ATOMIC_METRIC: "原子指标",
};
export type DeleteTarget =
  | { kind: "category"; node: StandardCategory }
  | { kind: "field"; node: StandardField }
  | { kind: "lookup"; node: StandardLookup }
  | { kind: "unit"; node: StandardMeasureUnit }
  | { kind: "naming"; node: StandardNamingEntry };
export interface SavedStandard {
  id: string;
  revision: number;
}
export interface OwnerOption {
  userId: string;
  displayName: string;
}
export type StandardReferences =
  | { availability: "NOT_ENABLED"; referenced: null; count: null; items: null }
  | { availability: "AVAILABLE"; referenced: boolean; count: number; items: ReferenceItem[] };
