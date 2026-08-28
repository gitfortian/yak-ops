import type {
  HomeAttentionItem,
  HomeAttentionSeverity,
  HomeAttentionSummary,
} from '@/services/home';
import { history } from '@umijs/max';
import {
  AlertCircle,
  CheckCircle2,
  ChevronRight,
  Info,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';

import { getHomeAttentionRoute } from '../config/cockpit';

interface AttentionCenterProps {
  attention?: HomeAttentionSummary;
  loading: boolean;
  failed: boolean;
}

const SEVERITY_META: Record<
  HomeAttentionSeverity,
  { icon: LucideIcon; iconClass: string; countClass: string }
> = {
  CRITICAL: {
    icon: AlertCircle,
    iconClass: 'bg-[#fff0f3] text-[#d94b67]',
    countClass: 'text-[#cf4862]',
  },
  WARNING: {
    icon: TriangleAlert,
    iconClass: 'bg-[#fff7e9] text-[#b47a2b]',
    countClass: 'text-[#a86f25]',
  },
  INFO: {
    icon: Info,
    iconClass: 'bg-[#eef4ff] text-[#5d79b8]',
    countClass: 'text-[#5871aa]',
  },
};

function AttentionCard({ item }: { item: HomeAttentionItem }) {
  const meta = SEVERITY_META[item.severity] || SEVERITY_META.INFO;
  const Icon = meta.icon;

  return (
    <button
      type="button"
      onClick={() => history.push(getHomeAttentionRoute(item.key))}
      className="group flex min-w-0 items-center gap-3 rounded-[14px] border border-[#eceef2] bg-white px-3.5 py-3 text-left transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-px hover:border-[#dfe2e8] hover:shadow-[0_6px_18px_rgba(31,35,41,0.05)]"
    >
      <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[9px] ${meta.iconClass}`}>
        <Icon size={15} strokeWidth={1.9} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline gap-2">
          <strong className="truncate text-[11px] font-semibold text-[#444851]">
            {item.title}
          </strong>
          <strong className={`shrink-0 text-[13px] font-semibold ${meta.countClass}`}>
            {item.count}
          </strong>
        </span>
        <span className="mt-0.5 block truncate text-[9px] text-[#9ba0a8]">
          {item.description}
        </span>
      </span>
      <ChevronRight
        size={13}
        strokeWidth={1.8}
        className="shrink-0 text-[#b5b9c0] transition-transform group-hover:translate-x-0.5"
      />
    </button>
  );
}

export default function AttentionCenter({
  attention,
  loading,
  failed,
}: AttentionCenterProps) {
  const items = attention?.items || [];

  return (
    <section
      id="home-attention-center"
      className="mt-3 rounded-[20px] border border-[#f0f1f3] bg-white px-5 py-4"
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <strong className="text-[13px] font-semibold text-[#373b44]">待处理</strong>
          {!loading && !failed ? (
            <span className="rounded-full bg-[#f3f4f6] px-2 py-0.5 text-[9px] font-medium text-[#858a94]">
              {attention?.total || 0}
            </span>
          ) : null}
        </div>
        <span className="text-[9px] text-[#a0a4ac]">按影响程度排序</span>
      </div>

      {loading ? (
        <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <div
              key={index}
              className="h-[58px] animate-pulse rounded-[14px] border border-[#f0f1f3] bg-[#fafbfc]"
            />
          ))}
        </div>
      ) : failed ? (
        <div className="mt-3 flex h-[58px] items-center justify-center rounded-[14px] bg-[#fafbfc] text-[10px] text-[#a0a4ac]">
          待处理摘要暂不可用
        </div>
      ) : items.length > 0 ? (
        <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2 xl:grid-cols-4">
          {items.map((item) => (
            <AttentionCard key={item.key} item={item} />
          ))}
        </div>
      ) : (
        <div className="mt-3 flex h-[58px] items-center gap-3 rounded-[14px] border border-[#edf2ef] bg-[#f8fbf9] px-4">
          <span className="flex h-8 w-8 items-center justify-center rounded-[9px] bg-[#eef8f2] text-[#4f936a]">
            <CheckCircle2 size={16} strokeWidth={1.9} />
          </span>
          <div>
            <strong className="block text-[11px] font-semibold text-[#4c6657]">
              当前无待处理事项
            </strong>
            <span className="mt-0.5 block text-[9px] text-[#86a08f]">
              数据源连接、运行任务和质量检查均未发现需要立即关注的问题
            </span>
          </div>
        </div>
      )}
    </section>
  );
}
