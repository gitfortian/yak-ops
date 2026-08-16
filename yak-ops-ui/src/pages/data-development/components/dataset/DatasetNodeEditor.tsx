import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import {
  Button,
  Input,
  Select,
  Spin,
  Table,
  Tooltip,
  message,
  type TableColumnsType,
} from 'antd';
import {
  FileStack,
  LoaderCircle,
  Play,
  RefreshCw,
  Save,
  X,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

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
  onDirtyChange?: (dirty: boolean) => void;
}

type RightPanelKey = 'properties' | 'versions';

const panelItems: Array<{ key: RightPanelKey; label: string }> = [
  { key: 'properties', label: '属性' },
  { key: 'versions', label: '版本' },
];

const roleOptions = [
  { label: '维度', value: 'DIMENSION' },
  { label: '指标', value: 'MEASURE' },
];

const DEFAULT_PANEL_WIDTH = 380;
const MIN_PANEL_WIDTH = 280;
const MAX_PANEL_WIDTH = 640;
const PANEL_WIDTH_STORAGE_KEY = 'yak-data-development.dataset-right-panel-width';

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, disabled, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const clampPanelWidth = (value: number) =>
  Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, value));

const initialPanelWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_PANEL_WIDTH;
  const stored = Number(window.localStorage.getItem(PANEL_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampPanelWidth(stored)
    : DEFAULT_PANEL_WIDTH;
};

const formatTime = (value?: string | null) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value.replace('T', ' ').slice(0, 19)
    : date.toLocaleString('zh-CN', { hour12: false });
};

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

