import {
  Badge,
  Button,
  Field,
  FieldLabel,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

import {
  listDataSources,
  listDataSourceSchemas,
  listDataSourceTables,
  type DataSourceCatalogTable,
  type DataSourceRecord,
} from "@/service/datasource";
import {
  createDataSyncTask,
  getDataSyncTask,
  previewDataSyncMapping,
  runDataSyncTask,
  updateDataSyncTask,
  type DataSyncFieldMapping,
  type DataSyncMappingPreview,
  type DataSyncRealtimeConfig,
  type DataSyncRuntimeConfig,
  type DataSyncTaskSavePayload,
  type DataSyncType,
} from "@/service/data-sync";

interface EditorForm {
  name: string;
  remark: string;
  sourceDataSourceId: string;
  sourceDatabase: string;
  sourceSchema: string;
  sourceTable: string;
  targetDataSourceId: string;
  targetDatabase: string;
  targetSchema: string;
  targetTable: string;
  runtimeConfig: DataSyncRuntimeConfig;
  realtimeConfig: DataSyncRealtimeConfig;
}

interface CatalogOptions {
  schemas: string[];
  tables: DataSourceCatalogTable[];
  loading: boolean;
}

const EMPTY_RUNTIME: DataSyncRuntimeConfig = {
  fetchSize: 500,
  readBatchSize: 500,
  writeBatchSize: 500,
  sourceParallelism: 1,
  timeoutSeconds: 30,
};

const EMPTY_REALTIME: DataSyncRealtimeConfig = {
  checkpointIntervalSeconds: 10,
  queueCapacity: 64,
  pollBatchSize: 500,
  writeBatchSize: 500,
  timeoutSeconds: 30,
};

const EMPTY_FORM: EditorForm = {
  name: "",
  remark: "",
  sourceDataSourceId: "",
  sourceDatabase: "",
  sourceSchema: "",
  sourceTable: "",
  targetDataSourceId: "",
  targetDatabase: "",
  targetSchema: "",
  targetTable: "",
  runtimeConfig: EMPTY_RUNTIME,
  realtimeConfig: EMPTY_REALTIME,
};

const tableKey = (table: DataSourceCatalogTable) =>
  [table.database || "", table.schema || "", table.name].join("|");

const tableLabel = (table: DataSourceCatalogTable) =>
  [table.schema, table.name].filter(Boolean).join(".") || table.name;

const selectedTableKey = (
  tables: DataSourceCatalogTable[],
  database: string,
  schema: string,
  tableName: string,
) =>
  tables.find(
    (table) =>
      table.name === tableName &&
      (table.database || "") === database &&
      (table.schema || "") === schema,
  )
    ? [database, schema, tableName].join("|")
    : undefined;

function useCatalogOptions(dataSourceId: string, database: string, schema: string): CatalogOptions {
  const [schemas, setSchemas] = useState<string[]>([]);
  const [tables, setTables] = useState<DataSourceCatalogTable[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!dataSourceId) {
      setSchemas([]);
      setTables([]);
      return;
    }
    let active = true;
    setLoading(true);
    void Promise.all([
      listDataSourceSchemas(dataSourceId, database || undefined),
      listDataSourceTables(dataSourceId, {
        database: database || undefined,
        schema: schema || undefined,
        limit: 500,
      }),
    ])
      .then(([schemaOptions, tableOptions]) => {
        if (!active) return;
        setSchemas(schemaOptions || []);
        setTables(tableOptions || []);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataSourceId, database, schema]);

  return { schemas, tables, loading };
}

interface DataSourceEndpointCardProps {
  title: string;
  dataSources: DataSourceRecord[];
  dataSourceId: string;
  onDataSourceChange: (value: string) => void;
}

function DataSourceEndpointCard({
  title,
  dataSources,
  dataSourceId,
  onDataSourceChange,
}: DataSourceEndpointCardProps) {
  const selectedDataSource = dataSources.find((item) => item.id === dataSourceId);
  const dataSourceItems = useMemo(
    () =>
      Object.fromEntries(
        dataSources.flatMap((item) => (item.id ? [[item.id, item.name || item.id] as const] : [])),
      ),
    [dataSources],
  );
  const scopeText = [selectedDataSource?.database, selectedDataSource?.schema]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="rounded-lg border border-[#e6e8eb] bg-white p-4">
      <div className="text-sm font-semibold text-[#344054]">{title}</div>
      <div className="mt-4 space-y-3">
        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>类型</FieldLabel>
          <div className="text-[13px] text-[#344054]">{selectedDataSource?.dbType || "-"}</div>
        </Field>

        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5">
            数据源
          </FieldLabel>
          <div className="space-y-1">
            <Select
              size="small"
              items={dataSourceItems}
              value={dataSourceId || undefined}
              onValueChange={(value) => onDataSourceChange(String(value || ""))}
            >
              <SelectTrigger variant="outlined">
                <SelectValue placeholder="请选择数据源" />
              </SelectTrigger>
              <SelectContent>
                {dataSources.map((item) =>
                  item.id ? (
                    <SelectItem key={item.id} value={item.id}>
                      <SelectItemText>{item.name || item.id}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ) : null,
                )}
              </SelectContent>
            </Select>
            {scopeText ? (
              <div className="px-1 text-xs text-[#98a2b3]">连接范围：{scopeText}</div>
            ) : null}
          </div>
        </Field>
      </div>
    </div>
  );
}

