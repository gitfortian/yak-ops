import type { WorkflowFailureStrategy } from '@/services/workflow';
import {
  listWorkflowVersions,
  type WorkflowDefinition,
  type WorkflowVersionSummary,
} from '@/services/workflow/definitions';
import { Button, InputNumber, Popover, Select, Spin, Tooltip, message } from 'antd';
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
    workflowTimeoutSeconds,
    failureStrategy,
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
  const [publishOpen, setPublishOpen] = useState(false);
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
  const lifecycleText = !hasPublished
    ? '尚未发布'
    : status === 'OFFLINE'
      ? `已停用 v${activeVersionNo}${draftChanged ? ' · 有草稿修改' : ''}`
      : `已发布 v${activeVersionNo}${draftChanged ? ' · 有草稿修改' : ''}`;
  const primaryPublishText = !hasPublished
    ? '发布 v1'
    : draftChanged
      ? `发布 v${nextVersionNo}`
      : status === 'OFFLINE'
        ? `重新启用 v${activeVersionNo}`
        : `v${activeVersionNo} 已发布`;

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

  const versionContent = useMemo(() => (
    <div className="w-[330px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]">
      <div className="flex h-11 items-center justify-between border-b border-[#f0f1f3] px-3.5">
        <div className="text-[13px] font-semibold text-[#344054]">发布版本</div>
        <span className="text-[10px] text-[#98a2b3]">{lifecycleText}</span>
      </div>
      <div className="max-h-[360px] overflow-auto p-2.5">
        {versionsLoading ? (
          <div className="flex h-24 items-center justify-center"><Spin size="small" /></div>
        ) : versions.length ? versions.map((version) => (
          <div key={version.id} className="mb-1.5 rounded-lg border border-[#eaecf0] bg-white px-3 py-2.5 last:mb-0">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-[12px] font-semibold text-[#344054]">
                <GitCommitHorizontal size={13} />
                v{version.versionNo}
                {version.active ? (
                  <span className="rounded bg-[#fff1f3] px-1.5 py-0.5 text-[9px] font-medium text-[#d92d50]">当前</span>
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
  ), [lifecycleText, versions, versionsLoading]);

  const runtimeSettingsContent = (
    <div className="w-[320px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]">
      <div className="border-b border-[#f0f1f3] px-4 py-3">
        <div className="text-[13px] font-semibold text-[#344054]">运行设置</div>
        <div className="mt-1 text-[10px] leading-4 text-[#98a2b3]">这些参数会进入发布版本和测试运行，不再作为隐藏配置生效。</div>
      </div>
      <div className="space-y-4 p-4">
        <div>
          <div className="mb-1.5 text-[11px] font-medium text-[#667085]">失败策略</div>
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
          <div className="mb-1.5 text-[11px] font-medium text-[#667085]">工作流整体超时</div>
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

  const canPublish = !hasPublished || draftChanged || status === 'OFFLINE';
  const publishContent = (
    <div className="w-[330px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]">
      <div className="px-4 pb-3 pt-4">
        <div className="text-[12px] text-[#667085]">{lifecycleText}</div>
        <div className="mt-1 text-[13px] font-semibold leading-5 text-[#344054]">
          {draftChanged
            ? `当前草稿将形成不可变的 v${nextVersionNo}，已有运行不会受影响`
            : status === 'OFFLINE'
              ? `重新启用已发布的 v${activeVersionNo}`
              : '当前草稿与已发布版本一致'}
        </div>
      </div>
      <div className="space-y-2 border-t border-[#f0f1f3] p-4">
        {canPublish ? (
          <Button
            block
            type="primary"
            disabled={testing}
            loading={statusAction || saving}
            icon={<Rocket size={14} />}
            onClick={() => { onOnline(); setPublishOpen(false); }}
            className="!h-9 !rounded-lg !font-medium"
          >
            {primaryPublishText}
          </Button>
        ) : null}
        <Button
          block
          disabled={testing}
          loading={saving}
          icon={<Save size={14} />}
          onClick={() => { onSave(); setPublishOpen(false); }}
          className="!h-9 !rounded-lg"
        >
          保存草稿
        </Button>
        {status === 'ONLINE' && hasPublished ? (
          <Button
            block
            disabled={testing}
            loading={statusAction}
            icon={<CircleStop size={14} />}
            onClick={() => { onOffline(); setPublishOpen(false); }}
            className="!h-9 !rounded-lg"
          >
            停用正式运行入口
          </Button>
        ) : null}
      </div>
    </div>
  );

  return (
    <header className="workflow-editor-toolbar flex h-[48px] shrink-0 items-center border-b border-[#ebecef] bg-white px-3">
      <span className="mr-auto inline-flex h-6 items-center rounded-[7px] border border-[rgba(22,24,35,.06)] bg-[#fafafa] px-2.5 text-[10px] font-medium text-[#667085]">
        {lifecycleText}
      </span>

      <div className="flex items-center gap-1.5">
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
              size="small"
              aria-label="运行设置"
              icon={<SlidersHorizontal size={14} />}
              className="relative !h-8 !rounded-lg !border-[#dfe2e7] !px-2.5 !text-[#667085] shadow-none"
            >
              {hasRuntimeOverrides ? <span className="absolute right-1 top-1 h-1.5 w-1.5 rounded-full bg-[#fe2c55]" /> : null}
            </Button>
          </Tooltip>
        </Popover>

        <Tooltip title="保存当前草稿并按草稿配置测试，不影响已发布版本">
          <Button
            size="small"
            loading={testing}
            disabled={!definition?.id || saving || statusAction}
            icon={<Play size={14} />}
            onClick={onTestRun}
            className="!h-8 !rounded-lg !border-[#dfe2e7] !px-3 !text-[12px] !font-medium !text-[#344054] shadow-none"
          >
            {testing ? '测试运行中' : '测试运行'}
          </Button>
        </Tooltip>

        <Popover
          open={publishOpen}
          onOpenChange={(open) => { if (!testing) setPublishOpen(open); }}
          trigger="click"
          placement="bottomRight"
          arrow={false}
          content={publishContent}
          overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
        >
          <Button
            type="primary"
            size="small"
            disabled={testing}
            icon={<Rocket size={14} />}
            className="!h-8 !rounded-lg !px-3.5 !text-[12px] !font-medium"
          >
            发布
            <ChevronDown size={13} className="ml-1" />
          </Button>
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
              size="small"
              aria-label="发布版本"
              icon={<History size={15} />}
              className="!h-8 !rounded-lg !border-[#dfe2e7] !px-2.5 !text-[#667085] shadow-none"
            />
          </Tooltip>
        </Popover>
      </div>
    </header>
  );
};

export default WorkflowToolbar;
