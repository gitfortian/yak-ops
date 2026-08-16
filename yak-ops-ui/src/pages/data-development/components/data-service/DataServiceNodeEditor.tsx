import { history } from '@umijs/max';
import {
  Button,
  Input,
  InputNumber,
  Select,
  Spin,
  Switch,
  Table,
  Tag,
  message,
  type TableColumnsType,
} from 'antd';
import { ExternalLink, Network, RefreshCw, Rocket, Save } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

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
  type DevelopmentDataServiceRevisionSummary,
  type DevelopmentDataServiceSource,
} from '../../data-service-node-service';
import {
  deployDataServiceRuntime,
  fetchDataServicePublicationState,
  syncDataServiceRuntime,
  type DataServicePublicationState,
} from '../../data-service-runtime-publication';
import type { DevelopmentId, DevelopmentResourceNode } from '../../types';

interface DataServiceNodeEditorProps {
  node: DevelopmentResourceNode;
  onSaved?: () => void | Promise<void>;
}

const contractTypeOptions: { label: string; value: DataServiceContractType }[] = [
  { label: 'STRING', value: 'STRING' },
  { label: 'INTEGER', value: 'INTEGER' },
  { label: 'NUMBER', value: 'NUMBER' },
  { label: 'BOOLEAN', value: 'BOOLEAN' },
  { label: 'DATE', value: 'DATE' },
  { label: 'DATETIME', value: 'DATETIME' },
  { label: 'OBJECT', value: 'OBJECT' },
];

const formatTime = (value?: string | null) => value ? value.replace('T', ' ').slice(0, 19) : '-';
const normalizeId = (value: unknown): DevelopmentId => {
  if (value === undefined || value === null || String(value).trim() === '') return '0';
  return String(value);
};
const safeArray = <T,>(value: T[] | null | undefined): T[] =>
  Array.isArray(value) ? value.filter(Boolean) : [];

/**
 * Data Service Node is a resource editor, so one malformed/older API payload must never crash the
 * whole Data Development workspace. Keep a complete local authoring shape even while wire contracts
 * evolve across releases.
 */
