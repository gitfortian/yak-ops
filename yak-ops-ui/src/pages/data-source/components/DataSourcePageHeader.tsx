import { YakButton } from '@/components/ui';
import { Database, Plus } from 'lucide-react';

interface DataSourcePageHeaderProps {
  canCreate: boolean;
  onCreate: () => void;
}

const DataSourcePageHeader = ({
  canCreate,
  onCreate,
}: DataSourcePageHeaderProps) => (
  <header className="flex items-center justify-between gap-8 max-md:items-start">
    <div className="flex min-w-0 items-center gap-3.5">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[13px] border border-[rgba(31,35,41,0.07)] bg-[linear-gradient(145deg,#f7f9fc_0%,#eef2f7_100%)] text-[#4f5968] shadow-[0_4px_12px_rgba(31,35,41,0.04)]">
        <Database size={21} strokeWidth={1.8} />
      </span>

      <div className="min-w-0">
        <h1 className="m-0 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
          数据源管理
        </h1>
        <p className="mb-0 mt-1 text-[13px] leading-5 text-[#9498a1]">
          统一管理开发、测试与生产环境的数据连接
        </p>
      </div>
    </div>

    {canCreate ? (
      <YakButton
        type="primary"
        icon={<Plus size={16} strokeWidth={2.1} />}
        className="!h-9 !shrink-0 !rounded-[10px] !px-4 !text-[13px]"
        onClick={onCreate}
      >
        新建数据源
      </YakButton>
    ) : null}
  </header>
);

export default DataSourcePageHeader;
