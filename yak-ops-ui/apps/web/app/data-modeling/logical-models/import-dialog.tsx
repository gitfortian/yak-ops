import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogTitle,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";
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
import {
  columnToField,
  findDuplicateFieldCodes,
  mapPhysicalColumn,
  normalizeFieldCode,
} from "./field-mapping";
import type { FieldDraft } from "./fields";
import { newFieldKey } from "./fields";

interface Props {
  existing: FieldDraft[];
  onClose: () => void;
  onImport: (rows: FieldDraft[]) => void;
}

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
const ENV_LABEL: Record<string, string> = { PROD: "生产", TEST: "测试", DEVELOP: "开发" };

const mappingBadge = (column: DataSourceCatalogColumn) => {
  const mapping = mapPhysicalColumn(column);
  if (mapping.kind === "MAPPED") return <Badge tone="success">→ {mapping.dataType}</Badge>;
  if (mapping.kind === "REVIEW") return <Badge tone="warning">需确认</Badge>;
  return <Badge tone="danger">不支持</Badge>;
};

export default function ImportDialog({ existing, onClose, onImport }: Props) {
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
  const [selected, setSelected] = useState<string[]>([]);
  const [envConfirmed, setEnvConfirmed] = useState(false);
  const [loading, setLoading] = useState<Record<LoadArea, boolean>>({
    datasources: false,
    databases: false,
    schemas: false,
    tables: false,
    columns: false,
  });
  const [failures, setFailures] = useState<Partial<Record<LoadArea, string>>>({});
  const [retry, setRetry] = useState(0);

  const datasource = datasources.find((item) => item.id === datasourceId);
  const nonProduction = Boolean(datasource && datasource.environment !== "PROD");
  const blocked = nonProduction && !envConfirmed;
  const takenCodes = useMemo(() => new Set(existing.map((row) => row.enCode.trim())), [existing]);
  const tableRequestKey = JSON.stringify([datasourceId, database, schema, tableSearch]);
  const activeSelection =
    !blocked && selectedTable?.requestKey === tableRequestKey ? selectedTable : null;
  const tables = !blocked && tableResult?.requestKey === tableRequestKey ? tableResult.values : [];
  const columns =
    activeSelection && columnResult?.selectionKey === activeSelection.selectionKey
      ? columnResult.values
      : [];
  const conflictingCodes = useMemo(() => findDuplicateFieldCodes(columns), [columns]);
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
        setDatabase((previous) =>
          previous && values.includes(previous) ? previous : (values[0] ?? ""),
        );
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
  }, [datasourceId, retry]);

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
      setSelected([]);
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
        const duplicates = findDuplicateFieldCodes(values);
        setColumnResult({ selectionKey: selection.selectionKey, values });
        setSelected(
          values
            .filter((column) => {
              const code = normalizeFieldCode(column.name);
              return (
                mapPhysicalColumn(column).kind === "MAPPED" &&
                !takenCodes.has(code) &&
                !duplicates.has(code)
              );
            })
            .map((column) => column.name),
        );
      })
      .catch(() => {
        if (!active) return;
        setColumnResult({ selectionKey: selection.selectionKey, values: [] });
        setSelected([]);
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
  }, [activeSelection, datasourceId, database, schema, retry, takenCodes]);

  const resetTableSelection = () => {
    setTableResult(null);
    setSelectedTable(null);
    setColumnResult(null);
    setSelected([]);
  };

  const selectTable = (row: DataSourceCatalogTable) => {
    const selectionKey = JSON.stringify([
      tableRequestKey,
      row.database ?? null,
      row.schema ?? null,
      row.name,
    ]);
    setSelectedTable({ requestKey: tableRequestKey, selectionKey, table: row });
    setSelected([]);
  };

  const tableColumns: TableColumns<DataSourceCatalogTable> = [
    { title: "表名", dataIndex: "name", width: 260, ellipsis: true },
    { title: "类型", dataIndex: "type", width: 90 },
    { title: "备注", dataIndex: "remarks", ellipsis: true },
    {
      title: "操作",
      key: "action",
      width: 80,
      align: "center",
      render: (_, row) => (
        <Button
          size="small"
          variant="ghost"
          className="px-0.5 font-normal [color:var(--yak-color-primary)] hover:[color:var(--yak-color-primary)]"
          onClick={() => selectTable(row)}
        >
          选择
        </Button>
      ),
    },
  ];

  const columnColumns: TableColumns<DataSourceCatalogColumn> = [
    {
      title: "",
      key: "pick",
      width: 44,
      align: "center",
      render: (_, row) => {
        const code = normalizeFieldCode(row.name);
        const duplicate = takenCodes.has(code) || conflictingCodes.has(code);
        return (
          <Checkbox
            aria-label={`选择字段 ${row.name}`}
            checked={selected.includes(row.name)}
            disabled={duplicate}
            onCheckedChange={(checked) =>
              setSelected((previous) =>
                checked ? [...previous, row.name] : previous.filter((name) => name !== row.name),
              )
            }
          />
        );
      },
    },
    { title: "字段名", dataIndex: "name", width: 200, ellipsis: true },
    { title: "物理类型", dataIndex: "typeName", width: 150, ellipsis: true },
    {
      title: "可空",
      key: "nullable",
      width: 66,
      render: (_, row) => (row.nullable === false ? "否" : "是"),
    },
    {
      title: "主键",
      key: "pk",
      width: 66,
      render: (_, row) => (row.primaryKey ? "是" : "—"),
    },
    { title: "映射", key: "mapping", width: 96, render: (_, row) => mappingBadge(row) },
    {
      title: "说明",
      key: "note",
      ellipsis: true,
      render: (_, row) => {
        const code = normalizeFieldCode(row.name);
        if (takenCodes.has(code)) return "字段列表中已存在同名字段";
        if (conflictingCodes.has(code))
          return `多个物理字段将生成编码 ${code}，请手工添加后调整编码`;
        return mapPhysicalColumn(row).note ?? "—";
      },
    },
  ];

  const importable = useMemo(
    () =>
      columns
        .filter((column) => {
          const code = normalizeFieldCode(column.name);
          return (
            selected.includes(column.name) && !takenCodes.has(code) && !conflictingCodes.has(code)
          );
        })
        .map((column) => columnToField(column, mapPhysicalColumn(column), newFieldKey())),
    [columns, selected, takenCodes, conflictingCodes],
  );
  const mappedCount = columns.filter((column) => {
    const code = normalizeFieldCode(column.name);
    return (
      mapPhysicalColumn(column).kind === "MAPPED" &&
      !takenCodes.has(code) &&
      !conflictingCodes.has(code)
    );
  }).length;
  const grouped = importable.filter((row) => row.dataType === null).length;

  const selectAllMapped = () =>
    setSelected(
      columns
        .filter((column) => {
          const code = normalizeFieldCode(column.name);
          return (
            mapPhysicalColumn(column).kind === "MAPPED" &&
            !takenCodes.has(code) &&
            !conflictingCodes.has(code)
          );
        })
        .map((column) => column.name),
    );

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="w-[1000px] rounded-xl p-5 shadow-none">
        <DialogTitle className="text-sm font-medium">从表/视图导入字段</DialogTitle>
        <p className="mt-2 text-xs leading-5 text-[#667475]">
          只读取当前工作空间内的数据源。系统只自动映射语义明确的物理类型；标红的行会保留在字段列表里，
          保存前必须为它选定类型或删除。
        </p>
        <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3">
          <label className="grid gap-1 text-xs">
            <span className="font-medium">数据源</span>
            <Select
              value={datasourceId || null}
              size="small"
              disabled={loading.datasources}
              items={datasources.map((item) => ({
                value: item.id ?? "",
                label: `${item.name ?? item.id}（${ENV_LABEL[item.environment ?? ""] ?? item.environment ?? "未知"}）`,
              }))}
              onValueChange={(value) => {
                setDatasourceId(value ?? "");
                setEnvConfirmed(false);
                setDatabases([]);
                setDatabase("");
                setSchemas([]);
                setSchema("");
                setTableKeyword("");
                setTableSearch("");
                resetTableSelection();
                setFailures({});
              }}
            >
              <SelectTrigger variant="outlined" aria-label="选择数据源">
                <SelectValue placeholder={loading.datasources ? "数据源加载中" : "请选择数据源"} />
              </SelectTrigger>
              <SelectContent>
                {datasources.map((item) => (
                  <SelectItem key={item.id} value={item.id ?? ""}>
                    <SelectItemText>
                      {item.name ?? item.id}（
                      {ENV_LABEL[item.environment ?? ""] ?? item.environment ?? "未知"}）
                    </SelectItemText>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
          {databases.length > 0 && (
            <label className="grid gap-1 text-xs">
              <span className="font-medium">数据库</span>
              <Select
                value={database || null}
                size="small"
                items={databases.map((value) => ({ value, label: value }))}
                onValueChange={(value) => {
                  setDatabase(value ?? "");
                  setSchema("");
                  resetTableSelection();
                }}
              >
                <SelectTrigger variant="outlined" aria-label="选择数据库">
                  <SelectValue placeholder="请选择数据库" />
                </SelectTrigger>
                <SelectContent>
                  {databases.map((value) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{value}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          )}
          {schemas.length > 0 && (
            <label className="grid gap-1 text-xs">
              <span className="font-medium">Schema</span>
              <Select
                value={schema || null}
                size="small"
                items={schemas.map((value) => ({ value, label: value }))}
                onValueChange={(value) => {
                  setSchema(value ?? "");
                  resetTableSelection();
                }}
              >
                <SelectTrigger variant="outlined" aria-label="选择 Schema">
                  <SelectValue placeholder="请选择 Schema" />
                </SelectTrigger>
                <SelectContent>
                  {schemas.map((value) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{value}</SelectItemText>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
          )}
        </div>
        {nonProduction && (
          <div className="mt-3 flex items-start gap-2 rounded-lg border border-[#fecdca] bg-[#fffbfa] px-3 py-2 text-xs text-[#93370d]">
            <Checkbox
              aria-label="确认非生产环境"
              checked={envConfirmed}
              onCheckedChange={(checked) => setEnvConfirmed(checked)}
            />
            <span>
              即将从「{ENV_LABEL[datasource?.environment ?? ""] ?? datasource?.environment}
              」环境读取字段， 请确认连接的是目标库；环境只作提示，不做拦截。
            </span>
          </div>
        )}
        {failureMessages.length > 0 && (
          <div className="mt-3">
            <Alert>
              <div>
                {failureMessages.map((message) => (
                  <div key={message}>{message}</div>
                ))}
              </div>
              <Button size="small" onClick={() => setRetry((value) => value + 1)}>
                重试
              </Button>
            </Alert>
          </div>
        )}
        <div className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-2">
          <section className="min-w-0">
            <div className="mb-2 flex items-center gap-2">
              <h3 className="text-xs font-medium">表 / 视图</h3>
              <form
                className="ml-auto flex items-center gap-1"
                onSubmit={(event) => {
                  event.preventDefault();
                  setTableSearch(tableKeyword.trim());
                }}
              >
                <Input
                  size="small"
                  variant="outlined"
                  value={tableKeyword}
                  maxLength={128}
                  disabled={!datasourceId || blocked}
                  aria-label="搜索表名"
                  placeholder="表名关键字"
                  onChange={(event) => setTableKeyword(event.target.value)}
                />
                <Button size="small" type="submit" disabled={!datasourceId || blocked}>
                  查询
                </Button>
              </form>
            </div>
            {loading.tables ? (
              <div className="flex h-40 items-center justify-center">
                <Spinner />
              </div>
            ) : (
              <div className="h-64 overflow-auto">
                <Table<DataSourceCatalogTable>
                  rowKey="name"
                  columns={tableColumns}
                  dataSource={tables}
                  size="small"
                  bordered={false}
                  emptyText={
                    blocked
                      ? "请先确认非生产环境"
                      : failures.tables
                        ? "表列表读取失败，请重试"
                        : "未查询到表或视图"
                  }
                  pagination={false}
                  scroll={{ x: 560 }}
                />
              </div>
            )}
          </section>
          <section className="min-w-0">
            <div className="mb-2 flex items-center justify-between gap-2">
              <h3 className="text-xs font-medium">
                字段{activeSelection ? ` · ${activeSelection.table.name}` : ""}
              </h3>
              <Button
                size="small"
                variant="ghost"
                disabled={mappedCount === 0}
                onClick={selectAllMapped}
              >
                全选可映射（{mappedCount}）
              </Button>
            </div>
            {loading.columns ? (
              <div className="flex h-40 items-center justify-center">
                <Spinner />
              </div>
            ) : (
              <div className="h-64 overflow-auto">
                <Table<DataSourceCatalogColumn>
                  rowKey="name"
                  columns={columnColumns}
                  dataSource={columns}
                  size="small"
                  bordered={false}
                  emptyText={
                    failures.columns
                      ? "字段列表读取失败，请重试"
                      : activeSelection
                        ? "该表没有可读取的字段"
                        : "先在左侧选择一张表"
                  }
                  pagination={false}
                  scroll={{ x: 660 }}
                />
              </div>
            )}
          </section>
        </div>
        <div className="mt-4 flex items-center justify-between">
          <span className="text-xs text-[#667475]">
            已选 {selected.length} 个字段
            {grouped > 0 && `，其中 ${grouped} 个需要选定类型`}
          </span>
          <div className="flex gap-2">
            <Button size="small" onClick={onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={blocked || importable.length === 0}
              onClick={() => {
                if (!blocked && importable.length > 0) onImport(importable);
              }}
            >
              导入 {importable.length} 个字段
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
