import { fetchDataSourceSummary } from '@/pages/data-source/service';
import { history } from '@umijs/max';
import {
  ArrowRightLeft,
  Braces,
  ChevronRight,
  Database,
  RadioTower,
  Workflow,
} from 'lucide-react';
import { type ReactNode, useEffect, useState } from 'react';

import DataCenter from './DataCenter';
import HomeWorkbench from './HomeWorkbench';
import ScheduleCenter from './ScheduleCenter';
import { homeDataCenterApi } from './service';

type CardTheme = 'offline' | 'realtime' | 'development' | 'workflow';

interface QuickCreateItem {
  key: CardTheme;
  title: string;
  description: string;
  icon: ReactNode;
  route: string;
}

const quickCreateItems: QuickCreateItem[] = [
  {
    key: 'offline',
    title: '离线同步',
    description: '创建批量与定时离线同步任务',
    icon: <ArrowRightLeft size={18} strokeWidth={2.1} />,
    route: '/sync/batch-link-up',
  },
  {
    key: 'realtime',
    title: '实时同步',
    description: '创建持续运行的实时同步任务',
    icon: <RadioTower size={18} strokeWidth={2.1} />,
    route: '/sync/realtime',
  },
  {
    key: 'development',
    title: '数据开发',
    description: '创建 SQL、Shell、Python 等开发节点',
    icon: <Braces size={18} strokeWidth={2.1} />,
    route: '/data-development',
  },
  {
    key: 'workflow',
    title: '工作流',
    description: '创建并编排任务工作流与调度',
    icon: <Workflow size={18} strokeWidth={2.1} />,
    route: '/workflow/definitions',
  },
];

const themeStyles: Record<
  CardTheme,
  { panel: string; core: string; glow: string; particles: string }
> = {
  offline: {
    panel:
      'bg-[linear-gradient(180deg,#9fc7ff_0%,#d6e7ff_54%,#f1f7ff_100%)]',
    core:
      'bg-[linear-gradient(145deg,#758fff_0%,#6267f2_100%)] shadow-[inset_0_-1px_2px_rgba(60,73,214,0.22)]',
    glow: 'bg-[#abc9ff]',
    particles:
      'bg-[radial-gradient(circle,rgba(112,142,255,0.42)_0.8px,transparent_0.9px)]',
  },
  realtime: {
    panel:
      'bg-[linear-gradient(180deg,#80d6ff_0%,#ccefff_54%,#effaff_100%)]',
    core:
      'bg-[linear-gradient(145deg,#35c6ff_0%,#168cf4_100%)] shadow-[inset_0_-1px_2px_rgba(0,93,231,0.22)]',
    glow: 'bg-[#78d9ff]',
    particles:
      'bg-[radial-gradient(circle,rgba(44,173,239,0.42)_0.8px,transparent_0.9px)]',
  },
  development: {
    panel:
      'bg-[linear-gradient(180deg,#ff8ea5_0%,#ffd4dd_54%,#fff0f3_100%)]',
    core:
      'bg-[linear-gradient(145deg,#ff4768_0%,#fe2c55_100%)] shadow-[inset_0_-1px_2px_rgba(188,21,56,0.22)]',
    glow: 'bg-[#ff8ea5]',
    particles:
      'bg-[radial-gradient(circle,rgba(254,44,85,0.34)_0.8px,transparent_0.9px)]',
  },
  workflow: {
    panel:
      'bg-[linear-gradient(180deg,#ffd95c_0%,#ffedb5_54%,#fff9e6_100%)]',
    core:
      'bg-[linear-gradient(145deg,#ffd33d_0%,#ffb900_100%)] shadow-[inset_0_-1px_2px_rgba(225,152,0,0.20)]',
    glow: 'bg-[#ffe27d]',
    particles:
      'bg-[radial-gradient(circle,rgba(241,181,0,0.36)_0.8px,transparent_0.9px)]',
  },
};

