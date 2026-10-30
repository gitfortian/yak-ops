/**
 * Shared datasource → database → schema → table → column loading for the modeling pickers.
 *
 * <p>Extracted from the field-import dialog under YOIP-12 so the reverse-modeling wizard and the
 * import dialog cannot drift on the three rules that matter: results are scoped to the request key
 * that produced them, a stale response never overwrites a newer selection, and revoking the
 * non-production confirmation invalidates the table and the columns as well as the confirmation.
 */
import { useEffect, useRef, useState } from "react";
import {
  listDataSourceColumns,
  listDataSourceDatabases,
  listDataSourceSchemas,
  listDataSources,
  listDataSourceTables,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
  type DataSourceRecord,
} from "@/service/datasource";
import { resolveDefaultDatabase } from "./catalog-default";

type LoadArea = "datasources" | "databases" | "schemas" | "tables" | "columns";

interface TableResult {
  requestKey: string;
  values: DataSourceCatalogTable[];
}

interface TableSelection {
  requestKey: string;
  selectionKey: string;
  table: DataSourceCatalogTable;
}

interface ColumnResult {
  selectionKey: string;
  values: DataSourceCatalogColumn[];
}

const ENV_RANK: Record<string, number> = { PROD: 0, TEST: 1, DEVELOP: 2 };

const EMPTY_LOADING: Record<LoadArea, boolean> = {
  datasources: false,
  databases: false,
  schemas: false,
  tables: false,
  columns: false,
};

// Stable empty references: callers put these arrays in effect dependencies, and a fresh literal on
// every render would retrigger their effects.
const NO_TABLES: DataSourceCatalogTable[] = [];
const NO_COLUMNS: DataSourceCatalogColumn[] = [];

export interface CatalogPicker {
  datasources: DataSourceRecord[];
  datasource: DataSourceRecord | undefined;
  datasourceId: string;
  selectDatasource: (id: string) => void;
  databases: string[];
  database: string;
  selectDatabase: (value: string) => void;
  schemas: string[];
  schema: string;
  selectSchema: (value: string) => void;
  tableKeyword: string;
  setTableKeyword: (value: string) => void;
  searchTables: () => void;
  tables: DataSourceCatalogTable[];
  /** True once a table query for the current selection has returned, so an empty list is a result. */
  tablesLoaded: boolean;
  selectedTable: DataSourceCatalogTable | null;
  selectTable: (table: DataSourceCatalogTable) => void;
  columns: DataSourceCatalogColumn[];
  loading: Record<LoadArea, boolean>;
  failures: Partial<Record<LoadArea, string>>;
  failureMessages: string[];
  retry: () => void;
  nonProduction: boolean;
  envConfirmed: boolean;
  setEnvConfirmed: (value: boolean) => void;
  blocked: boolean;
}

