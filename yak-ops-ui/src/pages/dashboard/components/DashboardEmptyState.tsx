import { YakButton, YakEmpty } from '@/components/ui';
import { LayoutDashboard, Plus } from 'lucide-react';

interface DashboardEmptyStateProps {
  filtered: boolean;
  onReset: () => void;
  onCreate: () => void;
}

const DashboardEmptyState = ({
  filtered,
  onReset,
  onCreate,
}: DashboardEmptyStateProps) => (
  <div className="flex min-h-[420px] items-center justify-center pb-10">
    <div className="flex flex-col items-center text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-[16px] bg-[#f5f6f7] text-[#98a2b3]">
        <LayoutDashboard size={25} strokeWidth={1.6} />
      </div>

      <YakEmpty
        compact
        className="!min-h-0 !px-0 !pb-3 !pt-3 [&>div:first-child]:hidden"
        title={filtered ? '没有匹配的仪表盘' : '还没有仪表盘'}
        description={
          filtered
            ? '换个关键词或清空筛选条件再试试。'
            : '把指标、图表和趋势放在同一个画布里，创建属于你的数据视图。'
        }
      />

      {filtered ? (
        <YakButton size="small" onClick={onReset}>
          清空筛选
        </YakButton>
      ) : (
        <YakButton
          type="primary"
          size="small"
          icon={<Plus size={14} />}
          onClick={onCreate}
        >
          新建仪表盘
        </YakButton>
      )}
    </div>
  </div>
);

export default DashboardEmptyState;
