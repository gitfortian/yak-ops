import { Button, Input, Select, Spin, Table, Tag, message, type TableColumnsType } from 'antd';
import { Database, RefreshCw, Save } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  getDevelopmentDatasetNode,
  previewDevelopmentDatasetNode,
  saveDevelopmentDatasetNode,
  type DevelopmentDatasetFieldDraft,
  type DevelopmentDatasetFieldRole,
  type DevelopmentDatasetNodeContext,
  type DevelopmentDatasetNodeSource,
} from '../../dataset-service';
import type { DevelopmentId, DevelopmentResourceNode } from '../../types';

interface DatasetNodeEditorProps {
  node: DevelopmentResourceNode;
  onSaved?: () => void | Promise<void>;
}

const roleOptions = [
  { label: '维度', value: 'DIMENSION' },
  { label: '指标', value: 'MEASURE' },
];

const toFieldDrafts = (context?: DevelopmentDatasetNodeContext) =>
  (context?.dataset?.fields || []).map((field) => ({
    fieldId: field.fieldId,
    physicalName: field.physicalName,
    displayName: field.displayName,
    dataType: field.dataType,
    nullable: field.nullable,
    description: field.description,
    defaultRole: field.defaultRole,
  }));

const DatasetNodeEditor = ({ node, onSaved }: DatasetNodeEditorProps) => {
  const [context, setContext] = useState<DevelopmentDatasetNodeContext>();
  const [selectedSourceId, setSelectedSourceId] = useState<DevelopmentId>();
  const [description, setDescription] = useState('');
  const [fields, setFields] = useState<DevelopmentDatasetFieldDraft[]>([]);
  const [loading, setLoading] = useState(true);
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);

  const applyContext = useCallback((next: DevelopmentDatasetNodeContext) => {
    setContext(next);
    setSelectedSourceId(next.selectedSource?.taskAssetId);
    setDescription(next.dataset?.description || '');
    setFields(toFieldDrafts(next));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      applyContext(await getDevelopmentDatasetNode(node.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载 Dataset Node 失败');
      setContext(undefined);
      setSelectedSourceId(undefined);
      setDescription('');
      setFields([]);
    } finally {
      setLoading(false);
    }
  }, [applyContext, node.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const updateField = useCallback((
    index: number,
    patch: Partial<DevelopmentDatasetFieldDraft>,
  ) => {
    setFields((current) => current.map((field, fieldIndex) =>
      fieldIndex === index ? { ...field, ...patch } : field,
    ));
  }, []);

  const columns = useMemo<TableColumnsType<DevelopmentDatasetFieldDraft>>(() => [
    {
      title: '字段',
      dataIndex: 'physicalName',
      width: 180,
      render: (value: string) => (
        <span className="font-mono text-[12px] text-[#344054]">{value}</span>
      ),
    },
    {
      title: '显示名称',
      dataIndex: 'displayName',
      width: 180,
      render: (value: string, _record, index) => (
        <Input
          size="small"
          value={value}
          maxLength={200}
          onChange={(event) => updateField(index, { displayName: event.target.value })}
        />
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      width: 110,
      render: (value: string) => <Tag className="!m-0">{value}</Tag>,
    },
    {
      title: '角色',
      dataIndex: 'defaultRole',
      width: 120,
      render: (value: DevelopmentDatasetFieldRole, _record, index) => (
        <Select
          size="small"
          value={value}
          options={roleOptions}
          className="w-full"
          onChange={(next) => updateField(index, {
            defaultRole: next as DevelopmentDatasetFieldRole,
          })}
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
          placeholder="可选"
          onChange={(event) => updateField(index, { description: event.target.value })}
        />
      ),
    },
  ], [updateField]);

  const availableSources = context?.availableSources || [];
  const selectableSourceIds = useMemo(
    () => new Set(availableSources.map((source) => source.taskAssetId)),
    [availableSources],
  );
  const sourceOptions = useMemo(() => {
    const values: DevelopmentDatasetNodeSource[] = [...availableSources];
    const selected = context?.selectedSource;
    if (selected && !values.some((item) => item.taskAssetId === selected.taskAssetId)) {
      values.unshift(selected);
    }
    return values.map((source) => ({
      value: source.taskAssetId,
      label: `${source.nodeName} · SQL R${source.revisionNo} · ${source.status}`,
      disabled: source.status !== 'ONLINE',
    }));
  }, [availableSources, context?.selectedSource]);
  const activeSource = useMemo(
    () => availableSources.find((source) => source.taskAssetId === selectedSourceId)
      || (context?.selectedSource?.taskAssetId === selectedSourceId ? context.selectedSource : undefined),
    [availableSources, context?.selectedSource, selectedSourceId],
  );
  const currentVersion = context?.dataset?.currentVersion;
  const sourceChanged = Boolean(
    selectedSourceId
      && currentVersion?.sourceTaskAssetId
      && selectedSourceId !== currentVersion.sourceTaskAssetId,
  );
  const sourceUpdated = Boolean(
    activeSource
      && currentVersion
      && activeSource.taskAssetId === currentVersion.sourceTaskAssetId
      && activeSource.revisionNo !== currentVersion.sourceTaskRevisionNo,
  );
  const sourceSelectable = Boolean(selectedSourceId && selectableSourceIds.has(selectedSourceId));

  const changeSource = (value: DevelopmentId) => {
    setSelectedSourceId(value);
    if (value !== currentVersion?.sourceTaskAssetId) setFields([]);
  };

  const preview = async () => {
    if (!selectedSourceId || !sourceSelectable || previewing) return;
    setPreviewing(true);
    try {
      const next = await previewDevelopmentDatasetNode(node.id, selectedSourceId);
      const existing = new Map(fields.map((field) => [
        field.physicalName.toLowerCase(),
        field,
      ]));
      setFields(next.map((field) => {
        const previous = existing.get(field.physicalName.toLowerCase());
        return previous ? {
          ...field,
          fieldId: previous.fieldId || field.fieldId,
          displayName: previous.displayName || field.displayName,
          description: previous.description,
          defaultRole: previous.defaultRole,
        } : field;
      }));
      message.success(`已发现 ${next.length} 个输出字段`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发现 Dataset 字段失败');
    } finally {
      setPreviewing(false);
    }
  };

  const save = async () => {
    if (!selectedSourceId || !sourceSelectable || !fields.length || saving) return;
    setSaving(true);
    try {
      const next = await saveDevelopmentDatasetNode(node.id, {
        sourceTaskAssetId: selectedSourceId,
        description: description.trim() || undefined,
        fields: fields.map((field) => ({
          ...field,
          displayName: field.displayName.trim() || field.physicalName,
          description: field.description?.trim() || undefined,
        })),
      });
      applyContext(next);
      await onSaved?.();
      message.success(`Dataset 已保存 · DV${next.dataset?.currentVersion?.versionNo || 1}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Dataset Node 失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <Spin size="small" />
      </div>
    );
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-12 shrink-0 items-center justify-between border-b border-[#e4e7ec] px-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f5f5f6] text-[#475467]">
            <Database size={15} />
          </span>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-semibold text-[#161823]">{node.name}</div>
            <div className="text-[10px] text-[#98a2b3]">
              数据集节点{context?.dataset ? ` · Dataset #${context.dataset.datasetId}` : ' · 尚未创建资产'}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="small"
            icon={<RefreshCw size={13} />}
            loading={previewing}
            disabled={!sourceSelectable}
            onClick={() => void preview()}
          >
            {fields.length ? '刷新字段' : '发现字段'}
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<Save size={13} />}
            loading={saving}
            disabled={!sourceSelectable || !fields.length}
            onClick={() => void save()}
          >
            保存数据集
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 text-[11px] font-semibold text-[#344054]">来源 SQL</div>
          <div className="grid max-w-[900px] grid-cols-[110px_minmax(0,1fr)] items-start gap-y-2">
            <span className="pt-1.5 text-[11px] text-[#667085]">SQL 节点</span>
            <Select
              showSearch
              optionFilterProp="label"
              value={selectedSourceId}
              options={sourceOptions}
              placeholder="选择一个已发布的 SQL 节点"
              className="w-full"
              onChange={(value) => changeSource(String(value))}
            />
          </div>
          <div className="ml-[110px] mt-1.5 text-[10px] leading-5 text-[#98a2b3]">
            数据集只引用已发布 SQL 的不可变 Revision；节点之间的执行依赖请在“工作流”模块配置。
          </div>
          {!availableSources.length ? (
            <div className="mt-2 rounded-md border border-[#f2d6a2] bg-[#fffbeb] px-2.5 py-2 text-[11px] text-[#8a6116]">
              当前项目暂无可选的 ONLINE SQL，请先发布一个 SQL 节点。
            </div>
          ) : null}
          {activeSource ? (
            <div className="mt-2 flex items-center gap-2 text-[11px] text-[#667085]">
              <span>{activeSource.nodeName}</span>
              <span>·</span>
              <span>SQL R{activeSource.revisionNo}</span>
              <Tag className="!m-0">{activeSource.status}</Tag>
              {currentVersion ? (
                <span className="text-[#98a2b3]">
                  当前数据集：DV{currentVersion.versionNo} / SQL R{currentVersion.sourceTaskRevisionNo}
                </span>
              ) : null}
              {sourceChanged ? <span className="font-medium text-[#f79009]">来源已更换</span> : null}
              {sourceUpdated ? <span className="font-medium text-[#f79009]">SQL 已有新 Revision</span> : null}
            </div>
          ) : null}
        </section>

        <section className="border-b border-[#eef0f2] px-4 py-3">
          <div className="mb-2 text-[11px] font-semibold text-[#344054]">基本信息</div>
          <div className="grid max-w-[900px] grid-cols-[110px_minmax(0,1fr)] items-start gap-y-2">
            <span className="pt-1.5 text-[11px] text-[#667085]">名称</span>
            <Input size="small" value={node.name} disabled />
            <span className="pt-1.5 text-[11px] text-[#667085]">描述</span>
            <Input.TextArea
              value={description}
              maxLength={2000}
              autoSize={{ minRows: 2, maxRows: 4 }}
              placeholder="说明这个数据集提供什么数据"
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
        </section>

        <section className="px-4 py-3">
          <div className="mb-2 flex items-center justify-between">
            <div>
              <div className="text-[11px] font-semibold text-[#344054]">字段结构</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">
                物理字段和类型来自选中的 SQL Revision；可调整显示名称、角色和描述。
              </div>
            </div>
            <span className="text-[10px] text-[#98a2b3]">{fields.length} 个字段</span>
          </div>
          <Table<DevelopmentDatasetFieldDraft>
            size="small"
            bordered={false}
            pagination={false}
            rowKey={(record) => record.fieldId || record.physicalName}
            dataSource={fields}
            columns={columns}
            locale={{ emptyText: sourceSelectable ? '点击“发现字段”读取 SQL 输出结构' : '先选择一个已发布 SQL' }}
            scroll={{ x: 880 }}
          />
        </section>
      </div>
    </div>
  );
};

export default DatasetNodeEditor;
