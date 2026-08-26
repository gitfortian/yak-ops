import { ChevronRight } from 'lucide-react';

import type { HomeAssetOverview } from './service';

export interface HomeAssetOverviewState {
  data?: HomeAssetOverview;
  loading: boolean;
  failed: boolean;
}

interface SectionHeaderProps {
  title: string;
  description?: string;
  onMore?: () => void;
}

const COUNT_FORMATTER = new Intl.NumberFormat('zh-CN');

export const formatMetric = (value?: number | null) =>
  value == null ? '--' : COUNT_FORMATTER.format(value);

export const compactName = (value: string) =>
  value.length > 16 ? `${value.slice(0, 13)}...` : value;

export const relativeTime = (value?: string | null) => {
  if (!value) return '--';
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return value;

  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;
  return new Date(timestamp).toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  });
};

export const relationTypeLabel = (relationType?: string) => {
  const labels: Record<string, string> = {
    READS_FROM: '读取',
    WRITES_TO: '写入',
    DERIVES_FROM: '派生',
    CONSUMES: '消费',
    CONTAINS: '包含',
  };
  return labels[relationType?.toUpperCase() || ''] || relationType || '关系';
};

export const assetTypeColor = (assetType?: string) => {
  const colors: Record<string, string> = {
    TABLE: '#6f83d9',
    COLUMN: '#8ca0d9',
    SQL_TASK: '#cf7e6b',
    DATASET: '#5b9b83',
    DATASET_FIELD: '#76aa98',
    CHART: '#d29359',
    DASHBOARD: '#8a72c7',
  };
  return colors[assetType?.toUpperCase() || ''] || '#87909d';
};

export function SectionHeader({
  title,
  description,
  onMore,
}: SectionHeaderProps) {
  return (
    <header className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {title}
        </h2>
        {description ? (
          <p className="mt-1 text-[12px] leading-5 text-[#92969f]">
            {description}
          </p>
        ) : null}
      </div>

      {onMore ? (
        <button
          type="button"
          onClick={onMore}
          className="mt-0.5 flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#747982] transition-colors hover:text-[#252832]"
        >
          查看更多
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      ) : null}
    </header>
  );
}

export function EmptyList({
  loading,
  failed,
  unavailable,
  text,
}: {
  loading: boolean;
  failed: boolean;
  unavailable: boolean;
  text: string;
}) {
  return (
    <div className="flex min-h-[214px] items-center justify-center text-[11px] text-[#a0a4ac]">
      {loading
        ? '数据加载中...'
        : failed
          ? '数据加载失败'
          : unavailable
            ? '数据暂不可用'
            : text}
    </div>
  );
}
