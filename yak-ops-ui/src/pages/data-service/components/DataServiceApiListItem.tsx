import type { DataServiceApi } from '@/services/data-service';

import DataServiceMethodBadge from './DataServiceMethodBadge';

interface DataServiceApiListItemProps {
  service: DataServiceApi;
  dataSourceName: string;
  calls?: number;
  rank?: number;
  onOpen: () => void;
}

const DataServiceApiListItem = ({
  service,
  dataSourceName,
  calls,
  rank,
  onOpen,
}: DataServiceApiListItemProps) => (
  <button
    type="button"
    onClick={onOpen}
    className="group flex min-h-[82px] w-full items-center gap-3 rounded-lg border-0 bg-[#fafbfc] px-3 py-3 text-left transition-colors hover:bg-[#f5f6f7]"
  >
    {rank ? (
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white font-mono text-[10px] font-medium text-[#98a2b3] shadow-[0_0_0_1px_rgba(16,24,40,.04)]">
        {String(rank).padStart(2, '0')}
      </span>
    ) : null}

    <div className="min-w-0 flex-1">
      <div className="flex items-center gap-2">
        <span className="truncate text-[13px] font-medium text-[#161823]">
          {service.name}
        </span>
        <DataServiceMethodBadge />
        {!service.enabled ? (
          <span className="text-[10px] text-[#98a2b3]">已停用</span>
        ) : null}
      </div>
      <div className="mt-1 truncate text-[11px] leading-5 text-[#8a9099]">
        {service.description || '暂无描述'}
      </div>
      <div
        className="mt-0.5 truncate font-mono text-[10px] text-[#a3a8b0]"
        title={service.runtimePath}
      >
        {service.runtimePath}
      </div>
    </div>

    <div
      className={
        rank
          ? 'w-[72px] shrink-0 text-right'
          : 'w-[118px] shrink-0 text-right'
      }
    >
      <div className="truncate text-[11px] font-medium text-[#667085]">
        {calls !== undefined ? `${calls} 次` : dataSourceName}
      </div>
      <div className="mt-1 text-[10px] text-[#b0b5bd]">
        {calls !== undefined
          ? '近期调用'
          : service.enabled
            ? '运行中'
            : '已停用'}
      </div>
    </div>
  </button>
);

export default DataServiceApiListItem;
