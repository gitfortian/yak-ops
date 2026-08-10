import { FolderTree } from 'lucide-react';
import type { ReactNode } from 'react';

interface YakEmptyProps {
  title?: ReactNode;
  description?: ReactNode;
  compact?: boolean;
  className?: string;
}

const YakEmpty = ({
  title = '暂无数据',
  description,
  compact = false,
  className = '',
}: YakEmptyProps) => (
  <div
    className={[
      'flex w-full flex-col items-center justify-center px-5 text-center',
      compact ? 'min-h-[180px] py-8' : 'min-h-[320px] py-12',
      className,
    ].join(' ')}
  >
    <div
      className={[
        'flex items-center justify-center rounded-xl bg-[#f6f7f8] text-[#c4c9d1]',
        compact ? 'h-10 w-10' : 'h-12 w-12',
      ].join(' ')}
    >
      <FolderTree size={compact ? 20 : 24} strokeWidth={1.4} />
    </div>
    <div className="mt-3 text-[13px] font-medium text-[#667085]">{title}</div>
    {description ? (
      <div className="mt-1 max-w-[240px] text-[11px] leading-5 text-[#98a2b3]">
        {description}
      </div>
    ) : null}
  </div>
);

export default YakEmpty;
