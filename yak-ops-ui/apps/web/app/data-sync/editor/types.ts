import type { DataSourceCatalogTable, DataSourceRecord } from "@/service/datasource";
import type {
  DataSyncDraft,
  DataSyncTaskRecord,
  DataSyncIncrementalConfig,
  DataSyncWriteMode,
} from "@/service/data-sync";

export interface EditorSource {
  dataSourceId: string;
  database: string;
  schema: string;
  table: string;
  keylessEnabled: boolean;
  chunkKeyColumn: string;
  incremental?: DataSyncIncrementalConfig;
}

export interface EditorSink {
  dataSourceId: string;
  database: string;
  schema: string;
  table: string;
  writeMode: DataSyncWriteMode;
  targetPrimaryKeys: string[];
  schemaChangesEnabled: boolean;
  allowDropColumns: boolean;
}

/** UI state groups settings by the component that owns them, not by the V3 JSON layout. */
export interface EditorForm {
  name: string;
  remark: string;
  debug: boolean;
  source: EditorSource;
  sink: EditorSink;
}

export interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
  error: string;
  refresh: () => void;
}

export const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  debug: false,
  source: {
    dataSourceId: "",
    database: "",
    schema: "",
    table: "",
    keylessEnabled: false,
    chunkKeyColumn: "",
    incremental: undefined,
  },
  sink: {
    dataSourceId: "",
    database: "",
    schema: "",
    table: "",
    writeMode: "APPEND",
    targetPrimaryKeys: [],
    schemaChangesEnabled: true,
    allowDropColumns: false,
  },
};

export interface TaskEditorInput {
  draft: DataSyncDraft;
  task?: DataSyncTaskRecord;
  connections?: DataSourceRecord[];
}
