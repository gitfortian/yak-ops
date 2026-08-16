import { history } from '@umijs/max';
import {
  Button,
  Empty,
  Input,
  InputNumber,
  Select,
  Spin,
  Table,
  Tag,
  message,
  type TableColumnsType,
} from 'antd';
import {
  Braces,
  ExternalLink,
  FileText,
  History,
  Network,
  Play,
  RefreshCw,
  Rocket,
  Save,
  Settings2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  getDevelopmentDataServiceNode,
  previewDevelopmentDataServiceNode,
  publishDevelopmentDataServiceNode,
  saveDevelopmentDataServiceDraft,
  type DataServiceContractType,
  type DevelopmentDataServiceDefinition,
  type DevelopmentDataServiceNodeContext,
  type DevelopmentDataServiceParameter,
  type DevelopmentDataServiceResponseField,
} from '../../data-service-node-service';
import {
  deployDataServiceRuntime,
  fetchDataServicePublicationState,
  syncDataServiceRuntime,
  type DataServicePublicationState,
} from '../../data-service-runtime-publication';
import type { DevelopmentId, DevelopmentResourceNode } from '../../types';
import SqlMonacoEditor from '../../editors/sql/components/SqlMonacoEditor';
import {
  hydrateSqlTaskConfig,
  selectSqlDataSourceContext,
} from '../../editors/sql/metadata/sqlMetadataContextStore';
import {
  listSqlDataSources,
  type SqlDataSourceOption,
} from '../../editors/sql/metadata/sqlMetadataService';

interface DataServiceNodeEditorProps {
  node: DevelopmentResourceNode;
  onSaved?: () => void | Promise<void>;
  /** Kept temporarily for Workbench compatibility; standalone Data Service no longer opens a source SQL. */
  onOpenSourceNode?: (nodeId: DevelopmentId) => void;
  onDirtyChange?: (dirty: boolean) => void;
}

type RightPanelKey = 'properties' | 'request' | 'response' | 'versions' | 'deployment';

const requestTypeOptions: { label: string; value: DataServiceContractType }[] = [
  { label: 'STRING', value: 'STRING' },
  { label: 'INTEGER', value: 'INTEGER' },
  { label: 'NUMBER', value: 'NUMBER' },
  { label: 'BOOLEAN', value: 'BOOLEAN' },
  { label: 'DATE', value: 'DATE' },
  { label: 'DATETIME', value: 'DATETIME' },
];

const responseTypeOptions: { label: string; value: DataServiceContractType }[] = [
  ...requestTypeOptions,
  { label: 'OBJECT', value: 'OBJECT' },
];

const formatTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '-';
const normalizeId = (value: unknown): DevelopmentId => {
  if (value === undefined || value === null || String(value).trim() === '') return '0';
  return String(value);
};
const safeArray = <T,>(value: T[] | null | undefined): T[] =>
  Array.isArray(value) ? value.filter(Boolean) : [];

const normalizeContext = (
  raw: DevelopmentDataServiceNodeContext,
  node: DevelopmentResourceNode,
): DevelopmentDataServiceNodeContext => {
  const rawDraft = raw?.draft;
  const rawDefinition = rawDraft?.definition;
  const nodeId = normalizeId(raw?.nodeId || node.id);
  const definition: DevelopmentDataServiceDefinition = {
    sourceTaskAssetId: rawDefinition?.sourceTaskAssetId,
    sourceTaskRevisionId: rawDefinition?.sourceTaskRevisionId,
    sourceTaskRevisionNo: Number(rawDefinition?.sourceTaskRevisionNo || 0),
    dataSourceId: normalizeId(rawDefinition?.dataSourceId),
    sql: rawDefinition?.sql || '',
    serviceName: rawDefinition?.serviceName || raw?.nodeName || node.name,
    path: rawDefinition?.path || `/query/${nodeId}`,
    method: 'GET',
    parameters: safeArray(rawDefinition?.parameters),
    responseFields: safeArray(rawDefinition?.responseFields),
    maxRows: Number(rawDefinition?.maxRows || 1000),
    timeoutSeconds: Number(rawDefinition?.timeoutSeconds || 30),
    description: rawDefinition?.description || undefined,
  };

  return {
    nodeId,
    nodeName: raw?.nodeName || node.name,
    configured: Boolean(raw?.configured),
    draft: {
      nodeId: normalizeId(rawDraft?.nodeId || nodeId),
      definition,
      draftRevision: Number(rawDraft?.draftRevision || 0),
      createTime: rawDraft?.createTime,
      updateTime: rawDraft?.updateTime,
    },
    latestPublishedRevision: raw?.latestPublishedRevision || null,
    revisions: safeArray(raw?.revisions),
  };
};