function LayeredIcon({ theme, icon }: { theme: CardTheme; icon: ReactNode }) {
  const styles = themeStyles[theme];

  return (
    <div
      className="pointer-events-none absolute -left-px -top-[2px] z-[3] h-[80px] w-[76px] origin-center transition-transform duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:-translate-y-[1px] group-hover:scale-[1.045]"
    >
      <div
        className="absolute left-[2px] top-[8px] h-[72px] w-[59px] origin-bottom-right rotate-[20deg] rounded-[12px] border border-white/80 bg-[#d7d9df]/40 shadow-[0_4px_12px_rgba(31,35,41,0.035)] backdrop-blur-[4px] transition-[transform,border-width,border-color] duration-[360ms] ease-[cubic-bezier(0.34,1.56,0.64,1)] group-hover:rotate-[26deg] group-hover:border-2 group-hover:border-white"
      />

      <div
        className={`absolute left-0 top-[4px] flex h-[72px] w-[61px] items-center justify-center overflow-hidden rounded-[12px] border border-white/90 shadow-[0_7px_16px_rgba(31,35,41,0.09)] ${styles.panel}`}
      >
        <span className="absolute inset-x-[5px] top-[2px] h-[20px] rounded-full bg-white/25 blur-[8px]" />
        <div
          className={`relative flex h-[34px] w-[34px] shrink-0 items-center justify-center overflow-hidden rounded-[9px] text-white transition-transform duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:scale-[1.035] ${styles.core}`}
        >
          <span
            className={`absolute -left-[7px] -top-[7px] h-[19px] w-[19px] rounded-full opacity-60 blur-[6px] ${styles.glow}`}
          />
          <span className="absolute -bottom-[7px] -right-[6px] h-[18px] w-[18px] rounded-full bg-white/40 blur-[6px]" />
          <span className="relative z-[2] flex h-5 w-5 items-center justify-center drop-shadow-[0_1px_1px_rgba(0,0,0,0.06)]">
            {icon}
          </span>
        </div>
      </div>
    </div>
  );
}

