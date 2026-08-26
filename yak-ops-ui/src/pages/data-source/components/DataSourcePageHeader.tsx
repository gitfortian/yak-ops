import { YakButton } from '@/components/ui';
import { Plus } from 'lucide-react';

interface DataSourcePageHeaderProps {
  canCreate: boolean;
  onCreate: () => void;
}

const DataSourcePageHeader = ({
  canCreate,
  onCreate,
}: DataSourcePageHeaderProps) => (
  <header className="flex items-start justify-between gap-8">
    <h1 className="m-0 text-2xl font-bold tracking-[-0.45px] text-[#161823]">
      数据源管理
    </h1>

    {canCreate ? (
      <YakButton
        type="primary"
        size="large"
        icon={<Plus size={16} strokeWidth={2.1} />}
        className="!h-10 !shrink-0 !rounded-[7px] !px-[17px]"
        onClick={onCreate}
      >
        新建数据源
      </YakButton>
    ) : null}
  </header>
);

export default DataSourcePageHeader;
