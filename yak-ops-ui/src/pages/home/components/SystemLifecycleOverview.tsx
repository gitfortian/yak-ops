import type {
  HomeLifecycleStage,
  HomeLifecycleStatus,
} from '@/services/home';
import { history } from '@umijs/max';
import {
  Activity,
  Boxes,
  Braces,
  ChevronRight,
  Database,
  GitBranch,
  LayoutDashboard,
  Network,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react';

import { getHomeLifecycleRoute } from '../config/cockpit';

interface SystemLifecycleOverviewProps {
  stages?: HomeLifecycleStage[];
  loading: boolean;
  failed: boolean;
}

const ICONS: Record<string, LucideIcon> = {
  'data-source': Database,
  integration: Network,
  development: Braces,
  workflow: GitBranch,
  quality: ShieldCheck,
  asset: Boxes,
  service: Activity,
  consumption: LayoutDashboard,
};

const STATUS_META: Record<
  HomeLifecycleStatus,
  { label: string; dot: string; badge: string }
> = {
  READY: {
    label: '正常',
    dot: 'bg-[#57a773]',
    badge: 'bg-[#eef8f2] text-[#43815f]',
  },
  ATTENTION: {
    label: '需关注',
    dot: 'bg-[#fe2c55]',
    badge: 'bg-[#fff0f3] text-[#c7445e]',
  },
  EMPTY: {
    label: '未配置',
    dot: 'bg-[#b0b4bc]',
    badge: 'bg-[#f4f5f6] text-[#818690]',
  },
  UNAVAILABLE: {
    label: '暂不可用',
    dot: 'bg-[#c9ccd2]',
    badge: 'bg-[#f5f5f6] text-[#999da5]',
  },
};

function LifecycleSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-2 md:grid-cols-4 xl:grid-cols-8">
      {Array.from({ length: 8 }).map((_, index) => (
        <div
          key={index}
          className="h-[116px] animate-pulse rounded-[16px] border border-[#f0f1f3] bg-[#fafbfc]"
        />
      ))}
    </div>
  );
}

function LifecycleStageCard({ stage, index }: { stage: HomeLifecycleStage; index: number }) {
  const Icon = ICONS[stage.key] || Boxes;
  const meta = STATUS_META[stage.status] || STATUS_META.UNAVAILABLE;
  const value = stage.value == null ? '--' : stage.value.toLocaleString('zh-CN');

  return (
    <div className="relative min-w-0">
      <button
        type="button"
        onClick={() => history.push(getHomeLifecycleRoute(stage.key))}
        className="group relative z-10 flex h-full min-h-[116px] w-full flex-col rounded-[16px] border border-[#eceef2] bg-white px-3.5 py-3.5 text-left transition-[transform,box-shadow,border-color] duration-200 hover:-translate-y-0.5 hover:border-[#dfe2e8] hover:shadow-[0_8px_24px_rgba(31,35,41,0.065)]"
      >
        <div className="flex items-start justify-between gap-2">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[9px] bg-[#f4f6f9] text-[#626a78] transition-colors group-hover:bg-[#eef1f6] group-hover:text-[#333842]">
            <Icon size={16} strokeWidth={1.8} />
          </span>
          <span className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-[9px] ${meta.badge}`}>
            <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
            {meta.label}
          </span>
        </div>

        <div className="mt-3 min-w-0">
          <div className="flex items-baseline gap-1.5">
            <strong className="truncate text-[13px] font-semibold text-[#343841]">
              {stage.title}
            </strong>
            {stage.issueCount > 0 ? (
              <span className="shrink-0 text-[9px] font-medium text-[#d64c67]">
                {stage.issueCount} 异常
              </span>
            ) : null}
          </div>
          <div className="mt-1 flex items-baseline gap-1">
            <strong className="text-[18px] font-semibold tracking-[-0.4px] text-[#30343d]">
              {value}
            </strong>
            <span className="truncate text-[9px] text-[#9ca0a8]">{stage.valueLabel}</span>
          </div>
          <p className="mt-1 truncate text-[9px] text-[#a0a4ac]">{stage.description}</p>
        </div>
      </button>

      {index < 7 ? (
        <span className="pointer-events-none absolute -right-[7px] top-1/2 z-20 hidden -translate-y-1/2 items-center justify-center rounded-full bg-[#f7f8fa] text-[#c1c5cc] xl:flex">
          <ChevronRight size={13} strokeWidth={1.8} />
        </span>
      ) : null}
    </div>
  );
}

export default function SystemLifecycleOverview({
  stages,
  loading,
  failed,
}: SystemLifecycleOverviewProps) {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-5 pb-5 pt-[18px]">
      <div className="flex items-end justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <strong className="text-[14px] font-semibold text-[#333740]">数据生命周期</strong>
            <span className="rounded-full bg-[#f3f4f6] px-2 py-0.5 text-[9px] text-[#8d929b]">
              SYSTEM MAP
            </span>
          </div>
          <p className="mt-1 text-[10px] text-[#979ba4]">
            从数据接入、开发与治理，到服务发布和最终消费
          </p>
        </div>
        <div className="hidden items-center gap-1.5 text-[9px] text-[#a2a6ae] sm:flex">
          <span className="h-1.5 w-1.5 rounded-full bg-[#57a773]" /> 正常
          <span className="ml-2 h-1.5 w-1.5 rounded-full bg-[#fe2c55]" /> 需关注
        </div>
      </div>

      <div className="mt-4">
        {loading ? (
          <LifecycleSkeleton />
        ) : failed || !stages?.length ? (
          <div className="flex h-[116px] items-center justify-center rounded-[16px] border border-dashed border-[#e7e9ed] bg-[#fafbfc] text-[11px] text-[#a0a4ac]">
            系统全貌暂不可用
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2 md:grid-cols-4 xl:grid-cols-8">
            {stages.map((stage, index) => (
              <LifecycleStageCard key={stage.key} stage={stage} index={index} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
