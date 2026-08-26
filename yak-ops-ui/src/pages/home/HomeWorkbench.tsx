import { history } from '@umijs/max';
import {
  ChevronRight,
  Clock3,
  LayoutDashboard,
  Sparkles,
} from 'lucide-react';

import {
  DataLineageOverview,
  DatasetOverview,
  useHomeAssetOverview,
} from './HomeAssetOverview';
import HomeDataServiceOverview from './HomeDataServiceOverview';
import QualityOverview from './HomeQualityOverview';

/**
 * 首页业务总览。
 *
 * 数据集、血缘、数据质量与数据服务已接入真实统计；仪表盘继续按后续阶段替换 mock。
 */

interface SectionHeaderProps {
  title: string;
  description?: string;
  onMore?: () => void;
}

function SectionHeader({
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

/* -------------------------------------------------------------------------- */
/* 仪表盘                                                                     */
/* -------------------------------------------------------------------------- */

const dashboardItems = [
  {
    id: 'sales',
    title: '销售经营分析',
    description: '销售额、订单量、区域与渠道经营分析',
    time: '20 分钟前更新',
    type: 'sales',
  },
  {
    id: 'user',
    title: '用户增长分析',
    description: '新增用户、活跃度与留存趋势',
    time: '2 小时前更新',
    type: 'user',
  },
  {
    id: 'monitor',
    title: '数据平台运行监控',
    description: '任务运行、失败率与资源使用情况',
    time: '今天 08:20 更新',
    type: 'monitor',
  },
];

function DashboardPreview({ type }: { type: string }) {
  if (type === 'sales') {
    return (
      <div className="absolute inset-0 p-4">
        <div className="grid grid-cols-3 gap-2">
          {[64, 42, 78].map((width, index) => (
            <div
              key={`${width}-${index}`}
              className="h-7 rounded-[5px] bg-white/80 shadow-sm"
            >
              <div className="px-2 pt-1.5">
                <div className="h-1 w-7 rounded-full bg-[#d4dcf6]" />
                <div
                  className="mt-1 h-1.5 rounded-full bg-[#8fa4ea]"
                  style={{
                    width: `${width}%`,
                  }}
                />
              </div>
            </div>
          ))}
        </div>

        <div className="mt-3 flex h-[82px] items-end gap-2 rounded-[7px] bg-white/70 px-3 pb-2 pt-3">
          {[25, 42, 34, 62, 49, 76, 67, 89, 72, 94].map(
            (height, index) => (
              <span
                key={`${height}-${index}`}
                className="flex-1 rounded-t-[2px] bg-[#9caee8]"
                style={{
                  height: `${height}%`,
                }}
              />
            ),
          )}
        </div>
      </div>
    );
  }

  if (type === 'user') {
    return (
      <div className="absolute inset-0 p-4">
        <div className="flex gap-2">
          <div className="h-9 flex-1 rounded-[6px] bg-white/80 shadow-sm" />
          <div className="h-9 flex-1 rounded-[6px] bg-white/80 shadow-sm" />
        </div>

        <svg
          viewBox="0 0 320 100"
          className="mt-3 h-[88px] w-full rounded-[8px] bg-white/70"
          aria-hidden="true"
        >
          <path
            d="M8 78 C38 65 52 72 76 55 C105 35 121 58 149 45 C177 31 195 42 218 25 C244 9 266 37 312 15"
            fill="none"
            stroke="#8099e7"
            strokeWidth="3"
          />

          <path
            d="M8 78 C38 65 52 72 76 55 C105 35 121 58 149 45 C177 31 195 42 218 25 C244 9 266 37 312 15 L312 98 L8 98 Z"
            fill="rgba(128,153,231,0.10)"
          />
        </svg>
      </div>
    );
  }

  return (
    <div className="absolute inset-0 p-4">
      <div className="grid grid-cols-2 gap-2">
        <div className="flex h-[48px] items-center gap-2 rounded-[6px] bg-white/80 px-3 shadow-sm">
          <span className="h-6 w-6 rounded-full border-[5px] border-[#88a0e8] border-r-[#d8def2]" />
          <span className="h-1.5 flex-1 rounded-full bg-[#d5dbed]" />
        </div>

        <div className="flex h-[48px] items-end gap-1 rounded-[6px] bg-white/80 px-3 pb-2 pt-2 shadow-sm">
          {[44, 70, 52, 82, 63].map((height, index) => (
            <span
              key={`${height}-${index}`}
              className="flex-1 rounded-t-sm bg-[#91a5e6]"
              style={{
                height: `${height}%`,
              }}
            />
          ))}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-4 gap-2">
        {[1, 2, 3, 4].map((item) => (
          <div
            key={item}
            className="h-[60px] rounded-[6px] bg-white/75 shadow-sm"
          />
        ))}
      </div>
    </div>
  );
}

function DashboardOverview() {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-6 pt-5">
      <SectionHeader
        title="仪表盘"
        description="最近更新的业务分析与平台监控看板"
        onMore={() => history.push('/dashboard')}
      />

      <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {dashboardItems.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => history.push('/dashboard')}
            className="group overflow-hidden rounded-[14px] border border-[#eceef2] bg-white text-left transition-[box-shadow,transform,border-color] duration-200 hover:-translate-y-0.5 hover:border-[#e0e3e9] hover:shadow-[0_10px_28px_rgba(31,35,41,0.075)]"
          >
            <div className="relative h-[164px] overflow-hidden bg-[linear-gradient(145deg,#f4f6fb_0%,#edf1fa_100%)]">
              <DashboardPreview type={item.type} />

              <div className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-[8px] border border-white/80 bg-white/85 text-[#6674a8] opacity-0 shadow-sm backdrop-blur transition-opacity group-hover:opacity-100">
                <ChevronRight size={14} strokeWidth={1.8} />
              </div>
            </div>

            <div className="px-4 pb-4 pt-3.5">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[7px] bg-[#f2f4fb] text-[#737fae]">
                  <LayoutDashboard size={14} strokeWidth={1.8} />
                </span>

                <strong className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#373b44]">
                  {item.title}
                </strong>
              </div>

              <p className="mt-2 truncate text-[11px] text-[#92969f]">
                {item.description}
              </p>

              <div className="mt-3 flex items-center gap-1 text-[10px] text-[#a0a4ac]">
                <Clock3 size={11} strokeWidth={1.8} />
                {item.time}
              </div>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------------- */
/* 首页                                                                       */
/* -------------------------------------------------------------------------- */

export default function HomeWorkbench() {
  const assetOverviewState = useHomeAssetOverview();

  return (
    <div className="mt-4 space-y-4">
      <DatasetOverview state={assetOverviewState} />

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.32fr)_minmax(400px,0.68fr)]">
        <QualityOverview />
        <HomeDataServiceOverview />
      </div>

      <DataLineageOverview state={assetOverviewState} />

      <DashboardOverview />

      <div className="flex items-center justify-center gap-2 py-2 text-[10px] text-[#aaadb4]">
        <Sparkles size={11} strokeWidth={1.8} />
        数据集、血缘、数据质量与数据服务已接入真实数据，仪表盘将在后续阶段接入
      </div>
    </div>
  );
}
