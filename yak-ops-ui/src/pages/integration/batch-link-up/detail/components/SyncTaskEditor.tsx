import { Alert } from 'antd';
import type { DataSourceRecord } from '@/services/data-source';

import { isAutoCreateTableEnabledForDataSourceType } from '../../connectorProfiles';
import {
  resolveEndpointCapability,
  validateEditorCapabilities,
} from '../capabilities';
import useConnectorSchema from '../hooks/useConnectorSchema';
import useDataSourceColumns from '../hooks/useDataSourceColumns';
import useDataSourceTables from '../hooks/useDataSourceTables';
import useOfflineConnectorRuntime from '../hooks/useOfflineConnectorRuntime';
import { updateEndpointConfig, type SyncEditorState } from '../model';
import ChannelConfigSection from './ChannelConfigSection';
import ConnectorExtraParameters from './ConnectorExtraParameters';
import FieldMappingSection, { type FieldMappingValue } from './FieldMappingSection';
import MultiTableConfigSection from './MultiTableConfigSection';
import NotificationConfigSection from './NotificationConfigSection';
import ScheduleConfigSection from './ScheduleConfigSection';
import SingleTableConfigSection from './SingleTableConfigSection';
import TaskBasicSection from './TaskBasicSection';

interface SyncTaskEditorProps {
  editor: SyncEditorState;
  dataSources: DataSourceRecord[];
  dataSourceLoading: boolean;
  onChange: (value: SyncEditorState) => void;
}

const normalizeMappings = (value: unknown): FieldMappingValue[] => {
  if (!Array.isArray(value)) return [];
  return value
    .map((item: any) => ({
      source: String(item?.source ?? item?.sourceField ?? '').trim(),
      target: String(item?.target ?? item?.targetField ?? '').trim(),
    }))
    .filter((item) => item.source && item.target);
};

