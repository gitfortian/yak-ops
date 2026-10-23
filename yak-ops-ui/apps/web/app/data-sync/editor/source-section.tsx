import { Alert, CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction } from "react";
import { ExplicitTableSelection } from "../table-mapping-editor";
import { IncrementalFields } from "../incremental-fields";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm } from "./types";

interface Props {
  batch: boolean; prepareSchema: boolean; form: EditorForm;
  sourceCatalog: CatalogOptions; targetCatalog: CatalogOptions;
  selectedSourceDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[]; updateMappings: (value: DataSyncTableMapping[]) => void;
  writeMode?: DataSyncWriteMode;
  usesChangelog: boolean; useIncremental: boolean; initialLoad: boolean;
  patch: <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => void;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setForm: Dispatch<SetStateAction<EditorForm>>;
}
export function SourceSection({ batch, prepareSchema, form, sourceCatalog, targetCatalog, selectedSourceDataSource, mappings, updateMappings, writeMode, usesChangelog, useIncremental, initialLoad, patch, setTaskDirty, setParametersDirty, setForm }: Props) {
  const combination = { writeModes: writeMode ? [writeMode] : [] };
  return (
          <CollapseSection id="source" title="数据来源">
            {batch ? (
              <ExplicitTableSelection
                prepareSchema={prepareSchema}
                targetNamespace={{
                  database: form.targetDatabase || undefined,
                  schema: form.targetSchema || undefined,
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
                dataSourceId={form.sourceDataSourceId}
                boundSchema={selectedSourceDataSource?.schema}
                database={form.sourceDatabase}
                schema={form.sourceSchema}
                table={form.sourceTable}
                catalog={sourceCatalog}
                onSchemaChange={(value) => {
                  setTaskDirty(true);
                  setParametersDirty(true);
                  setForm((current) => ({
                    ...current,
                    sourceSchema: value,
                    sourceTable: "",
                  }));
                }}
                onTableChange={(table) => {
                  setTaskDirty(true);
                  setParametersDirty(true);
                  setForm((current) => ({
                    ...current,
                    sourceDatabase: current.sourceDatabase || table.database || "",
                    sourceSchema: current.sourceSchema || table.schema || "",
                    sourceTable: table.name,
                  }));
                }}
              >
                {usesChangelog ? (
                  <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
                ) : null}
                {useIncremental ? (
                  <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
                    <FieldLabel required>增量设置</FieldLabel>
                    <div className="w-1/2 min-w-0 max-lg:w-full">
                      <IncrementalFields
                        value={form.incremental}
                        initialLoad={initialLoad}
                        onChange={(value) => patch("incremental", value)}
                      />
                    </div>
                  </Field>
                ) : null}
              </TableSection>
            )}
          </CollapseSection>
  );
}
