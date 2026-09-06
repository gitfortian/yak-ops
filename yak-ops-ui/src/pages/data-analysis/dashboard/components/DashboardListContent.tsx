import type { DashboardSummary } from '@/services/dashboard';

import DashboardEmptyState from './DashboardEmptyState';
import DashboardListItem from './DashboardListItem';
import DashboardPagination from './DashboardPagination';

interface DashboardListContentProps {
  loading: boolean;
  totalDashboards: number;
  filteredTotal: number;
  pageItems: DashboardSummary[];
  currentPage: number;
  pageSize: number;
  filtered: boolean;
  deletingId?: string;
  onReset: () => void;
  onCreate: () => void;
  onOpen: (dashboard: DashboardSummary) => void;
  onEdit: (dashboard: DashboardSummary) => void;
  onRename: (dashboard: DashboardSummary) => void;
  onDelete: (dashboard: DashboardSummary) => Promise<void>;
  onPageChange: (page: number, pageSize: number) => void;
}

const DashboardListContent = ({
  loading,
  totalDashboards,
  filteredTotal,
  pageItems,
  currentPage,
  pageSize,
  filtered,
  deletingId,
  onReset,
  onCreate,
  onOpen,
  onEdit,
  onRename,
  onDelete,
  onPageChange,
}: DashboardListContentProps) => {
  if (loading && totalDashboards === 0) {
    return (
      <div className="flex min-h-[420px] items-center justify-center text-[13px] text-[#98a2b3]">
        正在加载仪表盘...
      </div>
    );
  }

  if (pageItems.length === 0) {
    return (
      <DashboardEmptyState
        filtered={filtered}
        onReset={onReset}
        onCreate={onCreate}
      />
    );
  }

  return (
    <>
      <section>
        {pageItems.map((dashboard) => (
          <DashboardListItem
            key={dashboard.id}
            dashboard={dashboard}
            deleting={deletingId === dashboard.id}
            onOpen={onOpen}
            onEdit={onEdit}
            onRename={onRename}
            onDelete={onDelete}
          />
        ))}
      </section>

      <DashboardPagination
        total={filteredTotal}
        current={currentPage}
        pageSize={pageSize}
        onChange={onPageChange}
      />
    </>
  );
};

export default DashboardListContent;
