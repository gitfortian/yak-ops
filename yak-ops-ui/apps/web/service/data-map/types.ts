/** Shared shapes of the data map HTTP contract. */
export interface Paging<T> {
  bizData: T[];
  pagination: { total: number; pages: number; pageNo: number; pageSize: number };
}

export type TableStatus = "ACTIVE" | "MISSING";
export type CrawlStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type PreviewTruncation = "ROWS" | "SIZE";

export interface Tag {
  id: string;
  name: string;
}

export interface TableSummary {
  id: string;
  datasourceId: string;
  datasourceName: string | null;
  database: string;
  schema: string;
  name: string;
  tableType: string | null;
  remarks: string | null;
  status: TableStatus;
  description: string | null;
  ownerId: string | null;
  domainId: string | null;
  firstSeenTime: string;
  lastSeenTime: string;
  tags: Tag[];
  matchedColumns: string[];
}

export interface TableColumn {
  ordinal: number;
  name: string;
  typeName: string;
  jdbcType: number;
  size: number | null;
  scale: number | null;
  nullable: boolean;
  primaryKey: boolean;
  primaryKeyPosition: number | null;
  remarks: string | null;
}

export interface TableDetail {
  id: string;
  datasourceId: string;
  datasourceName: string | null;
  database: string;
  schema: string;
  name: string;
  tableType: string | null;
  remarks: string | null;
  status: TableStatus;
  description: string | null;
  ownerId: string | null;
  domainId: string | null;
  firstSeenTime: string;
  lastSeenTime: string;
  favorited: boolean;
  tags: Tag[];
  columns: TableColumn[];
}

export interface TableQuery {
  keyword?: string;
  datasourceId?: string;
  database?: string;
  schema?: string;
  domainId?: string;
  unclassified?: boolean;
  tagId?: string;
  includeMissing?: boolean;
  pageNo: number;
  pageSize: number;
}

export interface CuratedUpdate {
  description: string | null;
  ownerId: string | null;
  domainId: string | null;
}

export interface TableChange {
  changeType: string;
  detail: string;
  createdTime: string;
}

export interface Overview {
  assetCount: number;
  managedDatasourceCount: number;
  newTablesLastSevenDays: number;
  unclassifiedCount: number;
  hotTables: TableSummary[];
}

export interface Preview {
  columns: { name: string; typeName: string }[];
  rows: string[][];
  truncated: PreviewTruncation;
  notice: string;
}

export interface Crawl {
  id: string;
  datasourceId: string;
  database: string;
  schema: string;
  status: CrawlStatus;
  tableCount: number;
  columnCount: number;
  addedCount: number;
  updatedCount: number;
  removedCount: number;
  failedCount: number;
  error: string | null;
  createdTime: string;
  startedTime: string | null;
  finishedTime: string | null;
  createBy: string;
}

export interface CrawlFailure {
  tableName: string;
  error: string;
  createdTime: string;
}

export interface CrawlDetail {
  crawl: Crawl;
  failures: CrawlFailure[];
}

export interface CrawlSubmission {
  datasourceId: string;
  database?: string;
  schema?: string;
}
