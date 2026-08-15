import type { WorkflowFailureStrategy } from '@/services/workflow';
import {
  listWorkflowVersions,
  type WorkflowDefinition,
  type WorkflowVersionSummary,
} from '@/services/workflow/definitions';
import {
  Button,
  Dropdown,
  InputNumber,
  Modal,
  Popover,
  Select,
  Spin,
  Tooltip,
  message,
} from 'antd';
import {
  ChevronDown,
  CircleStop,
  GitCommitHorizontal,
  History,
  Play,
  Rocket,
  Save,
  SlidersHorizontal,
} from 'lucide-react';
import { useMemo, useState } from 'react';

interface WorkflowToolbarProps {
  definition?: WorkflowDefinition;
  name: string;
  description: string;
  workflowTimeoutSeconds: number;
  failureStrategy: WorkflowFailureStrategy;
  nodesCount: number;
  edgesCount: number;
  locked: boolean;
  saving: boolean;
  testing: boolean;
  statusAction: boolean;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onWorkflowTimeoutChange: (value: number) => void;
  onFailureStrategyChange: (value: WorkflowFailureStrategy) => void;
  onClear: () => void;
  onSave: () => void;
  onTestRun: () => void;
  onOnline: () => void;
  onOffline: () => void;
}

const WORKFLOW_FAILURE_OPTIONS = [
  { value: 'CONTINUE_INDEPENDENT_BRANCHES', label: '继续独立分支' },
  { value: 'FAIL_FAST', label: '快速失败' },
  { value: 'TERMINATE_ALL', label: '终止全部分支' },
];

const formatDateTime = (value?: string) => {
  if (!value) return '--';
  return value.replace('T', ' ').slice(0, 19);
};