function QuickCreateCard({ item }: { item: QuickCreateItem }) {
  const styles = themeStyles[item.key];

  return (
    <button
      type="button"
      onClick={() => history.push(item.route)}
      className="group relative flex h-[76px] min-w-0 items-center overflow-visible rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.96] pr-4 text-left shadow-[0_3px_10px_rgba(31,35,41,0.045),0_1px_2px_rgba(31,35,41,0.025)] transition-[border-color,box-shadow,transform] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:-translate-y-px hover:border-[rgba(31,35,41,0.09)] hover:shadow-[0_8px_20px_rgba(31,35,41,0.07),0_1px_2px_rgba(31,35,41,0.025)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-200/70"
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute inset-0 z-0 overflow-hidden rounded-[16px] opacity-0 transition-opacity duration-300 group-hover:opacity-100 [background-size:8px_8px] [mask-image:linear-gradient(90deg,#000_0%,rgba(0,0,0,0.84)_24%,rgba(0,0,0,0.16)_72%,transparent_100%)] ${styles.particles}`}
      />
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 z-[1] rounded-[16px] bg-[linear-gradient(90deg,rgba(255,255,255,0.03)_0%,rgba(255,255,255,0.12)_38%,rgba(255,255,255,0.91)_100%)]"
      />

      <div className="relative z-[2] h-[76px] w-[77px] shrink-0">
        <LayeredIcon theme={item.key} icon={item.icon} />
      </div>

      <div className="relative z-[2] ml-2 min-w-0 flex-1">
        <div className="truncate text-[15px] font-semibold leading-[22px] text-[#292c35]">
          {item.title}
        </div>
        <div className="mt-0.5 flex min-w-0 items-center text-[13px] font-normal leading-5 text-[#9498a1]">
          <span className="min-w-0 truncate">{item.description}</span>
          <ChevronRight
            size={13}
            strokeWidth={1.8}
            className="ml-[2px] shrink-0 -translate-x-[3px] text-[#92969f] opacity-0 transition-[opacity,transform] duration-[220ms] ease-out group-hover:translate-x-0 group-hover:opacity-100"
          />
        </div>
      </div>
    </button>
  );
}

function ProfileStat({
  label,
  value,
  arrow = false,
  onClick,
}: {
  label: string;
  value: number;
  arrow?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center border-0 bg-transparent p-0 text-sm leading-[22px] text-[#747983] transition-colors duration-200 hover:text-[#292c35]"
    >
      <span>{label}</span>
      <strong className="ml-[5px] font-semibold text-[#282b34]">{value}</strong>
      {arrow ? (
        <ChevronRight
          size={14}
          strokeWidth={1.8}
          className="ml-[3px] text-[#9599a2]"
        />
      ) : null}
    </button>
  );
}

function AtmosphereBand() {
  return (
    <div className="absolute left-[330px] right-3 top-[1px] h-[106px] overflow-hidden rounded-[999px] bg-[linear-gradient(90deg,rgba(247,248,250,0)_0%,rgba(245,248,251,0.72)_15%,rgba(230,240,246,0.90)_50%,rgba(245,248,251,0.72)_85%,rgba(247,248,250,0)_100%)] opacity-90">
      <div className="absolute inset-0 opacity-[0.30] [background-image:radial-gradient(circle,rgba(255,255,255,0.95)_0.8px,transparent_0.9px)] [background-size:7px_7px] [mask-image:linear-gradient(90deg,transparent_0%,#000_15%,#000_85%,transparent_100%)]" />
      <div className="absolute left-1/2 top-1/2 h-[170%] w-[42%] -translate-x-1/2 -translate-y-1/2 bg-[radial-gradient(ellipse_at_center,rgba(186,220,237,0.57)_0%,rgba(210,231,241,0.31)_45%,rgba(255,255,255,0)_78%)] blur-[20px]" />
      <div className="absolute inset-x-[12%] top-0 h-[34%] bg-gradient-to-b from-white/60 to-transparent blur-[5px]" />
      <div className="absolute inset-y-0 left-0 w-[18%] bg-gradient-to-r from-[#f7f8fa] via-[#f7f8fa]/55 to-transparent" />
      <div className="absolute inset-y-0 right-0 w-[18%] bg-gradient-to-l from-[#f7f8fa] via-[#f7f8fa]/55 to-transparent" />
    </div>
  );
}

export default function CreatorWorkbenchPage() {
  const [headerStats, setHeaderStats] = useState({
    dataSourceCount: 0,
    runningCount: 0,
    exceptionCount: 0,
  });

  useEffect(() => {
    let active = true;

    Promise.allSettled([
      fetchDataSourceSummary(),
      homeDataCenterApi.overview('7d'),
    ]).then(([dataSourceResult, overviewResult]) => {
      if (!active) return;

      setHeaderStats({
        dataSourceCount:
          dataSourceResult.status === 'fulfilled'
            ? dataSourceResult.value.data?.total || 0
            : 0,
        runningCount:
          overviewResult.status === 'fulfilled'
            ? overviewResult.value.data?.metrics?.runningCount || 0
            : 0,
        exceptionCount:
          overviewResult.status === 'fulfilled'
            ? overviewResult.value.data?.metrics?.failedCount || 0
            : 0,
      });
    });

    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="relative min-h-screen w-full overflow-hidden bg-[#f7f8fa] text-[#242731]">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 overflow-hidden"
      >
        <AtmosphereBand />
        <div className="absolute -right-[40px] -top-[185px] h-[390px] w-[68%] bg-[radial-gradient(ellipse_at_center,rgba(158,215,239,0.22)_0%,rgba(190,224,239,0.10)_46%,rgba(255,255,255,0)_74%)] blur-[24px]" />
        <div className="absolute -bottom-[190px] -right-[170px] h-[420px] w-[620px] bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.28),rgba(255,255,255,0)_70%)] blur-[20px]" />
        <div className="absolute right-[2%] top-0 h-[102px] w-[62%] opacity-[0.08] [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)] [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_70%,transparent_100%)]" />
      </div>

      <div className="relative z-10">
        <header className="flex h-[116px] items-center px-4">
          <div className="flex items-center">
            <div className="flex h-[66px] w-[66px] shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/90 bg-gradient-to-br from-[#dde6ef] via-[#a6c8e2] to-[#5e93d4] text-white/95 shadow-[0_2px_4px_rgba(31,35,41,0.04)]">
              <Database size={30} strokeWidth={1.5} />
            </div>

            <div className="ml-4 min-w-0">
              <div className="flex min-h-[22px] items-center">
                <span className="whitespace-nowrap text-sm font-medium leading-[22px] text-[#252830]">
                  Yak Ops
                </span>
                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />
                <span className="whitespace-nowrap text-sm font-normal leading-[22px] text-[#777b84]">
                  Data Operations Platform
                </span>
                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />
                <span className="whitespace-nowrap text-sm font-normal leading-[22px] text-[#777b84]">
                  让数据接入、开发、编排、质量与消费变得更简单
                </span>
              </div>

              <div className="mt-2.5 flex items-center gap-[27px]">
                <ProfileStat
                  label="数据源"
                  value={headerStats.dataSourceCount}
                  arrow
                  onClick={() => history.push('/data-source')}
                />
                <ProfileStat
                  label="运行中"
                  value={headerStats.runningCount}
                  arrow
                  onClick={() => history.push('/data-development/executions')}
                />
                <ProfileStat
                  label="近7日异常"
                  value={headerStats.exceptionCount}
                  onClick={() => history.push('/data-development/executions')}
                />
              </div>
            </div>
          </div>
        </header>

        <main className="px-4 pb-4">
          <section className="rounded-[22px] border border-[#f1f1f1] bg-white/[0.74] px-[22px] pb-6 pt-6 backdrop-blur-[8px]">
            <h2 className="mb-5 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
              快速创建
            </h2>

            <div className="grid grid-cols-1 gap-[14px] sm:grid-cols-2 min-[1280px]:grid-cols-4">
              {quickCreateItems.map((item) => (
                <QuickCreateCard key={item.key} item={item} />
              ))}
            </div>
          </section>

          <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_410px]">
            <DataCenter />
            <ScheduleCenter />
          </div>

          <HomeWorkbench />
        </main>
      </div>
    </div>
  );
}
