export type NodeKind = "CATEGORY" | "DOMAIN" | "PROCESS" | "DATASET" | "SUBJECT";
export type NodeKey = `${NodeKind}:${string}`;

interface NodeFields {
  id: string;
  name: string;
  enName: string | null;
  code: string;
  ownerId: string | null;
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

export interface DatamartNode extends NodeFields {
  kind: "DATASET";
  parentId: null;
  children: SubjectNode[];
}

export interface SubjectNode extends NodeFields {
  kind: "SUBJECT";
  parentId: string;
  children: [];
}

/** Public-layer business tree node. */
export type BusinessNode = CategoryNode | DomainNode | ProcessNode;
/** Application-layer data mart tree node. */
export type ApplicationNode = DatamartNode | SubjectNode;
export type RootNode = CategoryNode | DatamartNode;
export type PlanningNode = BusinessNode | ApplicationNode;

/** Both trees reserve abbreviations in one namespace; the split is presentational. */
export interface PlanningTree {
  categories: CategoryNode[];
  datamarts: DatamartNode[];
}

export interface NodeInput {
  name: string;
  enName: string;
  code: string;
  ownerId: string;
  description: string | null;
}

export type CreateDraft =
  | { mode: "create"; kind: "CATEGORY"; parent: null }
  | { mode: "create"; kind: "DOMAIN"; parent: CategoryNode }
  | { mode: "create"; kind: "PROCESS"; parent: DomainNode }
  | { mode: "create"; kind: "DATASET"; parent: null }
  | { mode: "create"; kind: "SUBJECT"; parent: DatamartNode };

export type EditorDraft =
  | CreateDraft
  | {
      mode: "edit";
      node: PlanningNode;
      parent: PlanningNode | null;
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
