export type NodeKind = "CATEGORY" | "DOMAIN" | "PROCESS";
export type NodeKey = `${NodeKind}:${string}`;

interface NodeFields {
  id: string;
  name: string;
  code: string;
  ownerId: string;
  ownerName: string | null;
  ownerActive: boolean;
  description: string | null;
  revision: number;
}

export interface CategoryNode extends NodeFields {
  kind: "CATEGORY";
  parentId: null;
  children: DomainNode[];
}

export interface DomainNode extends NodeFields {
  kind: "DOMAIN";
  parentId: string;
  children: ProcessNode[];
}

export interface ProcessNode extends NodeFields {
  kind: "PROCESS";
  parentId: string;
  children: [];
}

export type PlanningNode = CategoryNode | DomainNode | ProcessNode;

export interface NodeInput {
  name: string;
  code: string;
  ownerId: string;
  description: string | null;
}

export type CreateDraft =
  | { mode: "create"; kind: "CATEGORY"; parent: null }
  | { mode: "create"; kind: "DOMAIN"; parent: CategoryNode }
  | { mode: "create"; kind: "PROCESS"; parent: DomainNode };

export type EditorDraft =
  | CreateDraft
  | {
      mode: "edit";
      node: PlanningNode;
      parent: CategoryNode | DomainNode | null;
    };

export interface SavedNode {
  id: string;
  kind: NodeKind;
  revision: number;
}

export interface OwnerOption {
  userId: string;
  displayName: string;
}

export interface Page<T> {
  bizData: T[];
  pagination: { pageNo: number; pageSize: number; total: number; pages: number };
}

export interface LogicalModelSummary {
  id: string;
  name: string;
  code: string;
}

export type AssociatedModels =
  | { availability: "NOT_ENABLED"; page: null }
  | { availability: "AVAILABLE"; page: Page<LogicalModelSummary> };