interface TableSectionProps {
  title: string;
  dataSourceId: string;
  boundSchema?: string;
  database: string;
  schema: string;
  table: string;
  catalog: CatalogOptions;
  onSchemaChange: (value: string) => void;
  onTableChange: (table: DataSourceCatalogTable) => void;
}

function TableSection({
  title,
  dataSourceId,
  boundSchema,
  database,
  schema,
  table,
  catalog,
  onSchemaChange,
  onTableChange,
}: TableSectionProps) {
  const tableValue = selectedTableKey(catalog.tables, database, schema, table);
  const tableItems = useMemo(
    () => Object.fromEntries(catalog.tables.map((item) => [tableKey(item), tableLabel(item)])),
    [catalog.tables],
  );
  const requiresSchema = !boundSchema && catalog.schemas.length > 0;
  const tableDisabled = !dataSourceId || catalog.loading || (requiresSchema && !schema);

  return (
    <section className="rounded-lg border border-[#e6e8eb] bg-white">
      <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
        {title}
      </h2>
      <div className="space-y-3 p-4">
        {requiresSchema ? (
          <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>Schema</FieldLabel>
            <Select
              size="small"
              value={schema || undefined}
              onValueChange={(value) => onSchemaChange(String(value || ""))}
            >
              <SelectTrigger variant="outlined">
                <SelectValue placeholder="请选择 Schema" />
              </SelectTrigger>
              <SelectContent>
                {catalog.schemas.map((item) => (
                  <SelectItem key={item} value={item}>
                    <SelectItemText>{item}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        ) : null}

        <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>表</FieldLabel>
          <Select
            size="small"
            items={tableItems}
            disabled={tableDisabled}
            value={tableValue}
            onValueChange={(value) => {
              const selected = catalog.tables.find((item) => tableKey(item) === value);
              if (selected) onTableChange(selected);
            }}
          >
            <SelectTrigger variant="outlined">
              <SelectValue
                placeholder={
                  !dataSourceId
                    ? "请先选择数据源"
                    : requiresSchema && !schema
                      ? "请先选择 Schema"
                      : catalog.loading
                        ? "正在读取 Catalog..."
                        : "请选择表"
                }
              />
            </SelectTrigger>
            <SelectContent>
              {catalog.tables.map((item) => (
                <SelectItem key={tableKey(item)} value={tableKey(item)}>
                  <SelectItemText>{tableLabel(item)}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      </div>
    </section>
  );
}

interface DataSyncTaskEditorPageProps {
  syncType: DataSyncType;
}

export function DataSyncTaskEditorPage({ syncType }: DataSyncTaskEditorPageProps) {
  const realtime = syncType === "REALTIME";
  const basePath = realtime ? "/realtime-sync" : "/offline-sync";
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();
  const draft = (
    location.state as {
      draft?: {
        name?: string;
        sourceDataSourceId?: string;
        targetDataSourceId?: string;
      };
    } | null
  )?.draft;

  const [form, setForm] = useState<EditorForm>(() => ({
    ...EMPTY_FORM,
    name: draft?.name || "",
    sourceDataSourceId: draft?.sourceDataSourceId || "",
    targetDataSourceId: draft?.targetDataSourceId || "",
    runtimeConfig: { ...EMPTY_RUNTIME },
    realtimeConfig: { ...EMPTY_REALTIME },
  }));
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [loading, setLoading] = useState(editing);
  const [saving, setSaving] = useState(false);
  const [mappingLoading, setMappingLoading] = useState(false);
  const [mapping, setMapping] = useState<DataSyncMappingPreview>();
  const [runtimeOpen, setRuntimeOpen] = useState(false);

  const sourceCatalog = useCatalogOptions(
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
  );
  const targetCatalog = useCatalogOptions(
    form.targetDataSourceId,
    form.targetDatabase,
    form.targetSchema,
  );

  const sourceDataSources = useMemo(
    () => (realtime ? dataSources.filter((item) => item.dbType === "MYSQL") : dataSources),
    [dataSources, realtime],
  );
  const targetDataSources = useMemo(
    () =>
      realtime
        ? dataSources.filter((item) => ["MYSQL", "POSTGRE_SQL", "ORACLE"].includes(item.dbType || ""))
        : dataSources,
    [dataSources, realtime],
  );
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.sourceDataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.targetDataSourceId);

  useEffect(() => {
    void listDataSources({ pageNo: 1, pageSize: 200 }).then((result) =>
      setDataSources(result?.bizData || []),
    );
  }, []);

  useEffect(() => {
    if (dataSources.length === 0) return;
    setForm((current) => {
      const source = dataSources.find((item) => item.id === current.sourceDataSourceId);
      const target = dataSources.find((item) => item.id === current.targetDataSourceId);
      return {
        ...current,
        sourceDatabase: source?.database || current.sourceDatabase,
        sourceSchema: source?.schema || current.sourceSchema,
        targetDatabase: target?.database || current.targetDatabase,
        targetSchema: target?.schema || current.targetSchema,
      };
    });
  }, [dataSources]);

  useEffect(() => {
    if (!id) return;
    let active = true;
    setLoading(true);
    void getDataSyncTask(id)
      .then((task) => {
        if (!active) return;
        setForm({
          name: task.name,
          remark: task.remark || "",
          sourceDataSourceId: task.sourceDataSourceId,
          sourceDatabase: task.sourceDatabase || "",
          sourceSchema: task.sourceSchema || "",
          sourceTable: task.sourceTable,
          targetDataSourceId: task.targetDataSourceId,
          targetDatabase: task.targetDatabase || "",
          targetSchema: task.targetSchema || "",
          targetTable: task.targetTable,
          runtimeConfig: task.runtimeConfig || { ...EMPTY_RUNTIME },
          realtimeConfig: task.realtimeConfig || { ...EMPTY_REALTIME },
        });
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  const mappingPayload = useMemo(
    () =>
      form.sourceDataSourceId && form.sourceTable && form.targetDataSourceId && form.targetTable
        ? {
            sourceDataSourceId: form.sourceDataSourceId,
            sourceDatabase: form.sourceDatabase || undefined,
            sourceSchema: form.sourceSchema || undefined,
            sourceTable: form.sourceTable,
            targetDataSourceId: form.targetDataSourceId,
            targetDatabase: form.targetDatabase || undefined,
            targetSchema: form.targetSchema || undefined,
            targetTable: form.targetTable,
          }
        : undefined,
    [
      form.sourceDataSourceId,
      form.sourceDatabase,
      form.sourceSchema,
      form.sourceTable,
      form.targetDataSourceId,
      form.targetDatabase,
      form.targetSchema,
      form.targetTable,
    ],
  );

  useEffect(() => {
    if (!mappingPayload) {
      setMapping(undefined);
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      setMappingLoading(true);
      void previewDataSyncMapping(mappingPayload)
        .then((result) => {
          if (active) setMapping(result);
        })
        .finally(() => {
          if (active) setMappingLoading(false);
        });
    }, 200);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [mappingPayload]);

  const mappingColumns: TableColumns<DataSyncFieldMapping> = [
    { key: "sourceName", title: "来源字段", dataIndex: "sourceName", minWidth: 180 },
    {
      key: "sourceType",
      title: "来源类型",
      minWidth: 180,
      render: (_value, record) => record.sourceType || "-",
    },
    {
      key: "targetName",
      title: "目标字段",
      minWidth: 180,
      render: (_value, record) => record.targetName || "-",
    },
    {
      key: "targetType",
      title: "目标类型",
      minWidth: 180,
      render: (_value, record) => record.targetType || "-",
    },
    {
      key: "status",
      title: "状态",
      width: 160,
      render: (_value, record) =>
        record.compatible ? (
          <Badge tone="success">兼容</Badge>
        ) : (
          <div className="space-y-1">
            <Badge tone="danger">不兼容</Badge>
            {record.message ? <div className="text-xs text-[#d92d20]">{record.message}</div> : null}
          </div>
        ),
    },
  ];

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const patchRuntime = (key: keyof DataSyncRuntimeConfig, value: string) => {
    const parsed = Number(value);
    setForm((current) => ({
      ...current,
      runtimeConfig: {
        ...current.runtimeConfig,
        [key]: Number.isFinite(parsed) ? parsed : 0,
      },
    }));
  };

  const patchRealtime = (key: keyof DataSyncRealtimeConfig, value: string) => {
    const parsed = Number(value);
    setForm((current) => ({
      ...current,
      realtimeConfig: {
        ...current.realtimeConfig,
        [key]: Number.isFinite(parsed) ? parsed : 0,
      },
    }));
  };

  const patchOptionalSplitSize = (value: string) => {
    const parsed = Number(value);
    setForm((current) => ({
      ...current,
      runtimeConfig: {
        ...current.runtimeConfig,
        splitSize: value.trim() && Number.isFinite(parsed) ? parsed : undefined,
      },
    }));
  };

  const payload = (): DataSyncTaskSavePayload => {
    const common = {
      name: form.name.trim(),
      sourceDataSourceId: form.sourceDataSourceId,
      sourceDatabase: form.sourceDatabase || undefined,
      sourceSchema: form.sourceSchema || undefined,
      sourceTable: form.sourceTable,
      targetDataSourceId: form.targetDataSourceId,
      targetDatabase: form.targetDatabase || undefined,
      targetSchema: form.targetSchema || undefined,
      targetTable: form.targetTable,
      remark: form.remark.trim() || undefined,
    };
    return realtime
      ? { ...common, syncType: "REALTIME", realtimeConfig: form.realtimeConfig }
      : { ...common, syncType: "OFFLINE", runtimeConfig: form.runtimeConfig };
  };

  const canSave =
    form.name.trim() &&
    mapping?.compatible &&
    !mappingLoading &&
    !sourceCatalog.loading &&
    !targetCatalog.loading;

  const save = async (runAfterSave = false) => {
    if (!canSave || saving) return;
    setSaving(true);
    try {
      const saved =
        editing && id
          ? await updateDataSyncTask(id, payload())
          : await createDataSyncTask(payload());
      if (runAfterSave) {
        const instance = await runDataSyncTask(saved.id);
        toast.success(realtime ? "实时同步任务已保存并启动" : "同步任务已保存并启动");
        navigate(realtime ? basePath : `/offline-sync/instances/${instance.id}`, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存" : "同步任务已创建");
      navigate(`${basePath}/${saved.id}`, { replace: true });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步任务...</div>;
  }

  return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={
          editing
            ? form.name || (realtime ? "编辑实时同步任务" : "编辑离线同步任务")
            : realtime
              ? "新建实时同步任务"
              : "新建离线同步任务"
        }
        description={
          realtime
            ? "MySQL CDC 单表实时同步 · 首次全量后持续消费 Binlog"
            : "单表离线同步 · 自动同名字段映射"
        }
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            <Button size="small" disabled={saving} onClick={() => navigate(basePath)}>
              取消
            </Button>
            <Button size="small" loading={saving} disabled={!canSave} onClick={() => void save()}>
              保存
            </Button>
            <Button
              size="small"
              variant="primary"
              loading={saving}
              disabled={!canSave}
              onClick={() => void save(true)}
            >
              {realtime ? "保存并启动" : "保存并运行"}
            </Button>
          </>
        }
      />

      <div className="flex gap-5 px-6 pb-8 pt-5 max-md:px-4">
        <main className="min-w-0 flex-1 space-y-4">
          {realtime ? (
            <div className="rounded-lg border border-[#b2ccff] bg-[#f5f8ff] px-4 py-3 text-xs leading-5 text-[#344054]">
              首次启动会先同步来源表当前全量数据，随后持续消费 MySQL Binlog；停止后再次启动同一任务版本会从已保存的 CDC 状态继续。
            </div>
          ) : null}

          <section id="basic" className="rounded-lg border border-[#e6e8eb] bg-white">
            <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
              基本信息
            </h2>
            <div className="space-y-3 p-4">
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel required>任务名称</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  maxLength={128}
                  value={form.name}
                  placeholder="请输入任务名称"
                  onChange={(event) => patch("name", event.target.value)}
                />
              </Field>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
                <FieldLabel className="pt-1.5">备注</FieldLabel>
                <Textarea
                  size="small"
                  rows={2}
                  maxLength={500}
                  value={form.remark}
                  className="min-h-[56px] resize-none"
                  placeholder="可选"
                  onValueChange={(value) => patch("remark", value)}
                />
              </Field>
            </div>
          </section>

          <section id="datasource" className="rounded-lg border border-[#e6e8eb] bg-white">
            <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
              数据源
            </h2>
            <div className="grid grid-cols-2 gap-3 p-4 max-lg:grid-cols-1">
              <DataSourceEndpointCard
                title="来源"
                dataSources={sourceDataSources}
                dataSourceId={form.sourceDataSourceId}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setForm((current) => ({
                    ...current,
                    sourceDataSourceId: value,
                    sourceDatabase: selected?.database || "",
                    sourceSchema: selected?.schema || "",
                    sourceTable: "",
                  }));
                }}
              />
              <DataSourceEndpointCard
                title="去向"
                dataSources={targetDataSources}
                dataSourceId={form.targetDataSourceId}
                onDataSourceChange={(value) => {
                  const selected = dataSources.find((item) => item.id === value);
                  setForm((current) => ({
                    ...current,
                    targetDataSourceId: value,
                    targetDatabase: selected?.database || "",
                    targetSchema: selected?.schema || "",
                    targetTable: "",
                  }));
                }}
              />
            </div>
          </section>

          <div id="source">
            <TableSection
              title="数据来源"
              dataSourceId={form.sourceDataSourceId}
              boundSchema={selectedSourceDataSource?.schema}
              database={form.sourceDatabase}
              schema={form.sourceSchema}
              table={form.sourceTable}
              catalog={sourceCatalog}
              onSchemaChange={(value) =>
                setForm((current) => ({ ...current, sourceSchema: value, sourceTable: "" }))
              }
              onTableChange={(table) =>
                setForm((current) => ({
                  ...current,
                  sourceDatabase: current.sourceDatabase || table.database || "",
                  sourceSchema: current.sourceSchema || table.schema || "",
                  sourceTable: table.name,
                }))
              }
            />
          </div>

          <div id="target">
            <TableSection
              title="数据去向"
              dataSourceId={form.targetDataSourceId}
              boundSchema={selectedTargetDataSource?.schema}
              database={form.targetDatabase}
              schema={form.targetSchema}
              table={form.targetTable}
              catalog={targetCatalog}
              onSchemaChange={(value) =>
                setForm((current) => ({ ...current, targetSchema: value, targetTable: "" }))
              }
              onTableChange={(table) =>
                setForm((current) => ({
                  ...current,
                  targetDatabase: current.targetDatabase || table.database || "",
                  targetSchema: current.targetSchema || table.schema || "",
                  targetTable: table.name,
                }))
              }
            />
          </div>

          <section
            id="mapping"
            className="overflow-hidden rounded-lg border border-[#e6e8eb] bg-white"
          >
            <div className="flex items-center justify-between border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5">
              <h2 className="text-sm font-semibold text-[#344054]">字段映射</h2>
              {mapping ? (
                mapping.compatible ? (
                  <Badge tone="success">字段兼容</Badge>
                ) : (
                  <Badge tone="danger">存在不兼容字段</Badge>
                )
              ) : null}
            </div>
            {!mappingPayload ? (
              <div className="px-4 py-10 text-center text-sm text-[#98a2b3]">
                请选择来源表和目标表
              </div>
            ) : (
              <Table<DataSyncFieldMapping>
                columns={mappingColumns}
                dataSource={mapping?.mappings || []}
                rowKey="sourceName"
                loading={mappingLoading}
                bordered
                size="small"
                pagination={false}
                emptyText="暂无字段"
                scroll={{ x: 900 }}
              />
            )}
          </section>

          <section id="runtime" className="rounded-lg border border-[#e6e8eb] bg-white">
            <button
              type="button"
              className="flex w-full cursor-pointer items-center justify-between border-0 bg-[#fafafa] px-4 py-2.5 text-left"
              onClick={() => setRuntimeOpen((value) => !value)}
            >
              <span className="text-sm font-semibold text-[#344054]">运行参数</span>
              {runtimeOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            </button>
            {runtimeOpen ? (
              <div className="grid grid-cols-2 gap-x-6 gap-y-3 border-t border-[#eef0f3] p-4 max-lg:grid-cols-1">
                {realtime
                  ? (
                      [
                        ["checkpointIntervalSeconds", "Checkpoint 周期（秒）"],
                        ["queueCapacity", "CDC 队列容量"],
                        ["pollBatchSize", "CDC 读取批次"],
                        ["writeBatchSize", "写入 Batch Size"],
                        ["timeoutSeconds", "超时时间（秒）"],
                      ] as const
                    ).map(([key, label]) => (
                      <Field
                        key={key}
                        className="grid grid-cols-[140px_minmax(0,1fr)] items-center !gap-3"
                      >
                        <FieldLabel>{label}</FieldLabel>
                        <Input
                          type="number"
                          min={1}
                          size="small"
                          variant="outlined"
                          value={String(form.realtimeConfig[key])}
                          onChange={(event) => patchRealtime(key, event.target.value)}
                        />
                      </Field>
                    ))
                  : (
                      [
                        ["fetchSize", "Fetch Size"],
                        ["readBatchSize", "读取 Batch Size"],
                        ["writeBatchSize", "写入 Batch Size"],
                        ["sourceParallelism", "Source 并行度"],
                        ["timeoutSeconds", "超时时间（秒）"],
                      ] as const
                    ).map(([key, label]) => (
                      <Field
                        key={key}
                        className="grid grid-cols-[140px_minmax(0,1fr)] items-center !gap-3"
                      >
                        <FieldLabel>{label}</FieldLabel>
                        <Input
                          type="number"
                          min={1}
                          max={key === "sourceParallelism" ? 16 : undefined}
                          size="small"
                          variant="outlined"
                          value={String(form.runtimeConfig[key])}
                          onChange={(event) => patchRuntime(key, event.target.value)}
                        />
                      </Field>
                    ))}
                {!realtime ? (
                  <Field className="grid grid-cols-[140px_minmax(0,1fr)] items-center !gap-3">
                    <FieldLabel>Split Size</FieldLabel>
                    <Input
                      type="number"
                      min={1}
                      max={10000000}
                      size="small"
                      variant="outlined"
                      value={form.runtimeConfig.splitSize ? String(form.runtimeConfig.splitSize) : ""}
                      placeholder="留空则整表读取"
                      onChange={(event) => patchOptionalSplitSize(event.target.value)}
                    />
                  </Field>
                ) : null}
              </div>
            ) : null}
          </section>
        </main>

        <aside className="sticky top-4 hidden h-fit w-40 shrink-0 space-y-1 self-start lg:block">
          {[
            ["basic", "基本信息"],
            ["datasource", "数据源"],
            ["source", "数据来源"],
            ["target", "数据去向"],
            ["mapping", "字段映射"],
            ["runtime", "运行参数"],
          ].map(([anchor, label]) => (
            <a
              key={anchor}
              href={`#${anchor}`}
              className="flex items-center gap-2 rounded-md px-2 py-2 text-xs text-[#667085] hover:bg-[#f2f4f7] hover:text-[var(--yak-color-primary)]"
            >
              <span className="size-1.5 rounded-full bg-current" />
              {label}
            </a>
          ))}
        </aside>
      </div>
    </div>
  );
}

export default DataSyncTaskEditorPage;
