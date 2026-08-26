import { YakButton } from '@/components/ui';
import type {
  ComputeEnvironmentOption,
  RealtimeAction,
  RealtimeJob,
} from '@/services/realtime-sync';
import { MoreOutlined } from '@ant-design/icons';
import { Dropdown, Modal, Tooltip, message } from 'antd';
import type { MenuProps } from 'antd';

import {
  getRealtimeStartAvailability,
  isRealtimeReconciliationState,
  isRealtimeStableRunning,
} from '../utils';

interface RealtimeSyncActionColumnProps {
  job: RealtimeJob;
  environment?: ComputeEnvironmentOption;
  onEdit: (job: RealtimeJob) => void;
  onDetail: (job: RealtimeJob) => void;
  onDelete: (job: RealtimeJob) => Promise<void>;
  onAction: (job: RealtimeJob, action: RealtimeAction) => Promise<void>;
}

const RealtimeSyncActionColumn = ({
  job,
  environment,
  onEdit,
  onDetail,
  onDelete,
  onAction,
}: RealtimeSyncActionColumnProps) => {
  const running = job.desiredState === 'RUNNING';
  const stableRunning = isRealtimeStableRunning(job);
  const startAvailability = getRealtimeStartAvailability(job, environment);

  const confirmDelete = () => {
    Modal.confirm({
      title: '删除实时同步任务',
      content: `确认删除“${job.name}”？该操作仅允许已停止任务执行。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await onDelete(job);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除失败');
          throw error;
        }
      },
    });
  };

  const moreItems: MenuProps['items'] = [
    { key: 'detail', label: '查看运行详情' },
    {
      key: 'validate',
      label: 'Flink CDC 校验',
      disabled: job.releaseState === 'PUBLISHED',
    },
    {
      key: 'publish',
      label: running ? '发布当前版本（不影响运行）' : '发布当前版本',
      disabled: job.releaseState === 'PUBLISHED',
    },
    {
      key: 'restart-execution',
      label: '重启当前版本',
      disabled: !stableRunning,
    },
    ...(job.publishedUpdateAvailable
      ? [
          {
            key: 'apply-published-version',
            label: '应用已发布版本',
            disabled: !stableRunning,
          },
        ]
      : []),
    {
      key: 'reconcile',
      label: '立即状态对账',
      disabled: !isRealtimeReconciliationState(job.observedState),
    },
    { type: 'divider' },
    {
      key: 'delete',
      label: <span className="text-[#d92d20]">删除任务</span>,
      disabled: job.desiredState !== 'STOPPED',
    },
  ];

  const handleMoreAction: MenuProps['onClick'] = ({ key, domEvent }) => {
    domEvent.stopPropagation();
    if (key === 'detail') {
      onDetail(job);
      return;
    }
    if (key === 'delete') {
      confirmDelete();
      return;
    }
    void onAction(job, key as RealtimeAction);
  };

  return (
    <div className="flex min-h-7 items-center gap-0.5 whitespace-nowrap">
      <Tooltip
        title={
          running
            ? '运行中编辑只修改草稿，不影响当前 SyncExecution'
            : undefined
        }
      >
        <YakButton
          type="text"
          size="small"
          className="!h-7 !px-1.5 !text-[12px]"
          onClick={() => onEdit(job)}
        >
          编辑
        </YakButton>
      </Tooltip>

      {running ? (
        <YakButton
          type="text"
          danger
          size="small"
          className="!h-7 !px-1.5 !text-[12px]"
          onClick={() => void onAction(job, 'stop')}
        >
          停止
        </YakButton>
      ) : (
        <Tooltip title={startAvailability.tooltip}>
          <span>
            <YakButton
              type="text"
              danger
              size="small"
              className="!h-7 !px-1.5 !text-[12px]"
              disabled={startAvailability.disabled}
              onClick={() => void onAction(job, 'start')}
            >
              启动
            </YakButton>
          </span>
        </Tooltip>
      )}

      <Dropdown
        trigger={['click']}
        menu={{ items: moreItems, onClick: handleMoreAction }}
      >
        <YakButton
          type="text"
          size="small"
          iconOnly
          icon={<MoreOutlined />}
          className="!h-7 !w-7 !min-w-0 !p-0 !text-[#667085] hover:!bg-[#f2f4f7]"
        />
      </Dropdown>
    </div>
  );
};

export default RealtimeSyncActionColumn;
