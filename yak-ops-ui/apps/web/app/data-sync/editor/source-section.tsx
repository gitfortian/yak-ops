import { Alert, CollapseSection, Field, FieldLabel, Switch } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DataSourceEndpointCard } from "./data-source-endpoint-card";
import { listDataSourceColumns, type DataSourceCatalogColumn } from "@/service/datasource";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ExplicitTableSelection } from "./table-mapping-editor";
import { IncrementalFields } from "./incremental-fields";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm, EditorSource } from "./types";

interface Props {
  id?: string;
  sourceType: string;
  sourceDataSources: DataSourceRecord[];
  dataSourcesLoading: boolean;
  loadDataSources: () => Promise<void>;
  onConnectionChange: (value: string) => void;
  batch: boolean;
  prepareSchema: boolean;
  form: EditorForm;
  sourceCatalog: CatalogOptions;
  targetCatalog: CatalogOptions;
  selectedSourceDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeMode?: DataSyncWriteMode;
  allowKeyless: boolean;
  usesChangelog: boolean;
  useIncremental: boolean;
  initialLoad: boolean;
  patchSource: <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => void;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setForm: Dispatch<SetStateAction<EditorForm>>;
}
export function SourceSection({
  id,
  sourceType,
  sourceDataSources,
  dataSourcesLoading,
  loadDataSources,
  onConnectionChange,
  batch,
  prepareSchema,
  form,
  sourceCatalog,
  targetCatalog,
  selectedSourceDataSource,
  mappings,
  updateMappings,
  writeMode,
  allowKeyless,
  usesChangelog,
  useIncremental,
  initialLoad,
  patchSource,
  setTaskDirty,
  setParametersDirty,
  setForm,
}: Props) {
  const navigate = useNavigate();
  const combination = { writeModes: writeMode ? [writeMode] : [] };
  const [sourceColumns, setSourceColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [columnsError, setColumnsError] = useState(false);
  useEffect(() => {
    if (!allowKeyless || !form.source.dataSourceId || !form.source.table) {
      setSourceColumns([]);
      return;
    }
    let active = true;
    setSourceColumns([]);
    setColumnsError(false);
    void listDataSourceColumns(form.source.dataSourceId, {
      database: form.source.database || undefined,
      schema: form.source.schema || undefined,
      table: form.source.table,
    })
      .then((columns) => {
        if (active) setSourceColumns(columns);
      })
      .catch(() => {
        if (active) setColumnsError(true);
      });
    return () => {
      active = false;
    };
  }, [
    allowKeyless,
    form.source.dataSourceId,
    form.source.database,
    form.source.schema,
    form.source.table,
  ]);
  const chunkColumns = sourceColumns.filter(
    (column) =>
      column.nullable === false &&
      /^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(column.typeName || ""),
  );
  return (
    <CollapseSection id="source" title="数据来源 Source">
      <div className="mb-4">
        <DataSourceEndpointCard
          title="来源数据源"
          dataSourceType={sourceType}
          dataSources={sourceDataSources}
          dataSourceId={form.source.dataSourceId}
          refreshing={dataSourcesLoading}
          onRefresh={loadDataSources}
          onCreateDataSource={() =>
            navigate(
              "/data-source?create=1&returnTo=" +
                encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
            )
          }
          onDataSourceChange={onConnectionChange}
        />
      </div>
      {batch ? (
        <ExplicitTableSelection
          prepareSchema={prepareSchema}
          targetNamespace={{
            database: form.sink.database || undefined,
            schema: form.sink.schema || undefined,
          }}
          writeMode={combination?.writeModes[0]}
          sourceTables={sourceCatalog.tables}
          targetTables={targetCatalog.tables}
          mappings={mappings}
          loading={sourceCatalog.loading}
          error={sourceCatalog.error}
          refresh={sourceCatalog.refresh}
          onChange={updateMappings}
        />
      ) : (
        <TableSection
          dataSourceId={form.source.dataSourceId}
          boundSchema={selectedSourceDataSource?.schema}
          database={form.source.database}
          schema={form.source.schema}
          table={form.source.table}
          catalog={sourceCatalog}
          onSchemaChange={(value) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              source: {
                ...current.source,
                schema: value,
                table: "",
                keylessEnabled: false,
                chunkKeyColumn: "",
              },
              sink: {
                ...current.sink,
                targetPrimaryKeys: [],
                writeMode: usesChangelog ? "UPSERT" : current.sink.writeMode,
              },
            }));
          }}
          onTableChange={(table) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              source: {
                ...current.source,
                database: current.source.database || table.database || "",
                schema: current.source.schema || table.schema || "",
                table: table.name,
                keylessEnabled: false,
                chunkKeyColumn: "",
              },
              sink: {
                ...current.sink,
                targetPrimaryKeys: [],
                writeMode: usesChangelog ? "UPSERT" : current.sink.writeMode,
              },
            }));
          }}
        >
          {usesChangelog ? (
            <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
          ) : null}
          {allowKeyless ? (
            <>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>无主键表</FieldLabel>
                <Switch
                  aria-label="无主键表"
                  size="small"
                  checked={form.source.keylessEnabled}
                  onCheckedChange={(enabled) => {
                    setTaskDirty(true);
                    setParametersDirty(true);
                    setForm((current) => ({
                      ...current,
                      source: {
                        ...current.source,
                        keylessEnabled: enabled,
                        chunkKeyColumn: enabled ? current.source.chunkKeyColumn : "",
                      },
                      sink: {
                        ...current.sink,
                        targetPrimaryKeys: [],
                        writeMode: enabled ? "RETRACT" : "UPSERT",
                        schemaChangesEnabled: enabled ? false : current.sink.schemaChangesEnabled,
                        allowDropColumns: enabled ? false : current.sink.allowDropColumns,
                      },
                    }));
                  }}
                />
              </Field>
              {form.source.keylessEnabled ? (
                <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel required>快照分片字段</FieldLabel>
                  <div className="w-1/2 min-w-0 max-lg:w-full">
                    <DataSyncSearchableSelect
                      placeholder="选择非空数值字段"
                      value={form.source.chunkKeyColumn || null}
                      options={chunkColumns.map((column) => ({
                        value: column.name,
                        label: column.name,
                      }))}
                      onValueChange={(value) => patchSource("chunkKeyColumn", value)}
                    />
                  </div>
                </Field>
              ) : null}
              {columnsError && form.source.keylessEnabled ? <Alert>来源字段加载失败</Alert> : null}
            </>
          ) : null}
          {useIncremental ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
              <FieldLabel required>增量设置</FieldLabel>
              <div className="w-1/2 min-w-0 max-lg:w-full">
                <IncrementalFields
                  value={form.source.incremental}
                  initialLoad={initialLoad}
                  onChange={(value) => patchSource("incremental", value)}
                />
              </div>
            </Field>
          ) : null}
        </TableSection>
      )}
    </CollapseSection>
  );
}
