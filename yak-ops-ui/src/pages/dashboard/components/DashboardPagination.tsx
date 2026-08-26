import { Pagination } from 'antd';

import { DASHBOARD_PAGE_SIZE_OPTIONS } from '../constants';

interface DashboardPaginationProps {
  total: number;
  current: number;
  pageSize: number;
  onChange: (page: number, pageSize: number) => void;
}

const DashboardPagination = ({
  total,
  current,
  pageSize,
  onChange,
}: DashboardPaginationProps) => {
  if (total <= pageSize) {
    return (
      <div className="py-10 text-center text-[13px] text-[#8a9099]">
        没有更多仪表盘
      </div>
    );
  }

  return (
    <div className="flex items-center justify-between border-t border-[#f0f1f2] py-4">
      <span className="text-[12px] text-[#98a2b3]">共 {total} 个仪表盘</span>
      <Pagination
        size="small"
        current={current}
        pageSize={pageSize}
        total={total}
        showSizeChanger
        pageSizeOptions={DASHBOARD_PAGE_SIZE_OPTIONS}
        onChange={onChange}
      />
    </div>
  );
};

export default DashboardPagination;