const WorkflowToolbar = (props: WorkflowToolbarProps) => {
  const {
    definition,
    name,
    workflowTimeoutSeconds,
    failureStrategy,
    nodesCount,
    edgesCount,
    locked,
    saving,
    testing,
    statusAction,
    onWorkflowTimeoutChange,
    onFailureStrategyChange,
    onSave,
    onTestRun,
    onOnline,
    onOffline,
  } = props;
  const [versionOpen, setVersionOpen] = useState(false);
  const [runtimeSettingsOpen, setRuntimeSettingsOpen] = useState(false);
  const [versions, setVersions] = useState<WorkflowVersionSummary[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);

  const status = definition?.status || 'DRAFT';
  const activeVersionNo = definition?.activeVersionNo;
  const draftChanged = definition?.draftChanged ?? true;
  const nextVersionNo = (definition?.latestVersionNo || 0) + 1;
  const hasPublished = Boolean(activeVersionNo);
  const hasRuntimeOverrides = workflowTimeoutSeconds > 0
    || failureStrategy !== 'CONTINUE_INDEPENDENT_BRANCHES';
  const canPublish = !hasPublished || draftChanged || status === 'OFFLINE';
  const busy = saving || statusAction;

  const lifecycleText = !hasPublished
    ? '草稿 · 尚未发布'
    : status === 'OFFLINE'
      ? `已停用 v${activeVersionNo}${draftChanged ? ' · 有草稿修改' : ''}`
      : draftChanged
        ? `草稿 · 已发布 v${activeVersionNo} · 有草稿修改`
        : `已发布 v${activeVersionNo}`;

  const publishButtonText = !canPublish
    ? '已发布'
    : status === 'OFFLINE' && hasPublished && !draftChanged
      ? '重新启用'
      : '发布';

  const loadVersions = async () => {
    if (!definition?.id) return;
    setVersionsLoading(true);
    try {
      setVersions(await listWorkflowVersions(definition.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '版本列表加载失败');
    } finally {
      setVersionsLoading(false);
    }
  };

  const confirmPublish = () => {
    if (!canPublish || testing || busy) return;

    const reenable = status === 'OFFLINE' && hasPublished && !draftChanged;
    const targetVersionNo = hasPublished && draftChanged ? nextVersionNo : activeVersionNo || 1;

    Modal.confirm({
      centered: true,
      title: reenable
        ? `重新启用工作流 v${targetVersionNo}？`
        : `发布工作流 v${targetVersionNo}？`,
      content: reenable
        ? `将重新启用已发布的 v${targetVersionNo}，不会创建新的发布版本。`
        : `当前草稿将形成不可变的 v${targetVersionNo}，已有运行实例不会受到影响。`,
      okText: reenable ? '重新启用' : '发布',
      cancelText: '取消',
      onOk: onOnline,
    });
  };

  const confirmOffline = () => {
    if (testing || busy || status !== 'ONLINE' || !hasPublished) return;
    Modal.confirm({
      centered: true,
      title: `停用工作流 v${activeVersionNo}？`,
      content: '停用后将关闭正式运行入口，当前草稿仍可继续编辑和测试。',
      okText: '停用',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: onOffline,
    });
  };

  const versionContent = useMemo(() => (
    <div className="w-[320px] overflow-hidden rounded-[10px] border border-[#e4e7ec] bg-white shadow-[0_10px_28px_rgba(22,24,35,.12)]">
      <div className="flex h-11 items-center justify-between border-b border-[#f0f1f3] px-3.5">
        <div className="text-[12px] font-semibold text-[#344054]">发布版本</div>
        <span className="text-[9px] text-[#98a2b3]">{hasPublished ? `当前 v${activeVersionNo}` : '尚未发布'}</span>
      </div>
      <div className="max-h-[360px] overflow-auto p-2.5">
        {versionsLoading ? (
          <div className="flex h-24 items-center justify-center"><Spin size="small" /></div>
        ) : versions.length ? versions.map((version) => (
          <div
            key={version.id}
            className="mb-1.5 rounded-[8px] border border-[#eaecf0] bg-white px-3 py-2.5 last:mb-0"
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[#344054]">
                <GitCommitHorizontal size={13} />
                v{version.versionNo}
                {version.active ? (
                  <span className="rounded-[4px] bg-[#f4f5f6] px-1.5 py-0.5 text-[9px] font-medium text-[#475467]">当前</span>
                ) : null}
              </div>
              <span className="text-[9px] text-[#98a2b3]">{formatDateTime(version.publishedAt)}</span>
            </div>
            <div className="mt-1.5 text-[10px] text-[#667085]">
              {version.nodeCount} 节点 · {version.edgeCount} 连线 · {version.taskBindings.length} 个任务版本
            </div>
            <div className="mt-1 truncate text-[9px] text-[#98a2b3]">
              {version.taskBindings.map((item) => `${item.taskName} v${item.taskVersion}`).join(' · ') || '无任务'}
            </div>
          </div>
        )) : (
          <div className="flex h-24 items-center justify-center text-[11px] text-[#98a2b3]">暂无发布版本</div>
        )}
      </div>
    </div>
  ), [activeVersionNo, hasPublished, versions, versionsLoading]);

  const runtimeSettingsContent = (
    <div className="w-[300px] overflow-hidden rounded-[10px] border border-[#e4e7ec] bg-white shadow-[0_10px_28px_rgba(22,24,35,.12)]">
      <div className="border-b border-[#f0f1f3] px-4 py-3">
        <div className="text-[12px] font-semibold text-[#344054]">运行设置</div>
        <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">运行参数会进入草稿、测试运行和后续发布版本。</div>
      </div>
      <div className="space-y-4 p-4">
        <div>
          <div className="mb-1.5 text-[10px] font-medium text-[#667085]">失败策略</div>
          <Select
            size="small"
            disabled={locked}
            className="w-full"
            value={failureStrategy}
            options={WORKFLOW_FAILURE_OPTIONS}
            onChange={(value) => onFailureStrategyChange(value as WorkflowFailureStrategy)}
          />
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">默认继续执行与失败节点无依赖的独立分支。</div>
        </div>
        <div>
          <div className="mb-1.5 text-[10px] font-medium text-[#667085]">工作流整体超时</div>
          <InputNumber
            size="small"
            controls={false}
            disabled={locked}
            min={0}
            max={7 * 24 * 60 * 60}
            value={workflowTimeoutSeconds}
            addonAfter="秒"
            className="!w-full"
            onChange={(value) => onWorkflowTimeoutChange(Number(value || 0))}
          />
          <div className="mt-1 text-[9px] leading-4 text-[#98a2b3]">0 表示不设置工作流级超时。</div>
        </div>
      </div>
    </div>
  );

  const publishMoreItems = status === 'ONLINE' && hasPublished
    ? [{
      key: 'offline',
      label: <span className="text-[#b42318]">停用正式运行入口</span>,
      icon: <CircleStop size={13} className="text-[#b42318]" />,
    }]
    : [];

  return (
    <header className="workflow-editor-toolbar flex h-[52px] shrink-0 items-center justify-between border-b border-[#e8eaee] bg-white px-4">
      <div className="min-w-0">
        <div
          className="max-w-[420px] truncate text-[13px] font-semibold leading-5 text-[#161823]"
          title={name || '未命名工作流'}
        >
          {name || '未命名工作流'}
        </div>
        <div className="mt-0.5 flex h-4 items-center gap-2 text-[9px] leading-4 text-[#98a2b3]">
          <span>{lifecycleText}</span>
          <span className="h-1 w-1 rounded-full bg-[#d0d5dd]" />
          <span>{nodesCount} 节点 · {edgesCount} 连线</span>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1.5">
        <div className="flex items-center gap-0.5">
          <Popover
            open={runtimeSettingsOpen}
            onOpenChange={setRuntimeSettingsOpen}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={runtimeSettingsContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Tooltip title="运行设置">
              <Button
                type="text"
                aria-label="运行设置"
                disabled={busy}
                icon={<SlidersHorizontal size={14} />}
                className="relative !flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
              >
                {hasRuntimeOverrides ? (
                  <span className="absolute right-[5px] top-[5px] h-1.5 w-1.5 rounded-full bg-[#fe2c55] ring-2 ring-white" />
                ) : null}
              </Button>
            </Tooltip>
          </Popover>

          <Popover
            open={versionOpen}
            onOpenChange={(open) => {
              setVersionOpen(open);
              if (open) void loadVersions();
            }}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={versionContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Tooltip title="发布版本">
              <Button
                type="text"
                aria-label="发布版本"
                disabled={!definition?.id || busy}
                icon={<History size={14} />}
                className="!flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
              />
            </Tooltip>
          </Popover>
        </div>

        <span className="mx-1 h-5 w-px bg-[#eceef1]" />

        <Tooltip title="保存当前草稿并按草稿配置测试，不影响已发布版本">
          <Button
            size="small"
            loading={testing}
            disabled={!definition?.id || busy}
            icon={<Play size={13} />}
            onClick={onTestRun}
            className="!h-8 !rounded-[7px] !border-[#e4e7ec] !px-3 !text-[11px] !font-medium !text-[#344054] !shadow-none"
          >
            {testing ? '测试运行中' : '测试运行'}
          </Button>
        </Tooltip>

        <Button
          size="small"
          loading={saving}
          disabled={testing || statusAction}
          icon={<Save size={13} />}
          onClick={onSave}
          className="!h-8 !rounded-[7px] !border-[#e4e7ec] !px-3 !text-[11px] !font-medium !text-[#344054] !shadow-none"
        >
          保存草稿
        </Button>

        <div className="flex items-center">
          <Tooltip title={!canPublish ? '当前草稿与已发布版本一致' : undefined}>
            <span>
              <Button
                type="primary"
                size="small"
                loading={statusAction}
                disabled={testing || saving || !canPublish}
                icon={<Rocket size={13} />}
                onClick={confirmPublish}
                className={[
                  '!h-8 !rounded-[7px] !px-3.5 !text-[11px] !font-medium !shadow-none',
                  publishMoreItems.length ? '!rounded-r-[4px]' : '',
                ].join(' ')}
              >
                {publishButtonText}
              </Button>
            </span>
          </Tooltip>

          {publishMoreItems.length ? (
            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: publishMoreItems,
                onClick: ({ key }) => {
                  if (key === 'offline') confirmOffline();
                },
              }}
            >
              <Button
                type="primary"
                size="small"
                aria-label="更多发布操作"
                disabled={testing || busy}
                icon={<ChevronDown size={12} />}
                className="!-ml-px !h-8 !w-7 !min-w-0 !rounded-[4px] !rounded-r-[7px] !border-l-white/30 !p-0 !shadow-none"
              />
            </Dropdown>
          ) : null}
        </div>
      </div>
    </header>
  );
};

export default WorkflowToolbar;
