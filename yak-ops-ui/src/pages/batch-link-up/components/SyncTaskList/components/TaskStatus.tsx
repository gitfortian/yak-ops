import {
  YakButton,
  YakStatusIcon,
  type YakStatus,
} from '@/components/ui';
import { Popover, message } from 'antd';

interface TaskStatusProps {
  status?: string;
  errorMessage?: string;
}

interface StatusMeta {
  label: string;
  yakStatus: YakStatus;
  animated?: boolean;
}

const STATUS_META: Record<string, StatusMeta> = {
  IDLE: {
    label: '未运行',
    yakStatus: 'pending',
  },
  CREATED: {
    label: '已创建',
    yakStatus: 'pending',
  },
  SUBMITTED: {
    label: '提交中',
    yakStatus: 'pending',
    animated: true,
  },
  QUEUED: {
    label: '排队中',
    yakStatus: 'pending',
    animated: true,
  },
  RUNNING: {
    label: '运行中',
    yakStatus: 'running',
    animated: true,
  },
  SUCCEEDED: {
    label: '已完成',
    yakStatus: 'success',
  },
  FAILED: {
    label: '失败',
    yakStatus: 'failed',
  },
  PAUSED: {
    label: '已暂停',
    yakStatus: 'paused',
  },
  CANCELED: {
    label: '已取消',
    yakStatus: 'canceled',
  },
  LOST: {
    label: '状态丢失',
    yakStatus: 'warning',
  },
};

const STATUS_ALIASES: Record<string, string> = {
  FINISHED: 'SUCCEEDED',
  COMPLETED: 'SUCCEEDED',
  SUCCESS: 'SUCCEEDED',
  CANCELLED: 'CANCELED',
  STOPPED: 'CANCELED',
  PENDING: 'QUEUED',
  WAITING: 'QUEUED',
  NOT_STARTED: 'IDLE',
  NONE: 'IDLE',
};

const normalizeStatus = (value?: string) => {
  const normalized = String(value || '')
    .trim()
    .toUpperCase();
  return STATUS_ALIASES[normalized] || normalized || 'IDLE';
};

const TaskStatus = ({ status, errorMessage }: TaskStatusProps) => {
  const normalized = normalizeStatus(status);
  const meta: StatusMeta = STATUS_META[normalized] || {
    label: normalized,
    yakStatus: 'unknown',
  };

  const statusContent = (
    <span
      className="inline-flex min-w-[78px] items-center justify-center gap-1.5 whitespace-nowrap text-[12px] font-medium leading-5 text-[#475467]"
      data-offline-sync-status={normalized}
    >
      <YakStatusIcon
        status={meta.yakStatus}
        size={17}
        animated={Boolean(meta.animated)}
      />
      <span>{meta.label}</span>
    </span>
  );

  if (!errorMessage || (normalized !== 'FAILED' && normalized !== 'LOST')) {
    return statusContent;
  }

  const copyError = async () => {
    try {
      await navigator.clipboard.writeText(errorMessage);
      message.success('错误信息已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  return (
    <Popover
      placement="right"
      trigger="hover"
      content={
        <div className="w-[440px]">
          <div className="max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#101828] p-3 font-mono text-xs leading-5 text-[#fda29b]">
            {errorMessage}
          </div>
          <div className="mt-2 flex justify-end">
            <YakButton size="small" onClick={copyError}>
              复制错误
            </YakButton>
          </div>
        </div>
      }
    >
      <span className="inline-flex cursor-help rounded-md transition-colors hover:bg-[#fff7f6]">
        {statusContent}
      </span>
    </Popover>
  );
};

export default TaskStatus;
