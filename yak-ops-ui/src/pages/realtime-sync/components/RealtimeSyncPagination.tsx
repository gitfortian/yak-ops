import { Pagination, Select } from 'antd';

import { REALTIME_SYNC_PAGE_SIZE_OPTIONS } from '../constants';

interface RealtimeSyncPaginationProps {
  total: number;
  current: number;
  pageSize: number;
  onChange: (page: number, pageSize: number) => void;
}

const RealtimeSyncPagination = ({
  total,
  current,
  pageSize,
  onChange,
}: RealtimeSyncPaginationProps) => (
  <div className="flex items-center gap-3 text-[13px] text-[#667085]">
    <Pagination
      size="small"
      total={total}
      current={current}
      pageSize={pageSize}
      showSizeChanger={false}
      showQuickJumper={false}
      onChange={(page) => onChange(page, pageSize)}
    />

    <span className="whitespace-nowrap">每页显示：</span>
    <Select
      size="small"
      value={pageSize}
      options={REALTIME_SYNC_PAGE_SIZE_OPTIONS.map((value) => ({
        label: value,
        value,
      }))}
      onChange={(nextPageSize) => onChange(1, nextPageSize)}
      className="w-[64px]"
    />
    <span className="whitespace-nowrap text-[#344054]">总 {total} 行</span>
  </div>
);

export default RealtimeSyncPagination;