const DatasetNodeEditor = ({ node, onSaved, onDirtyChange }: DatasetNodeEditorProps) => {
  const dirtyChangeRef = useRef(onDirtyChange);
  const [context, setContext] = useState<DevelopmentDatasetNodeContext>();
  const [selectedSourceId, setSelectedSourceId] = useState<DevelopmentId>();
  const [description, setDescription] = useState('');
  const [fields, setFields] = useState<DevelopmentDatasetFieldDraft[]>([]);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activePanel, setActivePanel] = useState<RightPanelKey>();
  const [panelWidth, setPanelWidth] = useState(initialPanelWidth);
  const [resizing, setResizing] = useState(false);

  useEffect(() => {
    dirtyChangeRef.current = onDirtyChange;
  }, [onDirtyChange]);

  const setDirtyState = useCallback((next: boolean) => {
    setDirty(next);
    dirtyChangeRef.current?.(next);
  }, []);

  const markDirty = useCallback(() => setDirtyState(true), [setDirtyState]);

  useEffect(() => () => dirtyChangeRef.current?.(false), []);

  const applyContext = useCallback((next: DevelopmentDatasetNodeContext) => {
    setContext(next);
    setSelectedSourceId(next.selectedSource?.taskAssetId);
    setDescription(next.dataset?.description || '');
    setFields(toFieldDrafts(next));
    setDirtyState(false);
  }, [setDirtyState]);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    try {
      applyContext(await getDevelopmentDatasetNode(node.id));
    } catch (error) {
      const text = error instanceof Error ? error.message : '加载 Dataset Node 失败';
      setLoadError(text);
      setContext(undefined);
      message.error(text);
    } finally {
      setLoading(false);
    }
  }, [applyContext, node.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const availableSources = context?.availableSources || [];
  const currentVersion = context?.dataset?.currentVersion;
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
      label: `${source.nodeName} · SQL R${source.revisionNo}`,
      disabled: source.status !== 'ONLINE',
    }));
  }, [availableSources, context?.selectedSource]);

  const activeSource = useMemo(
    () => availableSources.find((source) => source.taskAssetId === selectedSourceId)
      || (context?.selectedSource?.taskAssetId === selectedSourceId ? context.selectedSource : undefined),
    [availableSources, context?.selectedSource, selectedSourceId],
  );

  const sourceSelectable = Boolean(selectedSourceId && selectableSourceIds.has(selectedSourceId));
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

  const changeSource = (value: DevelopmentId) => {
    setSelectedSourceId(value);
    const sameSnapshot = currentVersion
      && value === currentVersion.sourceTaskAssetId
      && activeSource?.revisionNo === currentVersion.sourceTaskRevisionNo;
    if (!sameSnapshot) setFields([]);
    markDirty();
  };

  const updateField = useCallback((
    index: number,
    patch: Partial<DevelopmentDatasetFieldDraft>,
  ) => {
    setFields((current) => current.map((field, fieldIndex) =>
      fieldIndex === index ? { ...field, ...patch } : field,
    ));
    markDirty();
  }, [markDirty]);

  const columns = useMemo<TableColumnsType<DevelopmentDatasetFieldDraft>>(() => [
    {
      title: '字段',
      dataIndex: 'physicalName',
      width: 190,
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
      render: (value: string) => (
        <span className="font-mono text-[11px] text-[#667085]">{value}</span>
      ),
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
          placeholder="字段说明"
          onChange={(event) => updateField(index, { description: event.target.value })}
        />
      ),
    },
  ], [updateField]);

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
      markDirty();
      message.success(`已发现 ${next.length} 个字段`);
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
      message.success(`数据集已保存 · DV${next.dataset?.currentVersion?.versionNo || 1}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存 Dataset Node 失败');
    } finally {
      setSaving(false);
    }
  };

  const versions = useMemo(
    () => [...(context?.dataset?.versions || [])].sort((left, right) => right.versionNo - left.versionNo),
    [context?.dataset?.versions],
  );

  const propertiesPanel = (
    <div className="text-[12px] leading-5">
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-x-4 gap-y-4">
        <div className="text-[#667085]">数据集名称：</div>
        <Input size="small" value={node.name} disabled />
        <div className="self-start pt-1 text-[#667085]">描述：</div>
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 6 }}
          maxLength={2000}
          value={description}
          placeholder="说明这个数据集提供什么数据"
          onChange={(event) => {
            setDescription(event.target.value);
            markDirty();
          }}
        />
      </div>

      <dl className="m-0 mt-5 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-3 border-t border-[#eef0f2] pt-4">
        <dt className="text-[#667085]">Dataset：</dt>
        <dd className="m-0 text-[#344054]">{context?.dataset ? `#${context.dataset.datasetId}` : '尚未创建'}</dd>
        <dt className="text-[#667085]">当前版本：</dt>
        <dd className="m-0 text-[#344054]">{currentVersion ? `DV${currentVersion.versionNo}` : '尚未保存'}</dd>
        <dt className="text-[#667085]">来源 SQL：</dt>
        <dd className="m-0 break-all text-[#344054]">
          {activeSource ? `${activeSource.nodeName} · SQL R${activeSource.revisionNo}` : '未选择'}
        </dd>
        <dt className="text-[#667085]">字段：</dt>
        <dd className="m-0 text-[#344054]">{fields.length} 个</dd>
      </dl>
    </div>
  );

  const versionsPanel = (
    <div className="space-y-2 text-[12px]">
      {versions.length ? versions.map((version, index) => (
        <div
          key={version.versionId}
          className="flex items-center gap-2 rounded-[3px] border border-[#eaecf0] px-2.5 py-2"
        >
          <FileStack size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-[#344054]">DV{version.versionNo}</span>
              {index === 0 ? (
                <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">当前</span>
              ) : null}
            </div>
            <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">
              SQL R{version.sourceTaskRevisionNo} · {formatTime(version.createTime)}
            </div>
          </div>
        </div>
      )) : (
        <div className="py-8 text-center text-[11px] text-[#98a2b3]">暂无数据集版本</div>
      )}
    </div>
  );

  const panelContent: Record<RightPanelKey, ReactNode> = {
    properties: propertiesPanel,
    versions: versionsPanel,
  };

  const handleResizeStart = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!activePanel) return;
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = panelWidth;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;

    setResizing(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const resize = (moveEvent: PointerEvent) => {
      setPanelWidth(clampPanelWidth(startWidth + startX - moveEvent.clientX));
    };
    const finish = (upEvent: PointerEvent) => {
      const nextWidth = clampPanelWidth(startWidth + startX - upEvent.clientX);
      setPanelWidth(nextWidth);
      setResizing(false);
      window.localStorage.setItem(PANEL_WIDTH_STORAGE_KEY, String(nextWidth));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', resize);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };

    window.addEventListener('pointermove', resize);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
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
          <div className="text-[14px] font-semibold text-[#344054]">Dataset Node 加载失败</div>
          <div className="mt-2 text-[12px] leading-5 text-[#98a2b3]">{loadError || '未返回有效编辑上下文'}</div>
          <Button className="mt-4" size="small" icon={<RefreshCw size={13} />} onClick={() => void load()}>
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  const canPreview = sourceSelectable;
  const canSave = sourceSelectable && fields.length > 0 && dirty;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2">
        <div className="flex shrink-0 items-center gap-0.5">
          <ToolbarButton
            title={previewing ? '正在发现字段' : fields.length ? '刷新字段' : '发现字段'}
            disabled={!canPreview || previewing}
            onClick={() => void preview()}
          >
            {previewing ? <LoaderCircle size={15} className="animate-spin" /> : <Play size={15} strokeWidth={1.8} />}
          </ToolbarButton>
          <ToolbarDivider />
          <ToolbarButton
            title="保存数据集版本"
            disabled={!canSave || saving}
            onClick={() => void save()}
          >
            {saving ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} strokeWidth={1.8} />}
          </ToolbarButton>
        </div>

        <div className="flex min-w-0 items-center gap-2 pr-1">
          <span className="shrink-0 text-[10px] text-[#98a2b3]">来源 SQL</span>
          <Select
            showSearch
            optionFilterProp="label"
            variant="borderless"
            size="small"
            value={selectedSourceId}
            options={sourceOptions}
            placeholder="选择已发布 SQL"
            className="min-w-[260px] max-w-[360px]"
            popupMatchSelectWidth={360}
            onChange={(value) => changeSource(String(value))}
          />
          {sourceUpdated ? (
            <span className="shrink-0 text-[10px] font-medium text-[#f79009]">有新 Revision</span>
          ) : sourceChanged ? (
            <span className="shrink-0 text-[10px] font-medium text-[#f79009]">来源已更换</span>
          ) : null}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#eef0f2] px-3">
            <span className="text-[12px] font-medium text-[#344054]">字段结构</span>
            <span className="text-[10px] text-[#98a2b3]">{fields.length} 个字段</span>
          </div>

          <div className="min-h-0 flex-1 overflow-hidden px-2 pt-2">
            <Table<DevelopmentDatasetFieldDraft>
              size="small"
              bordered={false}
              pagination={false}
              rowKey={(record) => record.fieldId || record.physicalName}
              dataSource={fields}
              columns={columns}
              locale={{
                emptyText: sourceSelectable
                  ? '点击顶部“发现字段”读取 SQL 输出结构'
                  : '选择一个已发布 SQL',
              }}
              scroll={{ x: 900, y: 'calc(100vh - 220px)' }}
            />
          </div>

          <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
            <div className="flex min-w-0 items-center gap-3">
              <span className="font-medium text-[#667085]">DATASET</span>
              <span className="truncate">{node.name}</span>
              {dirty ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
                  未保存
                </span>
              ) : null}
              <span className="max-w-[280px] truncate text-[#667085]">
                {activeSource ? `${activeSource.nodeName} · SQL R${activeSource.revisionNo}` : '未选择来源 SQL'}
              </span>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <span>{fields.length} 个字段</span>
              <span>{currentVersion ? `DV${currentVersion.versionNo}` : '尚未保存版本'}</span>
            </div>
          </div>
        </section>

        <aside className="flex shrink-0 bg-white" style={BRAND_CSS_VARIABLES}>
          <div
            className={[
              'relative h-full shrink-0',
              resizing ? 'transition-none' : 'transition-[width] duration-200 ease-out',
            ].join(' ')}
            style={{ width: activePanel ? panelWidth : 0 }}
          >
            {activePanel ? (
              <div
                role="separator"
                aria-label="调整右侧面板宽度"
                aria-orientation="vertical"
                onPointerDown={handleResizeStart}
                className="group absolute inset-y-0 left-0 z-30 w-3 -translate-x-1/2 cursor-col-resize touch-none"
              >
                <div className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#e5e7eb] transition-[width,background-color] duration-150 group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:w-[2px] group-active:bg-[rgba(254,44,85,1)]" />
              </div>
            ) : null}

            <div className="h-full overflow-hidden">
              <div className="flex h-full flex-col bg-white" style={{ width: panelWidth }}>
                <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
                  <span className="text-[13px] font-semibold text-[#30323b]">
                    {panelItems.find((item) => item.key === activePanel)?.label}
                  </span>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      title="刷新"
                      onClick={() => {
                        if (dirty) {
                          message.warning('当前有未保存修改，请先保存');
                          return;
                        }
                        void load();
                      }}
                      className="flex h-7 items-center gap-1 rounded-[3px] px-2 text-[11px] text-[#475467] transition-colors hover:bg-[#f5f5f6]"
                    >
                      <RefreshCw size={13} strokeWidth={1.8} />
                      刷新
                    </button>
                    <button
                      type="button"
                      title="关闭"
                      aria-label="关闭右侧面板"
                      onClick={() => setActivePanel(undefined)}
                      className="flex h-7 w-7 items-center justify-center rounded-[3px] text-[#667085] transition-colors hover:bg-[#f5f5f6] hover:text-[#344054]"
                    >
                      <X size={14} strokeWidth={1.8} />
                    </button>
                  </div>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5">
                  {activePanel ? panelContent[activePanel] : null}
                </div>
              </div>
            </div>
          </div>

          <div className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
            {panelItems.map((item, index) => {
              const active = activePanel === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  title={item.label}
                  aria-label={`${active ? '收起' : '展开'}${item.label}`}
                  aria-expanded={active}
                  onClick={() => setActivePanel((current) => current === item.key ? undefined : item.key)}
                  className={[
                    'relative flex min-h-[72px] w-9 shrink-0 items-center justify-center border-b border-[#e5e7eb] py-3 text-[12px] leading-5 transition-[color,background-color,opacity]',
                    '[writing-mode:vertical-rl] [letter-spacing:3px]',
                    index === 0 ? 'border-t' : '',
                    active
                      ? 'text-[var(--yak-brand-color)] opacity-100 before:absolute before:inset-y-0 before:left-0 before:w-px before:bg-[var(--yak-brand-color)]'
                      : 'text-[#475467] opacity-70 hover:bg-[#f7f8fa] hover:text-[#344054] hover:opacity-100',
                  ].join(' ')}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </aside>
      </div>
    </div>
  );
};

export default DatasetNodeEditor;