const panelItems: Array<{ key: RightPanelKey; label: string; icon: typeof Settings2 }> = [
  { key: 'properties', label: '属性', icon: Settings2 },
  { key: 'request', label: '请求参数', icon: Braces },
  { key: 'response', label: '返回参数', icon: FileText },
  { key: 'versions', label: '版本', icon: History },
  { key: 'deployment', label: '部署', icon: Rocket },
];

export default function DataServiceNodeEditor({
  node,
  onSaved,
  onDirtyChange,
}: DataServiceNodeEditorProps) {
  const dirtyChangeRef = useRef(onDirtyChange);
  useEffect(() => { dirtyChangeRef.current = onDirtyChange; }, [onDirtyChange]);

  const [context, setContext] = useState<DevelopmentDataServiceNodeContext>();
  const [dataSourceId, setDataSourceId] = useState<DevelopmentId>();
  const [dataSources, setDataSources] = useState<SqlDataSourceOption[]>([]);
  const [dataSourceLoading, setDataSourceLoading] = useState(false);
  const [sqlText, setSqlText] = useState('');
  const [serviceName, setServiceName] = useState(node.name);
  const [path, setPath] = useState(`/query/${node.id}`);
  const [maxRows, setMaxRows] = useState(1000);
  const [timeoutSeconds, setTimeoutSeconds] = useState(30);
  const [description, setDescription] = useState('');
  const [parameters, setParameters] = useState<DevelopmentDataServiceParameter[]>([]);
  const [responseFields, setResponseFields] = useState<DevelopmentDataServiceResponseField[]>([]);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publicationState, setPublicationState] = useState<DataServicePublicationState>();
  const [publicationError, setPublicationError] = useState<string>();
  const [publicationLoading, setPublicationLoading] = useState(false);
  const [deploying, setDeploying] = useState(false);
  const [rightPanelKey, setRightPanelKey] = useState<RightPanelKey>('properties');
  const [rightPanelOpen, setRightPanelOpen] = useState(true);

  const setDirtyState = useCallback((next: boolean) => {
    setDirty(next);
    dirtyChangeRef.current?.(next);
  }, []);
  const markDirty = useCallback(() => setDirtyState(true), [setDirtyState]);

  useEffect(() => () => dirtyChangeRef.current?.(false), []);

  const applyContext = useCallback((raw: DevelopmentDataServiceNodeContext) => {
    const next = normalizeContext(raw, node);
    const definition = next.draft.definition;
    const nextDataSourceId = definition.dataSourceId !== '0' ? definition.dataSourceId : undefined;
    setContext(next);
    setDataSourceId(nextDataSourceId);
    setSqlText(definition.sql || '');
    setServiceName(definition.serviceName || next.nodeName);
    setPath(definition.path || `/query/${next.nodeId}`);
    setMaxRows(definition.maxRows || 1000);
    setTimeoutSeconds(definition.timeoutSeconds || 30);
    setDescription(definition.description || '');
    setParameters(safeArray(definition.parameters));
    setResponseFields(safeArray(definition.responseFields));
    hydrateSqlTaskConfig(
      node.id,
      JSON.stringify(nextDataSourceId ? { dataSourceId: nextDataSourceId } : {}),
    );
    setDirtyState(false);
    return next;
  }, [node, setDirtyState]);

  const loadPublicationState = useCallback(async (
    hasPublishedRevision: boolean,
    notifyOnError = false,
  ) => {
    if (!hasPublishedRevision) {
      setPublicationState(undefined);
      setPublicationError(undefined);
      return;
    }
    setPublicationLoading(true);
    setPublicationError(undefined);
    try {
      setPublicationState(await fetchDataServicePublicationState(node.id));
    } catch (error) {
      const text = error instanceof Error ? error.message : '查询 Runtime 同步状态失败';
      setPublicationState(undefined);
      setPublicationError(text);
      if (notifyOnError) message.error(text);
    } finally {
      setPublicationLoading(false);
    }
  }, [node.id]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    setPublicationState(undefined);
    setPublicationError(undefined);
    try {
      const next = applyContext(await getDevelopmentDataServiceNode(node.id));
      await loadPublicationState(Boolean(next.latestPublishedRevision));
    } catch (error) {
      const text = error instanceof Error ? error.message : '加载 Data Service Node 失败';
      setLoadError(text);
      setContext(undefined);
      message.error(text);
    } finally {
      setLoading(false);
    }
  }, [applyContext, loadPublicationState, node.id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    setDataSourceLoading(true);
    listSqlDataSources()
      .then((values) => {
        if (active) setDataSources(values || []);
      })
      .catch((error) => {
        if (active) message.error(error instanceof Error ? error.message : '查询数据源失败');
      })
      .finally(() => {
        if (active) setDataSourceLoading(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!dataSourceId || !dataSources.length) return;
    const selected = dataSources.find((item) => item.value === dataSourceId);
    if (!selected) return;
    selectSqlDataSourceContext(node.id, {
      id: selected.value,
      name: selected.label,
      dbType: selected.dbType,
    });
  }, [dataSourceId, dataSources, node.id]);

  const dataSourceOptions = useMemo(() => dataSources.map((item) => ({
    value: item.value,
    label: `${item.label}${item.dbType ? ` · ${item.dbType}` : ''}`,
  })), [dataSources]);

  const activeDataSource = useMemo(
    () => dataSources.find((item) => item.value === dataSourceId),
    [dataSourceId, dataSources],
  );

  const invalidateContract = useCallback(() => {
    setParameters([]);
    setResponseFields([]);
  }, []);

  const changeDataSource = (nextDataSourceId: DevelopmentId) => {
    const selected = dataSources.find((item) => item.value === nextDataSourceId);
    setDataSourceId(nextDataSourceId);
    selectSqlDataSourceContext(node.id, selected ? {
      id: selected.value,
      name: selected.label,
      dbType: selected.dbType,
    } : { id: nextDataSourceId });
    invalidateContract();
    markDirty();
  };

  const changeSql = (value: string) => {
    setSqlText(value);
    invalidateContract();
    markDirty();
  };

  const preview = async () => {
    if (!dataSourceId || !sqlText.trim() || previewing) return;
    setPreviewing(true);
    try {
      const result = await previewDevelopmentDataServiceNode(
        node.id,
        dataSourceId,
        sqlText,
        timeoutSeconds,
      );
      const nextParameters = safeArray(result?.parameters);
      const nextResponses = safeArray(result?.responseFields);
      const oldParameters = new Map(
        parameters.filter((item) => item?.name).map((item) => [item.name.toLowerCase(), item]),
      );
      const oldResponses = new Map(
        responseFields.filter((item) => item?.name).map((item) => [item.name.toLowerCase(), item]),
      );

      setParameters(nextParameters.map((item) => {
        const previous = oldParameters.get(item.name.toLowerCase());
        return previous ? {
          ...item,
          type: previous.type === 'OBJECT' ? 'STRING' : previous.type,
          required: true,
          description: previous.description,
          example: previous.example,
        } : { ...item, required: true };
      }));
      setResponseFields(nextResponses.map((item) => {
        const previous = oldResponses.get(item.name.toLowerCase());
        return previous ? {
          ...item,
          type: previous.type,
          description: previous.description,
          example: previous.example,
        } : item;
      }));
      if (result?.dataSourceId) setDataSourceId(normalizeId(result.dataSourceId));
      markDirty();
      message.success(
        `Contract 已发现 · ${nextParameters.length} 个参数 / ${nextResponses.length} 个响应字段`,
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '预览 Data Service Contract 失败');
    } finally {
      setPreviewing(false);
    }
  };

  const save = async () => {
    if (!dataSourceId || !sqlText.trim() || saving) return;
    setSaving(true);
    try {
      const next = await saveDevelopmentDataServiceDraft(node.id, {
        dataSourceId,
        sql: sqlText,
        serviceName: serviceName.trim(),
        path: path.trim(),
        method: 'GET',
        parameters: parameters.map((item) => ({
          ...item,
          required: true,
          description: item.description?.trim() || undefined,
          example: item.example?.trim() || undefined,
        })),
        responseFields: responseFields.map((item) => ({
          ...item,
          description: item.description?.trim() || undefined,
          example: item.example?.trim() || undefined,
        })),
        maxRows,
        timeoutSeconds,
        description: description.trim() || undefined,
        baseRevision: context?.draft?.draftRevision || 0,
      });
      applyContext(next);
      await onSaved?.();
      message.success(`草稿已保存 · Draft #${next?.draft?.draftRevision || '-'}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Data Service Node 草稿失败');
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    const draftRevision = context?.draft?.draftRevision || 0;
    if (!draftRevision || publishing) return;
    if (dirty) {
      message.warning('当前有未保存修改，请先保存草稿再发布版本');
      return;
    }
    setPublishing(true);
    try {
      const revision = await publishDevelopmentDataServiceNode(node.id, draftRevision);
      await load();
      await onSaved?.();
      message.success(`已发布 DS R${revision.revisionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 Data Service Node 失败');
    } finally {
      setPublishing(false);
    }
  };

  const deployOrSync = async () => {
    const latestPublished = context?.latestPublishedRevision;
    if (!latestPublished || deploying) return;
    setDeploying(true);
    try {
      if (publicationState?.published) {
        const apiId = publicationState.detail?.id;
        if (!apiId) throw new Error('Runtime API 身份缺失，请刷新同步状态后重试');
        await syncDataServiceRuntime(apiId);
        message.success(`Runtime 已同步到 DS R${latestPublished.revisionNo}`);
      } else {
        await deployDataServiceRuntime(node.id);
        message.success(`Runtime 已部署 · DS R${latestPublished.revisionNo} · 默认停用`);
      }
      await loadPublicationState(true, true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '部署 Data Service Runtime 失败');
    } finally {
      setDeploying(false);
    }
  };

  const updateParameter = (
    index: number,
    patch: Partial<DevelopmentDataServiceParameter>,
  ) => {
    setParameters((current) => current.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch, required: true } : item,
    ));
    markDirty();
  };

  const updateResponseField = (
    index: number,
    patch: Partial<DevelopmentDataServiceResponseField>,
  ) => {
    setResponseFields((current) => current.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch } : item,
    ));
    markDirty();
  };

  const parameterColumns = useMemo<TableColumnsType<DevelopmentDataServiceParameter>>(() => [
    {
      title: '参数名称',
      dataIndex: 'name',
      width: 130,
      render: (value: string) => <span className="font-mono text-[11px] text-[#344054]">{value}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 112,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value === 'OBJECT' ? 'STRING' : value}
          options={requestTypeOptions}
          className="w-[100px]"
          onChange={(next) => updateParameter(index, { type: next })}
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 58,
      render: () => <Tag bordered={false}>是</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 170,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="参数说明"
          onChange={(event) => updateParameter(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 140,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="示例值"
          onChange={(event) => updateParameter(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const responseColumns = useMemo<TableColumnsType<DevelopmentDataServiceResponseField>>(() => [
    {
      title: '字段',
      dataIndex: 'name',
      width: 130,
      render: (value: string) => <span className="font-mono text-[11px] text-[#344054]">{value}</span>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 112,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value}
          options={responseTypeOptions}
          className="w-[100px]"
          onChange={(next) => updateResponseField(index, { type: next })}
        />
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 58,
      render: (value: boolean) => <Tag bordered={false}>{value ? '是' : '否'}</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      width: 170,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="字段说明"
          onChange={(event) => updateResponseField(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 140,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          placeholder="示例值"
          onChange={(event) => updateResponseField(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const latestPublished = context?.latestPublishedRevision;
  const runtimeRevisionNo = publicationState?.detail?.sourceRevisionNo;
  const runtimeStatus = !latestPublished
    ? '等待发布 DS Revision'
    : publicationLoading
      ? '正在查询 Runtime'
      : publicationError
        ? 'Runtime 状态不可用'
        : !publicationState?.published
          ? '尚未部署'
          : publicationState.updateAvailable
            ? `Runtime DS R${runtimeRevisionNo || '-'} · 待同步`
            : publicationState.detail?.enabled
              ? `Runtime DS R${runtimeRevisionNo || '-'} · 运行中`
              : `Runtime DS R${runtimeRevisionNo || '-'} · 已停用`;

  const propertiesPanel = (
    <div className="space-y-5">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">API 属性</div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          Data Service Node 独立拥有数据源、查询 SQL 和 API Contract，发布时一起冻结到 DS Revision。
        </div>
      </div>

      <div className="space-y-4">
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#667085]">API 名称</div>
          <Input
            size="small"
            value={serviceName}
            maxLength={200}
            onChange={(event) => { setServiceName(event.target.value); markDirty(); }}
          />
        </div>
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#667085]">API Path</div>
          <Input
            size="small"
            addonBefore="GET"
            value={path}
            maxLength={255}
            onChange={(event) => { setPath(event.target.value); markDirty(); }}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <div className="mb-1.5 text-[11px] font-medium text-[#667085]">最大返回行数</div>
            <InputNumber
              size="small"
              min={1}
              max={10000}
              value={maxRows}
              className="w-full"
              onChange={(value) => { setMaxRows(Number(value || 1000)); markDirty(); }}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[11px] font-medium text-[#667085]">超时时间（秒）</div>
            <InputNumber
              size="small"
              min={1}
              max={3600}
              value={timeoutSeconds}
              className="w-full"
              onChange={(value) => { setTimeoutSeconds(Number(value || 30)); markDirty(); }}
            />
          </div>
        </div>
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#667085]">描述</div>
          <Input.TextArea
            rows={4}
            maxLength={2000}
            value={description}
            placeholder="说明这个数据服务提供什么能力"
            onChange={(event) => { setDescription(event.target.value); markDirty(); }}
          />
        </div>
      </div>

      <div className="border-t border-[#eef0f2] pt-4 text-[11px] leading-6 text-[#667085]">
        <div className="flex justify-between"><span>Draft</span><span>#{context?.draft?.draftRevision || 0}</span></div>
        <div className="flex justify-between"><span>最新 DS Revision</span><span>{latestPublished ? `R${latestPublished.revisionNo}` : '尚未发布'}</span></div>
        <div className="flex justify-between gap-3"><span>数据源</span><span className="truncate text-right">{activeDataSource?.label || (dataSourceId ? `#${dataSourceId}` : '-')}</span></div>
        <div className="flex justify-between"><span>查询 SQL</span><span>{sqlText.trim() ? '已配置' : '未填写'}</span></div>
      </div>
    </div>
  );

  const requestPanel = (
    <div className="space-y-3">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">请求参数</div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          参数来自当前 SQL 中的 <span className="font-mono">:name</span> 命名参数。v1 统一为必填参数。
        </div>
      </div>
      <Table<DevelopmentDataServiceParameter>
        rowKey="name"
        size="small"
        pagination={false}
        dataSource={parameters}
        columns={parameterColumns}
        scroll={{ x: 610 }}
        locale={{ emptyText: '预览 Contract 后自动发现请求参数' }}
      />
    </div>
  );

  const responsePanel = (
    <div className="space-y-3">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">返回参数</div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          通过当前数据源真实预览发现字段类型，可补充描述与示例后固化到 DS Revision。
        </div>
      </div>
      <Table<DevelopmentDataServiceResponseField>
        rowKey={(record) => record.name}
        size="small"
        pagination={false}
        dataSource={responseFields}
        columns={responseColumns}
        scroll={{ x: 610 }}
        locale={{ emptyText: '预览 Contract 后自动发现返回字段' }}
      />
    </div>
  );

  const versionsPanel = (
    <div>
      <div className="text-[13px] font-semibold text-[#344054]">版本</div>
      <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
        每个 DS Revision 都冻结当时的数据源、SQL 和 Contract；发布不会自动更新 Runtime。
      </div>
      <div className="mt-4 space-y-2">
        {context?.revisions?.length ? context.revisions.map((revision) => (
          <div key={revision.id} className="border border-[#e5e7eb] px-3 py-2.5">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="text-[12px] font-semibold text-[#344054]">DS R{revision.revisionNo}</span>
                {latestPublished?.id === revision.id ? <Tag bordered={false}>最新</Tag> : null}
              </div>
              <span className="text-[10px] text-[#98a2b3]">{formatTime(revision.createTime)}</span>
            </div>
            <div className="mt-1 text-[11px] text-[#667085]">
              Draft #{revision.sourceDraftRevision}
            </div>
          </div>
        )) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未发布 DS Revision" />
        )}
      </div>
    </div>
  );

  const deploymentPanel = (
    <div className="space-y-4">
      <div>
        <div className="text-[13px] font-semibold text-[#344054]">Runtime 部署</div>
        <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
          发布与部署分离。先生成 DS Revision，再显式部署或同步到数据服务 Runtime。
        </div>
      </div>

      <div className="border border-[#e5e7eb] bg-[#fafafa] px-3 py-3 text-[11px] leading-6 text-[#667085]">
        <div className="flex justify-between"><span>最新发布</span><span>{latestPublished ? `DS R${latestPublished.revisionNo}` : '尚未发布'}</span></div>
        <div className="flex justify-between"><span>Runtime</span><span className="text-right text-[#344054]">{runtimeStatus}</span></div>
        {publicationState?.detail?.runtimePath ? (
          <div className="flex justify-between gap-3"><span>Endpoint</span><span className="truncate font-mono text-[#344054]">{publicationState.detail.runtimePath}</span></div>
        ) : null}
      </div>

      <Button
        block
        type="primary"
        icon={<Rocket size={14} />}
        disabled={!latestPublished}
        loading={deploying}
        onClick={() => void deployOrSync()}
      >
        {publicationState?.published ? '同步最新 Revision' : '部署 Runtime'}
      </Button>
      {publicationState?.published ? (
        <Button block icon={<ExternalLink size={14} />} onClick={() => history.push('/data-service')}>
          打开 API 服务
        </Button>
      ) : null}
      {publicationError ? (
        <div className="text-[11px] leading-5 text-[#b42318]">{publicationError}</div>
      ) : null}
    </div>
  );

  const panelContent: Record<RightPanelKey, React.ReactNode> = {
    properties: propertiesPanel,
    request: requestPanel,
    response: responsePanel,
    versions: versionsPanel,
    deployment: deploymentPanel,
  };

  const selectRightPanel = (key: RightPanelKey) => {
    if (rightPanelKey === key && rightPanelOpen) {
      setRightPanelOpen(false);
      return;
    }
    setRightPanelKey(key);
    setRightPanelOpen(true);
  };

  if (loading) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <Spin size="small" />
      </div>
    );
  }

  if (loadError || !context) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white px-6">
        <div className="max-w-[520px] text-center">
          <div className="text-[14px] font-semibold text-[#344054]">Data Service Node 加载失败</div>
          <div className="mt-2 text-[12px] leading-5 text-[#98a2b3]">{loadError || '未返回有效编辑上下文'}</div>
          <Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => void load()}>
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  const canPreview = Boolean(dataSourceId && sqlText.trim());
  const canSave = canPreview && dirty;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2.5">
        <div className="flex items-center gap-1">
          <Button type="text" size="small" icon={<RefreshCw size={14} />} onClick={() => void load()}>
            刷新
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Play size={14} />}
            loading={previewing}
            disabled={!canPreview}
            onClick={() => void preview()}
          >
            预览 Contract
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Save size={14} />}
            loading={saving}
            disabled={!canSave}
            onClick={() => void save()}
          >
            保存草稿
          </Button>
          <Button
            type="text"
            size="small"
            icon={<Rocket size={14} />}
            loading={publishing}
            disabled={!context.draft.draftRevision || dirty}
            onClick={() => void publish()}
          >
            发布版本
          </Button>
          {latestPublished ? (
            <Button
              type="text"
              size="small"
              icon={<Network size={14} />}
              loading={deploying}
              onClick={() => void deployOrSync()}
            >
              {publicationState?.published ? '同步 Runtime' : '部署 Runtime'}
            </Button>
          ) : null}
        </div>

        <div className="flex min-w-0 items-center gap-2 text-[10px] text-[#98a2b3]">
          <span>Draft #{context.draft.draftRevision || 0}</span>
          <span className="text-[#d0d5dd]">|</span>
          <span>{latestPublished ? `DS R${latestPublished.revisionNo}` : '尚未发布'}</span>
          <span className="text-[#d0d5dd]">|</span>
          <span className={publicationState?.updateAvailable ? 'text-[#b54708]' : ''}>{runtimeStatus}</span>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
          <div className="shrink-0 border-b border-[#eef0f2] px-4 py-3">
            <div className="flex items-center gap-3">
              <div className="w-[72px] shrink-0 text-[12px] font-medium text-[#344054]">数据源</div>
              <Select
                value={dataSourceId}
                options={dataSourceOptions}
                placeholder="选择数据源"
                className="min-w-0 flex-1"
                size="small"
                showSearch
                loading={dataSourceLoading}
                optionFilterProp="label"
                onChange={changeDataSource}
              />
            </div>
            <div className="ml-[84px] mt-1.5 text-[10px] text-[#98a2b3]">
              Data Service Node 独立持有数据源和 SQL，不再关联其他 SQL 节点。
            </div>
          </div>

          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#eef0f2] px-3">
            <div className="text-[12px] font-semibold text-[#344054]">查询 SQL</div>
            <div className="flex items-center gap-2 text-[10px] text-[#98a2b3]">
              {activeDataSource ? <span>{activeDataSource.label}</span> : dataSourceId ? <span>DataSource #{dataSourceId}</span> : null}
              <span>可编辑</span>
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-hidden bg-white">
            <SqlMonacoEditor
              id={`data-service-${node.id}`}
              value={sqlText}
              onChange={changeSql}
            />
          </div>

          <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
            <div className="flex min-w-0 items-center gap-3">
              <span className="font-medium text-[#667085]">DATA SERVICE</span>
              <span className="truncate">{node.name}</span>
              {dirty ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
                  未保存
                </span>
              ) : null}
            </div>
            <span>{responseFields.length ? `${responseFields.length} 个响应字段` : 'Contract 待预览'}</span>
          </div>
        </section>

        <aside className="flex shrink-0 border-l border-[#e8e9ec] bg-white">
          {rightPanelOpen ? (
            <div className="w-[380px] min-w-0 overflow-auto border-r border-[#eef0f2] p-4">
              {panelContent[rightPanelKey]}
            </div>
          ) : null}
          <div className="flex w-9 shrink-0 flex-col items-stretch bg-[#fafafa]">
            {panelItems.map((item) => {
              const Icon = item.icon;
              const active = rightPanelOpen && rightPanelKey === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  title={item.label}
                  onClick={() => selectRightPanel(item.key)}
                  className={[
                    'flex min-h-[84px] flex-col items-center justify-center gap-1.5 border-b border-[#eef0f2] text-[10px] transition-colors [writing-mode:vertical-rl]',
                    active
                      ? 'bg-white font-medium text-[#344054]'
                      : 'text-[#667085] hover:bg-white hover:text-[#344054]',
                  ].join(' ')}
                >
                  <Icon size={13} strokeWidth={1.8} className="[writing-mode:horizontal-tb]" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </aside>
      </div>
    </div>
  );
}
