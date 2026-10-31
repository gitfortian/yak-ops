/** Shared shapes of the data map HTTP contract. */
export interface Paging<T> {
  bizData: T[];
  pagination: { total: number; pages: number; pageNo: number; pageSize: number };
}

export type TableStatus = "ACTIVE" | "MISSING";
export type CrawlStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "INTERRUPTED";
export type CrawlTrigger = "MANUAL" | "SCHEDULED";
export type CrawlTaskStatus = "DRAFT" | "RUNNING" | "OFFLINE";
export type CrawlScheduleType = "MANUAL" | "CRON";
export type PreviewTruncation = "ROWS" | "SIZE";

/** One tag value shown on an asset, carrying its key beside the value. */
export interface TagRef {
  keyId: string;
  keyName: string;
  valueId: string;
  valueName: string;
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
  tags: TagRef[];
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
  tags: TagRef[];
  columns: TableColumn[];
}

export interface TableQuery {
  keyword?: string;
  datasourceId?: string;
  database?: string;
  schema?: string;
  domainId?: string;
  unclassified?: boolean;
  ownerId?: string;
  tagValueIds?: string[];
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
  describedCount: number;
  ownedCount: number;
  hotTables: TableSummary[];
}

/** Asset and coverage counts of one browse facet value, such as a domain or a datasource. */
export interface FacetSummary {
  facetKey: string;
  assetCount: number;
  describedCount: number;
  ownedCount: number;
}

/** Per-domain and per-datasource facet counts of the Workspace. */
export interface OverviewFacets {
  domains: FacetSummary[];
  datasources: FacetSummary[];
}

export interface Preview {
  columns: { name: string; typeName: string }[];
  rows: string[][];
  truncated: PreviewTruncation;
  notice: string;
}

export interface Crawl {
  id: string;
  taskId: string | null;
  taskName: string | null;
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
  filteredCount: number;
  triggerType: CrawlTrigger;
  excludePattern: string | null;
  durationSeconds: number | null;
  retryOf: string | null;
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

export interface CrawlTask {
  id: string;
  name: string;
  datasourceId: string;
  database: string;
  schema: string;
  ownerId: string;
  status: CrawlTaskStatus;
  scheduleType: CrawlScheduleType;
  cronExpression: string | null;
  nextFireTime: string | null;
  excludePattern: string | null;
  excludeTables: string[];
  lastRunStatus: CrawlStatus | null;
  lastRunTime: string | null;
  createdTime: string;
  updatedTime: string;
  createBy: string;
  updateBy: string;
}

export interface CrawlTaskSave {
  name: string;
  datasourceId: string;
  database: string;
  schema?: string;
  ownerId: string;
  scheduleType: CrawlScheduleType;
  cronExpression?: string;
  excludePattern?: string;
  excludeTables?: string[];
  status: CrawlTaskStatus;
}

export interface CrawlExcludePreview {
  matchedCount: number;
  tables: string[];
  truncated: boolean;
}

export interface CrawlSchedulePreview {
  nextFireTimes: string[];
}
