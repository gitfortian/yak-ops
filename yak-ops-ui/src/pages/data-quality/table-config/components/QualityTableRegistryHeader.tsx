import { YakButton } from '@/components/ui';
import { RefreshCw } from 'lucide-react';

interface QualityTableRegistryHeaderProps {
  refreshing: boolean;
  onRefresh: () => void;
}

const QualityTableRegistryHeader = ({
  refreshing,
  onRefresh,
}: QualityTableRegistryHeaderProps) => (
  <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e8e9ec] px-5">
    <h1 className="m-0 text-[20px] font-semibold text-[#161823]">
      数据表监控
    </h1>
    <YakButton
      icon={<RefreshCw size={14} />}
      loading={refreshing}
      onClick={onRefresh}
    >
      刷新
    </YakButton>
  </header>
);

export default QualityTableRegistryHeader;