export default function SyncTaskEditor({
  editor,
  dataSources,
  dataSourceLoading,
  onChange,
}: SyncTaskEditorProps) {
  const sourceConfig = editor.source.config || {};
  const sinkConfig = editor.sink.config || {};
  const sourceId = editor.source.dataSourceId;
  const targetId = editor.sink.dataSourceId;
  const mappingColumns = normalizeMappings(editor.mapping?.columns);
  const sinkAutoCreateTableEnabled =
    isAutoCreateTableEnabledForDataSourceType(editor.sink.dbType);
  const sinkAutoCreateTable =
    sinkAutoCreateTableEnabled && Boolean(sinkConfig.autoCreateTable);

  const connectorRuntime = useOfflineConnectorRuntime();
  const sourceCapability = resolveEndpointCapability(
    connectorRuntime.snapshot,
    editor.source.connectorId,
    'SOURCE',
  );
  const sinkCapability = resolveEndpointCapability(
    connectorRuntime.snapshot,
    editor.sink.connectorId,
    'SINK',
  );
  const capabilityErrors = validateEditorCapabilities(
    editor,
    connectorRuntime.snapshot,
  );

  const sourceSchema = useConnectorSchema(
    editor.source.connectorId,
    'SOURCE',
    sourceCapability.available,
  );
  const sinkSchema = useConnectorSchema(
    editor.sink.connectorId,
    'SINK',
    sinkCapability.available,
  );

  const sourceCatalog = useDataSourceTables(sourceId);
  const targetCatalog = useDataSourceTables(targetId);
  const sourceColumnRequest = sourceConfig.readMode === 'sql'
    ? sourceConfig.sql?.trim() ? { query: sourceConfig.sql } : undefined
    : sourceConfig.table ? { table_path: sourceConfig.table } : undefined;
  const targetColumnRequest = !sinkAutoCreateTable && sinkConfig.table
    ? { table_path: sinkConfig.table }
    : undefined;
  const sourceColumnCatalog = useDataSourceColumns(sourceId, sourceColumnRequest);
  const targetColumnCatalog = useDataSourceColumns(targetId, targetColumnRequest);
  const primaryKeyCatalog = sinkAutoCreateTable
    ? sourceColumnCatalog
    : targetColumnCatalog;
  const mappingTargetColumns = sinkAutoCreateTable
    ? sourceColumnCatalog.columns
    : targetColumnCatalog.columns;
  const mappingTargetLoading = sinkAutoCreateTable
    ? sourceColumnCatalog.loading
    : targetColumnCatalog.loading;

  const updateSource = (patch: Record<string, any>) =>
    onChange(updateEndpointConfig(editor, 'source', patch));
  const updateSink = (patch: Record<string, any>) =>
    onChange(updateEndpointConfig(editor, 'sink', patch));
  const updateMapping = (columns: FieldMappingValue[]) =>
    onChange({ ...editor, mapping: { columns } });

  const runtimeNotice = (() => {
    if (connectorRuntime.loading && !connectorRuntime.snapshot) {
      return (
        <Alert
          type="info"
          showIcon
          message="正在读取 Link-Up Connector 能力"
          description="能力确认完成前保留兼容表单，不会提前隐藏现有配置。"
        />
      );
    }

    if (connectorRuntime.error && !connectorRuntime.snapshot) {
      return (
        <Alert
          type="warning"
          showIcon
          message="暂时无法读取 Link-Up Connector 能力"
          description={`${connectorRuntime.error}。编辑器将保留兼容字段，保存时会再次校验。`}
        />
      );
    }

    if (connectorRuntime.snapshot && !connectorRuntime.snapshot.reachable) {
      return (
        <Alert
          type="warning"
          showIcon
          message="Link-Up Worker 当前不可达"
          description={
            connectorRuntime.snapshot.errorMessage ||
            '如果存在上次同步的 Schema，编辑器仅将其作为表单参考，不会把它视为当前可执行状态。'
          }
        />
      );
    }

    if (capabilityErrors.length > 0) {
      return (
        <Alert
          type="error"
          showIcon
          message="当前 Connector 能力与任务配置不匹配"
          description={capabilityErrors.join('；')}
        />
      );
    }

    return null;
  })();

  const sourceExtraParameters = (
    <ConnectorExtraParameters
      role="SOURCE"
      schema={sourceSchema.schema}
      loading={sourceSchema.loading}
      error={sourceSchema.error}
      config={sourceConfig}
      onChange={updateSource}
    />
  );
  const sinkExtraParameters = (
    <ConnectorExtraParameters
      role="SINK"
      schema={sinkSchema.schema}
      loading={sinkSchema.loading}
      error={sinkSchema.error}
      config={sinkConfig}
      onChange={updateSink}
    />
  );

  return (
    <div className="space-y-5">
      {runtimeNotice}

      <div id="task-basic" className="scroll-mt-6">
        <TaskBasicSection
          editor={editor}
          dataSources={dataSources}
          dataSourceLoading={dataSourceLoading}
          onChange={onChange}
        />
      </div>

      <div id="sync-config" className="scroll-mt-6">
        {editor.mode === 'GUIDE_MULTI' ? (
          <MultiTableConfigSection
            sourceConfig={sourceConfig}
            sinkConfig={sinkConfig}
            sinkCapability={sinkCapability}
            autoCreateTableEnabled={sinkAutoCreateTableEnabled}
            sourceTables={sourceCatalog.tables}
            sourceLoading={sourceCatalog.loading}
            sourceReady={Boolean(sourceId)}
            targetReady={Boolean(targetId)}
            sourceExtraParameters={sourceExtraParameters}
            sinkExtraParameters={sinkExtraParameters}
            onSourceTableSearch={sourceCatalog.search}
            onSourceChange={updateSource}
            onSinkChange={updateSink}
          />
        ) : (
          <SingleTableConfigSection
            sourceDataSourceId={sourceId}
            sourceConfig={sourceConfig}
            sinkConfig={sinkConfig}
            sourceCapability={sourceCapability}
            sinkCapability={sinkCapability}
            autoCreateTableEnabled={sinkAutoCreateTableEnabled}
            sourceTables={sourceCatalog.tables}
            targetTables={targetCatalog.tables}
            sourceLoading={sourceCatalog.loading}
            targetLoading={targetCatalog.loading}
            primaryKeyOptions={primaryKeyCatalog.columns}
            primaryKeyLoading={primaryKeyCatalog.loading}
            sourceReady={Boolean(sourceId)}
            targetReady={Boolean(targetId)}
            sourceExtraParameters={sourceExtraParameters}
            sinkExtraParameters={sinkExtraParameters}
            onSourceTableSearch={sourceCatalog.search}
            onTargetTableSearch={targetCatalog.search}
            onSourceChange={updateSource}
            onSinkChange={updateSink}
          />
        )}
      </div>

      <div id="runtime-params" className="scroll-mt-6">
        <ChannelConfigSection
          editor={editor}
          sinkConfig={sinkConfig}
          sinkCapability={sinkCapability}
          onChange={onChange}
          onSinkChange={updateSink}
        />
      </div>

      <div id="schedule-config" className="scroll-mt-6">
        <ScheduleConfigSection editor={editor} onChange={onChange} />
      </div>

      <div id="notification-config" className="scroll-mt-6">
        <NotificationConfigSection editor={editor} onChange={onChange} />
      </div>

      {editor.mode === 'GUIDE_SINGLE' ? (
        <div id="field-mapping" className="scroll-mt-6">
          <FieldMappingSection
            value={mappingColumns}
            onChange={updateMapping}
            sourceColumns={sourceColumnCatalog.columns}
            targetColumns={mappingTargetColumns}
            sourceLoading={sourceColumnCatalog.loading}
            targetLoading={mappingTargetLoading}
            sourceReady={Boolean(sourceId && sourceColumnRequest)}
            targetReady={Boolean(targetId && (sinkAutoCreateTable || targetColumnRequest))}
            targetDerived={sinkAutoCreateTable}
          />
        </div>
      ) : null}
    </div>
  );
}