export function useCatalogPicker(): CatalogPicker {
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [datasourceId, setDatasourceId] = useState("");
  const [databases, setDatabases] = useState<string[]>([]);
  const [database, setDatabase] = useState("");
  const [schemas, setSchemas] = useState<string[]>([]);
  const [schema, setSchema] = useState("");
  const [tableResult, setTableResult] = useState<TableResult | null>(null);
  const [tableKeyword, setTableKeyword] = useState("");
  const [tableSearch, setTableSearch] = useState("");
  const [selectedTable, setSelectedTable] = useState<TableSelection | null>(null);
  const [columnResult, setColumnResult] = useState<ColumnResult | null>(null);
  const [envConfirmed, setEnvConfirmed] = useState(false);
  const [loading, setLoading] = useState<Record<LoadArea, boolean>>(EMPTY_LOADING);
  const [failures, setFailures] = useState<Partial<Record<LoadArea, string>>>({});
  const [retry, setRetry] = useState(0);

  const datasource = datasources.find((item) => item.id === datasourceId);
  // The database effect may not depend on the current choice, or picking one would refetch the list.
  // A retry therefore reads the live value through this ref and keeps it instead of resetting.
  const databaseRef = useRef(database);
  databaseRef.current = database;
  const nonProduction = Boolean(datasource && datasource.environment !== "PROD");
  const blocked = nonProduction && !envConfirmed;
  const tableRequestKey = JSON.stringify([datasourceId, database, schema, tableSearch]);
  const activeSelection =
    !blocked && selectedTable?.requestKey === tableRequestKey ? selectedTable : null;
  const tablesLoaded = !blocked && tableResult?.requestKey === tableRequestKey;
  const tables = tablesLoaded ? tableResult.values : NO_TABLES;
  const columns =
    activeSelection && columnResult?.selectionKey === activeSelection.selectionKey
      ? columnResult.values
      : NO_COLUMNS;
  const failureMessages = Object.values(failures).filter((message): message is string =>
    Boolean(message),
  );

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading((previous) => ({ ...previous, datasources: true }));
    setFailures((previous) => ({ ...previous, datasources: "" }));
    const load = async () => {
      try {
        const values: DataSourceRecord[] = [];
        let pageNo = 1;
        let total = Number.MAX_SAFE_INTEGER;
        while (values.length < total) {
          const page = await listDataSources({ pageNo, pageSize: 100 }, controller.signal);
          if (!active) return;
          values.push(...page.bizData);
          total = page.pagination.total;
          if (page.bizData.length < 100) break;
          pageNo += 1;
        }
        const ordered = [...values].sort(
          (left, right) =>
            (ENV_RANK[left.environment ?? ""] ?? 9) - (ENV_RANK[right.environment ?? ""] ?? 9) ||
            (left.name ?? "").localeCompare(right.name ?? ""),
        );
        setDatasources(ordered);
      } catch {
        if (active)
          setFailures((previous) => ({
            ...previous,
            datasources: "数据源加载失败，请稍后重试",
          }));
      } finally {
        if (active) setLoading((previous) => ({ ...previous, datasources: false }));
      }
    };
    void load();
    return () => {
      active = false;
      controller.abort();
    };
  }, [retry]);

  useEffect(() => {
    if (!datasourceId) {
      setDatabases([]);
      setDatabase("");
      setLoading((previous) => ({ ...previous, databases: false }));
      return;
    }
    let active = true;
    setLoading((previous) => ({ ...previous, databases: true }));
    setFailures((previous) => ({ ...previous, databases: "" }));
    void listDataSourceDatabases(datasourceId)
      .then((values) => {
        if (!active) return;
        setDatabases(values);
        if (databaseRef.current && values.includes(databaseRef.current)) return;
        const resolved = resolveDefaultDatabase(datasource?.database, values);
        setDatabase(resolved.database);
        // A registered database the instance does not offer is not a load failure, but it is the one
        // thing this picker must not guess around, so it travels over the channel both dialogs show.
        setFailures((previous) => ({ ...previous, databases: resolved.hint }));
      })
      .catch(() => {
        if (!active) return;
        setDatabases([]);
        setDatabase("");
        setFailures((previous) => ({
          ...previous,
          databases: "数据库列表读取失败，请检查数据源连接",
        }));
      })
      .finally(() => {
        if (active) setLoading((previous) => ({ ...previous, databases: false }));
      });
    return () => {
      active = false;
    };
  }, [datasourceId, datasource?.database, retry]);

  useEffect(() => {
    if (!datasourceId) {
      setSchemas([]);
      setSchema("");
      setLoading((previous) => ({ ...previous, schemas: false }));
      return;
    }
    let active = true;
    setLoading((previous) => ({ ...previous, schemas: true }));
    setFailures((previous) => ({ ...previous, schemas: "" }));
    setSchemas([]);
    void listDataSourceSchemas(datasourceId, database || undefined)
      .then((values) => {
        if (!active) return;
        setSchemas(values);
        setSchema((previous) =>
          previous && values.includes(previous) ? previous : (values[0] ?? ""),
        );
      })
      .catch(() => {
        if (!active) return;
        setSchemas([]);
        setSchema("");
        setFailures((previous) => ({
          ...previous,
          schemas: "Schema 列表读取失败，请检查数据源连接",
        }));
      })
      .finally(() => {
        if (active) setLoading((previous) => ({ ...previous, schemas: false }));
      });
    return () => {
      active = false;
    };
  }, [datasourceId, database, retry]);

  useEffect(() => {
    if (!datasourceId || blocked) {
      setTableResult(null);
      setLoading((previous) => ({ ...previous, tables: false }));
      setFailures((previous) => ({ ...previous, tables: "" }));
      return;
    }
    let active = true;
    setLoading((previous) => ({ ...previous, tables: true }));
    setFailures((previous) => ({ ...previous, tables: "" }));
    void listDataSourceTables(datasourceId, {
      database: database || undefined,
      schema: schema || undefined,
      keyword: tableSearch || undefined,
      limit: 200,
    })
      .then((values) => {
        if (active) setTableResult({ requestKey: tableRequestKey, values });
      })
      .catch(() => {
        if (!active) return;
        setTableResult({ requestKey: tableRequestKey, values: [] });
        setFailures((previous) => ({
          ...previous,
          tables: "表列表读取失败，请检查数据源连接",
        }));
      })
      .finally(() => {
        if (active) setLoading((previous) => ({ ...previous, tables: false }));
      });
    return () => {
      active = false;
    };
  }, [datasourceId, database, schema, tableSearch, blocked, retry, tableRequestKey]);

  useEffect(() => {
    if (!activeSelection || !datasourceId) {
      setSelectedTable(null);
      setColumnResult(null);
      setLoading((previous) => ({ ...previous, columns: false }));
      setFailures((previous) => ({ ...previous, columns: "" }));
      return;
    }
    let active = true;
    const selection = activeSelection;
    setLoading((previous) => ({ ...previous, columns: true }));
    setFailures((previous) => ({ ...previous, columns: "" }));
    void listDataSourceColumns(datasourceId, {
      database: selection.table.database ?? (database || undefined),
      schema: selection.table.schema ?? (schema || undefined),
      table: selection.table.name,
    })
      .then((values) => {
        if (!active) return;
        setColumnResult({ selectionKey: selection.selectionKey, values });
      })
      .catch(() => {
        if (!active) return;
        setColumnResult({ selectionKey: selection.selectionKey, values: [] });
        setFailures((previous) => ({
          ...previous,
          columns: "字段列表读取失败，请检查数据源连接",
        }));
      })
      .finally(() => {
        if (active) setLoading((previous) => ({ ...previous, columns: false }));
      });
    return () => {
      active = false;
    };
  }, [activeSelection, datasourceId, database, schema, retry]);

  const selectDatasource = (id: string) => {
    setDatasourceId(id);
    setEnvConfirmed(false);
    setDatabases([]);
    setDatabase("");
    setSchemas([]);
    setSchema("");
    setTableKeyword("");
    setTableSearch("");
    setTableResult(null);
    setSelectedTable(null);
    setColumnResult(null);
    setFailures({});
  };

  const selectDatabase = (value: string) => {
    setDatabase(value);
    setSchema("");
    setTableResult(null);
    setSelectedTable(null);
    setColumnResult(null);
  };

  const selectSchema = (value: string) => {
    setSchema(value);
    setTableResult(null);
    setSelectedTable(null);
    setColumnResult(null);
  };

  const selectTable = (row: DataSourceCatalogTable) => {
    const selectionKey = JSON.stringify([
      tableRequestKey,
      row.database ?? null,
      row.schema ?? null,
      row.name,
    ]);
    setSelectedTable({ requestKey: tableRequestKey, selectionKey, table: row });
  };

  return {
    datasources,
    datasource,
    datasourceId,
    selectDatasource,
    databases,
    database,
    selectDatabase,
    schemas,
    schema,
    selectSchema,
    tableKeyword,
    setTableKeyword,
    searchTables: () => setTableSearch(tableKeyword.trim()),
    tables,
    tablesLoaded,
    selectedTable: activeSelection?.table ?? null,
    selectTable,
    columns,
    loading,
    failures,
    failureMessages,
    retry: () => setRetry((value) => value + 1),
    nonProduction,
    envConfirmed,
    setEnvConfirmed,
    blocked,
  };
}