const normalizeContext = (
  raw: DevelopmentDataServiceNodeContext,
  node: DevelopmentResourceNode,
): DevelopmentDataServiceNodeContext => {
  const rawDraft = raw?.draft;
  const rawDefinition = rawDraft?.definition;
  const nodeId = normalizeId(raw?.nodeId || node.id);
  const definition: DevelopmentDataServiceDefinition = {
    sourceTaskAssetId: normalizeId(rawDefinition?.sourceTaskAssetId),
    sourceTaskRevisionId: normalizeId(rawDefinition?.sourceTaskRevisionId),
    sourceTaskRevisionNo: Number(rawDefinition?.sourceTaskRevisionNo || 0),
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
    availableSources: safeArray(raw?.availableSources),
    selectedSource: raw?.selectedSource || null,
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

export default function DataServiceNodeEditor({
  node,
  onSaved,
}: DataServiceNodeEditorProps) {
  const [context, setContext] = useState<DevelopmentDataServiceNodeContext>();
  const [selectedSourceId, setSelectedSourceId] = useState<DevelopmentId>();
  const [selectedRevisionId, setSelectedRevisionId] = useState<DevelopmentId>();
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

  const applyContext = useCallback((raw: DevelopmentDataServiceNodeContext) => {
    const next = normalizeContext(raw, node);
    const definition = next.draft.definition;
    setContext(next);
    setSelectedSourceId(
      definition.sourceTaskAssetId !== '0' ? definition.sourceTaskAssetId : undefined,
    );
    setSelectedRevisionId(
      definition.sourceTaskRevisionId !== '0' ? definition.sourceTaskRevisionId : undefined,
    );
    setServiceName(definition.serviceName || next.nodeName);
    setPath(definition.path || `/query/${next.nodeId}`);
    setMaxRows(definition.maxRows || 1000);
    setTimeoutSeconds(definition.timeoutSeconds || 30);
    setDescription(definition.description || '');
    setParameters(safeArray(definition.parameters));
    setResponseFields(safeArray(definition.responseFields));
    setDirty(false);
    return next;
  }, [node]);

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

  const availableSources = context?.availableSources || [];
  const sourceOptions = useMemo(() => {
    const values: DevelopmentDataServiceSource[] = [...availableSources];
    const pinned = context?.selectedSource;
    if (pinned && !values.some((source) => source.taskAssetId === pinned.taskAssetId)) {
      values.unshift(pinned);
    }
    return values
      .filter((source) => source && source.taskAssetId)
      .map((source) => ({
        value: source.taskAssetId,
        disabled: source.status !== 'ONLINE',
        label: `${source.nodeName || 'SQL'} · SQL R${source.currentRevisionNo || source.revisionNo || '-'} · ${source.status || 'UNKNOWN'}`,
      }));
  }, [availableSources, context?.selectedSource]);

  const activeSource = useMemo(
    () => availableSources.find((source) => source.taskAssetId === selectedSourceId)
      || (context?.selectedSource?.taskAssetId === selectedSourceId
        ? context.selectedSource
        : undefined),
    [availableSources, context?.selectedSource, selectedSourceId],
  );

  const selectedRevisionNo = useMemo(() => {
    if (!activeSource || !selectedRevisionId) return undefined;
    if (activeSource.revisionId === selectedRevisionId) return activeSource.revisionNo;
    if (activeSource.currentRevisionId === selectedRevisionId) {
      return activeSource.currentRevisionNo || undefined;
    }
    if (context?.selectedSource?.revisionId === selectedRevisionId) {
      return context.selectedSource.revisionNo;
    }
    return undefined;
  }, [activeSource, context?.selectedSource, selectedRevisionId]);

  const sourceUpdateAvailable = Boolean(
    activeSource?.currentRevisionId
      && selectedRevisionId
      && activeSource.currentRevisionId !== selectedRevisionId,
  );

  const markDirty = () => setDirty(true);

  const changeSource = (taskAssetId: DevelopmentId) => {
    const source = availableSources.find((item) => item.taskAssetId === taskAssetId);
    setSelectedSourceId(taskAssetId);
    setSelectedRevisionId(source?.currentRevisionId || source?.revisionId);
    setParameters([]);
    setResponseFields([]);
    markDirty();
  };

  const upgradeSource = () => {
    if (!activeSource?.currentRevisionId) return;
    setSelectedRevisionId(activeSource.currentRevisionId);
    setParameters([]);
    setResponseFields([]);
    markDirty();
  };

  const preview = async () => {
    if (!selectedSourceId || !selectedRevisionId || previewing) return;
    setPreviewing(true);
    try {
      const result = await previewDevelopmentDataServiceNode(
        node.id,
        selectedSourceId,
        selectedRevisionId,
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
          type: previous.type,
          required: previous.required,
          description: previous.description,
          example: previous.example,
        } : item;
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
      if (result?.source) {
        setSelectedSourceId(normalizeId(result.source.taskAssetId));
        setSelectedRevisionId(normalizeId(result.source.revisionId));
      }
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
    if (!selectedSourceId || !selectedRevisionId || saving) return;
    setSaving(true);
    try {
      const next = await saveDevelopmentDataServiceDraft(node.id, {
        sourceTaskAssetId: selectedSourceId,
        sourceTaskRevisionId: selectedRevisionId,
        serviceName: serviceName.trim(),
        path: path.trim(),
        method: 'GET',
        parameters: parameters.map((item) => ({
          ...item,
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
      message.success(`Data Service 草稿已保存 · Draft #${next?.draft?.draftRevision || '-'}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Data Service Node 草稿失败');
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    const draftRevision = context?.draft?.draftRevision || 0;
    if (!draftRevision || dirty || publishing) {
      if (dirty) message.warning('请先保存当前 Data Service Node 草稿');
      return;
    }
    setPublishing(true);
    try {
      const revision = await publishDevelopmentDataServiceNode(node.id, draftRevision);
      await load();
      await onSaved?.();
      message.success(`Data Service Node 已发布 · DS R${revision.revisionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布 Data Service Node 失败');
    } finally {
      setPublishing(false);
    }
  };

  const deployOrSync = async () => {
    const latestPublished = context?.latestPublishedRevision;
    if (!latestPublished || !publicationState || deploying) return;
    setDeploying(true);
    try {
      if (publicationState.published) {
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
      itemIndex === index ? { ...item, ...patch } : item,
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
      title: '参数',
      dataIndex: 'name',
      width: 170,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[#344054]">{value}</span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 130,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value}
          options={contractTypeOptions.filter((item) => item.value !== 'OBJECT')}
          className="w-full"
          onChange={(next) => updateParameter(index, { type: next })}
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      width: 72,
      render: (value: boolean, _record, index) => (
        <Switch
          size="small"
          checked={value}
          onChange={(next) => updateParameter(index, { required: next })}
        />
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          maxLength={1000}
          placeholder="参数说明"
          onChange={(event) => updateParameter(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 180,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          maxLength={1000}
          placeholder="例如：PAID"
          onChange={(event) => updateParameter(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const responseColumns = useMemo<TableColumnsType<DevelopmentDataServiceResponseField>>(() => [
    {
      title: '字段',
      dataIndex: 'name',
      width: 170,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[#344054]">{value}</span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 130,
      render: (value: DataServiceContractType, _record, index) => (
        <Select
          size="small"
          value={value}
          options={contractTypeOptions}
          className="w-full"
          onChange={(next) => updateResponseField(index, { type: next })}
        />
      ),
    },
    {
      title: '可空',
      dataIndex: 'nullable',
      width: 72,
      render: (value: boolean) => (
        <span className="text-[12px] text-[#667085]">{value ? '是' : '否'}</span>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          maxLength={1000}
          placeholder="字段说明"
          onChange={(event) => updateResponseField(index, { description: event.target.value })}
        />
      ),
    },
    {
      title: '示例',
      dataIndex: 'example',
      width: 180,
      render: (value: string | null | undefined, _record, index) => (
        <Input
          size="small"
          value={value || ''}
          maxLength={1000}
          placeholder="可选"
          onChange={(event) => updateResponseField(index, { example: event.target.value })}
        />
      ),
    },
  ], []);

  const revisionColumns = useMemo<TableColumnsType<DevelopmentDataServiceRevisionSummary>>(() => [
    {
      title: '版本',
      dataIndex: 'revisionNo',
      width: 90,
      render: (value: number) => <span className="font-medium">DS R{value}</span>,
    },
    {
      title: '来源 SQL',
      dataIndex: 'sourceTaskRevisionNo',
      width: 120,
      render: (value: number) => `SQL R${value}`,
    },
    {
      title: 'Draft',
      dataIndex: 'sourceDraftRevision',
      width: 100,
      render: (value: number) => `#${value}`,
    },
    {
      title: '发布时间',
      dataIndex: 'createTime',
      render: (value?: string) => formatTime(value),
    },
  ], []);

  if (loading) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <Spin size="small" />
      </div>
    );
  }

  if (!context) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white px-6">
        <div className="max-w-[520px] text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-lg bg-[#f5f5f6] text-[#667085]">
            <Network size={18} />
          </div>
          <div className="mt-3 text-[14px] font-semibold text-[#344054]">Data Service Node 加载失败</div>
          <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">
            {loadError || '节点上下文暂时不可用。编辑器已阻止异常数据继续渲染，避免工作区白屏。'}
          </div>
          <Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => void load()}>
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  const draftRevision = context?.draft?.draftRevision || 0;
  const latestPublished = context?.latestPublishedRevision;
  const canSave = Boolean(
    selectedSourceId
      && selectedRevisionId
      && serviceName.trim()
      && path.trim(),
  );
  const canPublish = Boolean(
    draftRevision > 0
      && !dirty
      && responseFields.length > 0,
  );
  const runtimeRevisionNo = publicationState?.detail?.sourceRevisionNo;
  const runtimeStatusLabel = !latestPublished
    ? '等待发布 DS Revision'
    : publicationError
      ? 'Runtime 状态不可用'
      : publicationLoading && !publicationState
        ? '正在查询 Runtime'
        : !publicationState
          ? 'Runtime 状态未知'
          : !publicationState.published
            ? '未部署'
            : publicationState.updateAvailable
              ? '待同步'
              : publicationState.detail?.enabled
                ? '已同步 · 运行中'
                : '已同步 · 已停用';
  const showDeployAction = Boolean(
    latestPublished
      && publicationState
      && (!publicationState.published || publicationState.updateAvailable),
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-12 shrink-0 items-center justify-between border-b border-[#e4e7ec] px-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f5f5f6] text-[#475467]">
            <Network size={15} />
          </span>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-semibold text-[#161823]">{node.name}</div>
            <div className="text-[10px] text-[#98a2b3]">
              Data Service Node
              {draftRevision ? ` · Draft #${draftRevision}` : ' · 尚未保存'}
              {latestPublished ? ` · 已发布 DS R${latestPublished.revisionNo}` : ''}
              {dirty ? ' · 有未保存更改' : ''}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            size="small"
            icon={<RefreshCw size={13} />}
            loading={previewing}
            disabled={!selectedSourceId || !selectedRevisionId}
            onClick={() => void preview()}
          >
            预览 Contract
          </Button>
          <Button
            size="small"
            icon={<Save size={13} />}
            loading={saving}
            disabled={!canSave}
            onClick={() => void save()}
          >
            保存草稿
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<Rocket size={13} />}
            loading={publishing}
            disabled={!canPublish}
            onClick={() => void publish()}
          >
            发布版本
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 flex items-center justify-between gap-3">
            <div>
              <div className="text-[11px] font-semibold text-[#344054]">Runtime 部署</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">
                Data Development 负责发布不可变 DS Revision；这里仅显式部署/同步，运行策略仍在“数据服务”模块管理。
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Tag className="!m-0" bordered={false}>{runtimeStatusLabel}</Tag>
              {latestPublished ? (
                <Button
                  size="small"
                  type="text"
                  icon={<RefreshCw size={12} />}
                  loading={publicationLoading}
                  onClick={() => void loadPublicationState(true, true)}
                >
                  刷新
                </Button>
              ) : null}
            </div>
          </div>

          <div className="flex max-w-[980px] flex-wrap items-center justify-between gap-3 border border-[#e5e7eb] bg-[#fafafa] px-3 py-2.5">
            <div className="flex min-w-0 flex-wrap items-center gap-x-5 gap-y-1 text-[11px] text-[#667085]">
              <span>
                最新发布：
                <b className="font-medium text-[#344054]">
                  {latestPublished ? `DS R${latestPublished.revisionNo}` : '尚未发布'}
                </b>
              </span>
              <span>
                Runtime：
                <b className="font-medium text-[#344054]">
                  {publicationState?.published ? `DS R${runtimeRevisionNo || '-'}` : '未部署'}
                </b>
              </span>
              {publicationState?.detail?.runtimePath ? (
                <span className="max-w-[360px] truncate font-mono text-[#475467]">
                  {publicationState.detail.runtimePath}
                </span>
              ) : null}
              {publicationState?.updateAvailable ? (
                <span className="font-medium text-[#b54708]">
                  Runtime 仍为 DS R{runtimeRevisionNo || '-'}，需显式同步到 DS R{latestPublished?.revisionNo}
                </span>
              ) : null}
              {publicationError ? (
                <span className="text-[#b42318]">{publicationError}</span>
              ) : null}
            </div>

            <div className="flex shrink-0 items-center gap-2">
              {showDeployAction ? (
                <Button
                  size="small"
                  type="primary"
                  icon={<Rocket size={13} />}
                  loading={deploying}
                  onClick={() => void deployOrSync()}
                >
                  {publicationState?.published ? '同步最新 Revision' : '部署 Runtime'}
                </Button>
              ) : null}
              {publicationState?.published ? (
                <Button
                  size="small"
                  icon={<ExternalLink size={13} />}
                  onClick={() => history.push('/data-service')}
                >
                  打开 API 服务
                </Button>
              ) : null}
            </div>
          </div>

          {latestPublished && publicationState && !publicationState.published ? (
            <div className="mt-1.5 text-[10px] text-[#98a2b3]">
              首次部署默认保持停用，部署完成后请到“数据服务”配置访问控制并显式启用。
            </div>
          ) : null}
        </section>

        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 text-[11px] font-semibold text-[#344054]">来源 SQL Revision</div>
          <div className="grid max-w-[980px] grid-cols-[110px_minmax(0,1fr)] items-start gap-y-2">
            <span className="pt-1.5 text-[11px] text-[#667085]">SQL 节点</span>
            <Select
              showSearch
              optionFilterProp="label"
              value={selectedSourceId}
              options={sourceOptions}
              placeholder="选择同项目中已发布的 ONLINE SQL"
              className="w-full"
              onChange={(value) => changeSource(String(value))}
            />
          </div>

          <div className="ml-[110px] mt-1.5 text-[10px] leading-5 text-[#98a2b3]">
            Data Service Draft 固定精确 SQL Revision。SQL 后续发布新版本时不会自动漂移，需要显式升级来源。
          </div>

          {!availableSources.length ? (
            <div className="mt-2 rounded-md border border-[#e4e7ec] bg-[#fafafa] px-2.5 py-2 text-[11px] text-[#667085]">
              当前项目暂无可选的 ONLINE SQL，请先在数据开发发布一个 SQL 节点。
            </div>
          ) : null}

          {activeSource && selectedRevisionId ? (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-[#667085]">
              <span>{activeSource.nodeName}</span>
              <Tag className="!m-0">SQL R{selectedRevisionNo || '-'}</Tag>
              <span className="text-[#98a2b3]">Revision #{selectedRevisionId}</span>
              {sourceUpdateAvailable ? (
                <>
                  <span className="font-medium text-[#b54708]">
                    SQL R{activeSource.currentRevisionNo} 可更新
                  </span>
                  <Button size="small" onClick={upgradeSource}>
                    更新来源
                  </Button>
                </>
              ) : (
                <span className="text-[#667085]">已固定当前 Revision</span>
              )}
            </div>
          ) : null}
        </section>

        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 text-[11px] font-semibold text-[#344054]">接口定义</div>
          <div className="grid max-w-[980px] grid-cols-[110px_minmax(0,1fr)_110px_minmax(0,1fr)] items-start gap-x-4 gap-y-2">
            <span className="pt-1.5 text-[11px] text-[#667085]">服务名称</span>
            <Input
              size="small"
              value={serviceName}
              maxLength={200}
              onChange={(event) => {
                setServiceName(event.target.value);
                markDirty();
              }}
            />
            <span className="pt-1.5 text-[11px] text-[#667085]">请求路径</span>
            <Input
              size="small"
              addonBefore="GET"
              value={path}
              maxLength={255}
              placeholder="/orders"
              onChange={(event) => {
                setPath(event.target.value);
                markDirty();
              }}
            />

            <span className="pt-1.5 text-[11px] text-[#667085]">最大行数</span>
            <InputNumber
              size="small"
              min={1}
              max={10000}
              value={maxRows}
              className="w-full"
              onChange={(value) => {
                setMaxRows(Number(value || 1000));
                markDirty();
              }}
            />
            <span className="pt-1.5 text-[11px] text-[#667085]">超时时间</span>
            <InputNumber
              size="small"
              min={1}
              max={3600}
              value={timeoutSeconds}
              addonAfter="秒"
              className="w-full"
              onChange={(value) => {
                setTimeoutSeconds(Number(value || 30));
                markDirty();
              }}
            />

            <span className="pt-1.5 text-[11px] text-[#667085]">说明</span>
            <div className="col-span-3">
              <Input.TextArea
                value={description}
                maxLength={2000}
                autoSize={{ minRows: 2, maxRows: 4 }}
                placeholder="说明这个数据服务提供什么能力"
                onChange={(event) => {
                  setDescription(event.target.value);
                  markDirty();
                }}
              />
            </div>
          </div>
        </section>

        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <div>
              <div className="text-[11px] font-semibold text-[#344054]">请求参数 Contract</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">
                参数名来自 SQL 中的 :name 命名参数；类型、必填、描述和示例属于 Data Service Node。
              </div>
            </div>
            <span className="text-[10px] text-[#98a2b3]">{parameters.length} 个参数</span>
          </div>
          <Table<DevelopmentDataServiceParameter>
            size="small"
            pagination={false}
            rowKey="name"
            dataSource={parameters}
            columns={parameterColumns}
            locale={{
              emptyText: selectedRevisionId
                ? '点击“预览 Contract”发现 SQL 请求参数'
                : '先选择一个 SQL Revision',
            }}
            scroll={{ x: 880 }}
          />
        </section>

        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <div>
              <div className="text-[11px] font-semibold text-[#344054]">响应字段 Contract</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">
                响应字段和基础类型通过只读 Preview 自动发现；描述和示例随 Data Service Revision 冻结。
              </div>
            </div>
            <span className="text-[10px] text-[#98a2b3]">{responseFields.length} 个字段</span>
          </div>
          <Table<DevelopmentDataServiceResponseField>
            size="small"
            pagination={false}
            rowKey="name"
            dataSource={responseFields}
            columns={responseColumns}
            locale={{
              emptyText: selectedRevisionId
                ? '点击“预览 Contract”发现响应字段'
                : '先选择一个 SQL Revision',
            }}
            scroll={{ x: 880 }}
          />
        </section>

        <section className="px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <div>
              <div className="text-[11px] font-semibold text-[#344054]">发布历史</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">
                发布只生成不可变 DS Revision；新版本不会自动改变线上 Runtime，需要在上方显式部署或同步。
              </div>
            </div>
            <Tag className="!m-0">
              {latestPublished ? `最新 DS R${latestPublished.revisionNo}` : '尚未发布'}
            </Tag>
          </div>
          <Table<DevelopmentDataServiceRevisionSummary>
            size="small"
            pagination={false}
            rowKey="id"
            dataSource={context?.revisions || []}
            columns={revisionColumns}
            locale={{ emptyText: '保存草稿并发布后，这里会出现 DS Revision' }}
          />
        </section>
      </div>
    </div>
  );
}
