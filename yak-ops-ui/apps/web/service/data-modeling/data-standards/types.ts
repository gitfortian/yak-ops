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
export interface StandardCategory {
  id: string;
  parentId: string | null;
  name: string;
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
}
export interface StandardField extends StandardInput {
  id: string;
  categoryId: string;
  categoryPath: string;
  ownerName: string | null;
  ownerActive: boolean;
  updateTime: string;
  revision: number;
}
export interface StandardPage {
  bizData: StandardField[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}
export type StandardQuery =
  | { scope: "CATEGORY"; categoryId: string; pageNo: number; pageSize: number }
  | { scope: "ALL"; keyword: string; pageNo: number; pageSize: number };
export type StandardDraft =
  | { mode: "create"; category: { id: string; path: string } }
  | { mode: "edit"; standard: StandardField };
export type CategoryDraft =
  | { mode: "create"; parent: StandardCategory | null; parentPath: string }
  | { mode: "rename"; category: StandardCategory; parentPath: string };
export type DeleteTarget =
  | { kind: "category"; node: StandardCategory }
  | { kind: "field"; node: StandardField };
export interface SavedStandard {
  id: string;
  revision: number;
}
export interface OwnerOption {
  userId: string;
  displayName: string;
}
export type StandardReferences =
  | { availability: "NOT_ENABLED"; referenced: null; count: null }
  | { availability: "AVAILABLE"; referenced: boolean; count: number };
