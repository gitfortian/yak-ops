import { YakButton } from '@/components/ui';
import { Plus } from 'lucide-react';

interface OfflineSyncPageHeaderProps {
  onCreate: () => void;
}

const OfflineSyncPageHeader = ({ onCreate }: OfflineSyncPageHeaderProps) => (
  <div>
    <div className="flex min-h-10 items-start justify-between gap-6">
      <div>
        <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
          离线同步
        </h1>
        <div className="mt-1 text-[12px] text-[#98a2b3]">
          管理离线同步任务、发布状态、运行实例和调度信息
        </div>
      </div>

      <YakButton
        type="primary"
        size="small"
        icon={<Plus size={14} strokeWidth={2} />}
        className="!h-8 !px-3.5"
        onClick={onCreate}
      >
        新建同步任务
      </YakButton>
    </div>

    <div className="mt-3 flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
      <span className="mr-2 text-[14px] text-[#faad14]">▲</span>
      <span className="font-medium text-[#344054]">【提示】</span>
      <span>
        任务交互已增加“发布”动作；启动任务前，请先确认任务已经上线。
      </span>
    </div>
  </div>
);

export default OfflineSyncPageHeader;
