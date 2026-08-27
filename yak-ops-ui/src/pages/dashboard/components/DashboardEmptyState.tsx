import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import { Plus } from 'lucide-react';

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
      <YakOpsEmpty
        width={180}
        height={120}
        title={filtered ? '没有匹配的仪表盘' : '还没有仪表盘'}
        description={
          filtered
            ? '换个关键词或清空筛选条件再试试。'
            : '创建仪表盘后，可在这里统一查看指标、图表和趋势。'
        }
        showCaption
      />

      <div className="mt-3">
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
  </div>
);

export default DashboardEmptyState;
