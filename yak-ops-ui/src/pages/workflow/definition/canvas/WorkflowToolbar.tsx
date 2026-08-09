import type { WorkflowFailureStrategy } from '@/services/workflow';
import {
  runWorkflowDefinition,
  type WorkflowDefinition,
} from '@/services/workflow/definitions';
import { Button, Popover, Tooltip, message } from 'antd';
import {
  ChevronDown,
  CircleStop,
  Clock3,
  History,
  Play,
  Rocket,
  Save,
} from 'lucide-react';
import { useMemo, useState } from 'react';

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ONLINE: '已发布',
  OFFLINE: '未发布',
};

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
  statusAction: boolean;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onWorkflowTimeoutChange: (value: number) => void;
  onFailureStrategyChange: (value: WorkflowFailureStrategy) => void;
  onClear: () => void;
  onSave: () => void;
  onOnline: () => void;
  onOffline: () => void;
}

const formatDateTime = (value?: string) => {
  if (!value) return '--';
  return value.replace('T', ' ').slice(0, 19);
};

const WorkflowToolbar = (props: WorkflowToolbarProps) => {
  const {
    definition,
    locked,
    saving,
    statusAction,
    onSave,
    onOnline,
    onOffline,
  } = props;
  const [testing, setTesting] = useState(false);
  const [publishOpen, setPublishOpen] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);

  const status = definition?.status || 'DRAFT';
  const statusLabel = STATUS_LABEL[status] || status;

  const versionContent = useMemo(() => (
    <div className="w-[300px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]">
      <div className="flex h-11 items-center justify-between border-b border-[#f0f1f3] px-3.5">
        <div className="text-[13px] font-semibold text-[#344054]">版本</div>
        <span className="rounded-md bg-[#f2f4f7] px-2 py-1 text-[10px] font-medium text-[#667085]">
          {statusLabel}
        </span>
      </div>
      <div className="p-3.5">
        <div className="rounded-lg border border-[#eaecf0] bg-[#fafafa] p-3">
          <div className="flex items-center gap-2 text-[12px] font-medium text-[#344054]">
            <Clock3 size={14} className="text-[#667085]" />
            当前工作流
          </div>
          <div className="mt-2 grid grid-cols-[72px_1fr] gap-y-1.5 text-[11px] leading-5">
            <span className="text-[#98a2b3]">状态</span>
            <span className="text-[#475467]">{statusLabel}</span>
            <span className="text-[#98a2b3]">最后更新</span>
            <span className="text-[#475467]">{formatDateTime(definition?.updateTime)}</span>
          </div>
        </div>
        <div className="mt-3 text-[10px] leading-[18px] text-[#98a2b3]">
          后续接入工作流版本快照后，这里将展示已发布版本和恢复记录。
        </div>
      </div>
    </div>
  ), [definition?.updateTime, statusLabel]);

  const publishContent = (
    <div className="w-[320px] overflow-hidden rounded-xl border border-[#e4e7ec] bg-white shadow-[0_12px_32px_rgba(22,24,35,.14)]">
      <div className="px-4 pb-3 pt-4">
        <div className="text-[12px] text-[#667085]">
          {status === 'ONLINE' ? '当前工作流已发布' : '当前草稿未发布'}
        </div>
        <div className="mt-1 text-[14px] font-semibold text-[#344054]">
          {status === 'ONLINE' ? '工作流正在使用已发布配置' : '发布后即可按当前配置运行工作流'}
        </div>
      </div>

      {status !== 'ONLINE' ? (
        <div className="space-y-2 border-t border-[#f0f1f3] p-4">
          <Button
            block
            type="primary"
            loading={statusAction || saving}
            icon={<Rocket size={14} />}
            onClick={() => {
              onOnline();
              setPublishOpen(false);
            }}
            className="!h-9 !rounded-lg !font-medium"
          >
            发布更新
          </Button>
          <Button
            block
            loading={saving}
            icon={<Save size={14} />}
            onClick={() => {
              onSave();
              setPublishOpen(false);
            }}
            className="!h-9 !rounded-lg"
          >
            仅保存草稿
          </Button>
        </div>
      ) : (
        <div className="border-t border-[#f0f1f3] p-4">
          <Button
            block
            loading={statusAction}
            icon={<CircleStop size={14} />}
            onClick={() => {
              onOffline();
              setPublishOpen(false);
            }}
            className="!h-9 !rounded-lg"
          >
            下线后继续编辑
          </Button>
        </div>
      )}
    </div>
  );

  const handleTestRun = async () => {
    if (!definition?.id || testing) return;
    setTesting(true);
    try {
      await runWorkflowDefinition(definition.id);
      message.success('测试运行已提交');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '测试运行失败');
    } finally {
      setTesting(false);
    }
  };

  return (
    <>
      <style>{`
        .workflow-editor-toolbar + div::before {
          position: absolute;
          top: 12px;
          left: 14px;
          z-index: 8;
          display: inline-flex;
          align-items: center;
          height: 24px;
          padding: 0 9px;
          border: 1px solid rgba(22, 24, 35, .06);
          border-radius: 7px;
          background: rgba(255, 255, 255, .86);
          box-shadow: 0 2px 8px rgba(22, 24, 35, .05);
          backdrop-filter: blur(8px);
          color: #667085;
          font-size: 10px;
          font-weight: 500;
          line-height: 24px;
          pointer-events: none;
        }
        .workflow-editor-toolbar[data-workflow-status='DRAFT'] + div::before {
          content: '●  草稿';
        }
        .workflow-editor-toolbar[data-workflow-status='OFFLINE'] + div::before {
          content: '●  未发布';
        }
        .workflow-editor-toolbar[data-workflow-status='ONLINE'] + div::before {
          content: '●  已发布';
          color: #d92d50;
        }
      `}</style>
      <header
        data-workflow-status={status}
        className="workflow-editor-toolbar flex h-[48px] shrink-0 items-center justify-end border-b border-[#ebecef] bg-white px-3"
      >
        <div className="flex items-center gap-1.5">
          <Tooltip title="运行最近保存的工作流配置">
            <Button
              size="small"
              loading={testing}
              disabled={!definition?.id}
              icon={<Play size={14} />}
              onClick={() => void handleTestRun()}
              className="!h-8 !rounded-lg !border-[#dfe2e7] !px-3 !text-[12px] !font-medium !text-[#344054] shadow-none"
            >
              测试运行
            </Button>
          </Tooltip>

          <Popover
            open={publishOpen}
            onOpenChange={setPublishOpen}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={publishContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Button
              type="primary"
              size="small"
              icon={<Rocket size={14} />}
              className="!h-8 !rounded-lg !px-3.5 !text-[12px] !font-medium"
            >
              发布
              <ChevronDown size={13} className="ml-1" />
            </Button>
          </Popover>

          <Popover
            open={versionOpen}
            onOpenChange={setVersionOpen}
            trigger="click"
            placement="bottomRight"
            arrow={false}
            content={versionContent}
            overlayInnerStyle={{ padding: 0, background: 'transparent', boxShadow: 'none' }}
          >
            <Tooltip title="版本">
              <Button
                size="small"
                aria-label="版本"
                icon={<History size={15} />}
                className="!h-8 !rounded-lg !border-[#dfe2e7] !px-2.5 !text-[#667085] shadow-none"
              />
            </Tooltip>
          </Popover>
        </div>
      </header>
    </>
  );
};

export default WorkflowToolbar;
