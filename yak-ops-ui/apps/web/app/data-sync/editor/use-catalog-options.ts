import { useCallback, useEffect, useState } from "react";
import { listDataSourceSchemas, listDataSourceTables, type DataSourceCatalogTable } from "@/service/datasource";
import type { CatalogOptions } from "./types";

export function useCatalogOptions(dataSourceId: string, database: string, schema: string): CatalogOptions {
  const key = JSON.stringify([dataSourceId, database, schema]);
  const [result, setResult] = useState<{
    key: string;
    schemas: string[];
    tables: DataSourceCatalogTable[];
    loading: boolean;
    error: string;
  }>({ key: "", schemas: [], tables: [], loading: false, error: "" });
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  useEffect(() => {
    if (!dataSourceId) return;
    let active = true;
    setResult({ key, schemas: [], tables: [], loading: true, error: "" });
    void Promise.all([
      listDataSourceSchemas(dataSourceId, database || undefined),
      listDataSourceTables(dataSourceId, {
        database: database || undefined,
        schema: schema || undefined,
        limit: 500,
      }),
    ])
      .then(([schemas, tables]) => {
        if (active) setResult({ key, schemas, tables, loading: false, error: "" });
      })
      .catch(() => {
        if (active)
          setResult({ key, schemas: [], tables: [], loading: false, error: "表信息加载失败" });
      });
    return () => {
      active = false;
    };
  }, [key, dataSourceId, database, schema, revision]);
  const current = Boolean(dataSourceId) && result.key === key;
  return {
    schemas: current ? result.schemas : [],
    tables: current ? result.tables : [],
    loading: Boolean(dataSourceId) && (!current || result.loading),
    error: current ? result.error : "",
    refresh,
  };
}
