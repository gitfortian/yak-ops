import YakTab from '@/components/YakTab';
import { useIntl } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';

type CenterTabKey = 'dashboard' | 'screen';

interface AssetItem {
  key: string;
  title: string;
  description: string;
  code: string;
  thumbnailClassName: string;
}

interface AssetListProps {
  items: AssetItem[];
}

const MAX_VISIBLE_ITEMS = 5;

const EXIT_DURATION = 90;
const ENTER_DURATION = 200;

/**
 * Mock data only.
 *
 * These data sources will be replaced by backend APIs later.
 */
const DATASET_ITEMS: AssetItem[] = [
  {
    key: 'dataset-1',
    title: '用户订单明细数据集',
    description: 'MySQL · 36 个字段 · 12.8 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#eef4ff]',
  },
  {
    key: 'dataset-2',
    title: '商品销售汇总数据集',
    description: 'StarRocks · 28 个字段 · 8.6 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#f1f8ed]',
  },
  {
    key: 'dataset-3',
    title: '客户基础信息数据集',
    description: 'PostgreSQL · 42 个字段 · 5.2 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#fff4e8]',
  },
  {
    key: 'dataset-4',
    title: '门店经营指标数据集',
    description: 'Doris · 24 个字段 · 3.7 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#f5efff]',
  },
  {
    key: 'dataset-5',
    title: '供应链库存数据集',
    description: 'Oracle · 31 个字段 · 16.4 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#edf8f7]',
  },
  {
    key: 'dataset-6',
    title: '会员行为分析数据集',
    description: 'ClickHouse · 46 个字段 · 28.1 万行',
    code: 'DS',
    thumbnailClassName: 'bg-[#fff1f4]',
  },
];

const DASHBOARD_ITEMS: AssetItem[] = [
  {
    key: 'dashboard-1',
    title: '销售经营分析仪表盘',
    description: '12 个图表 · 10 分钟前更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#f0f4ff]',
  },
  {
    key: 'dashboard-2',
    title: '用户增长分析仪表盘',
    description: '9 个图表 · 25 分钟前更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#fff3e9]',
  },
  {
    key: 'dashboard-3',
    title: '商品运营分析仪表盘',
    description: '16 个图表 · 1 小时前更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#f2f8ec]',
  },
  {
    key: 'dashboard-4',
    title: '供应链监控仪表盘',
    description: '8 个图表 · 今天 16:40 更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#f5f0ff]',
  },
  {
    key: 'dashboard-5',
    title: '数据质量运营仪表盘',
    description: '11 个图表 · 今天 15:26 更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#edf8f7]',
  },
  {
    key: 'dashboard-6',
    title: '财务指标分析仪表盘',
    description: '14 个图表 · 昨天更新',
    code: 'BI',
    thumbnailClassName: 'bg-[#fff1f3]',
  },
];

const SCREEN_ITEMS: AssetItem[] = [
  {
    key: 'screen-1',
    title: '企业经营驾驶舱',
    description: '1920 × 1080 · 18 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#edf3ff]',
  },
  {
    key: 'screen-2',
    title: '实时销售数据大屏',
    description: '1920 × 1080 · 21 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#eef8f4]',
  },
  {
    key: 'screen-3',
    title: '智慧园区运营大屏',
    description: '2560 × 1440 · 16 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#fff3e8]',
  },
  {
    key: 'screen-4',
    title: '供应链实时监控大屏',
    description: '1920 × 1080 · 24 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#f5efff]',
  },
  {
    key: 'screen-5',
    title: '数据中心运行态势大屏',
    description: '2560 × 1440 · 20 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#eef7fa]',
  },
  {
    key: 'screen-6',
    title: '客户服务运营大屏',
    description: '1920 × 1080 · 13 个组件',
    code: 'SC',
    thumbnailClassName: 'bg-[#fff0f4]',
  },
];

const SERVICE_ITEMS: AssetItem[] = [
  {
    key: 'service-1',
    title: '用户信息查询服务',
    description: 'REST API · 2 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#edf4ff]',
  },
  {
    key: 'service-2',
    title: '商品实时库存服务',
    description: 'REST API · 4 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#eff8ed]',
  },
  {
    key: 'service-3',
    title: '订单状态查询服务',
    description: 'REST API · 3 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#fff4e8]',
  },
  {
    key: 'service-4',
    title: '客户标签查询服务',
    description: 'REST API · 5 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#f4efff]',
  },
  {
    key: 'service-5',
    title: '经营指标聚合服务',
    description: 'REST API · 6 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#edf8f7]',
  },
  {
    key: 'service-6',
    title: '组织架构查询服务',
    description: 'REST API · 2 个接口 · 正常',
    code: 'API',
    thumbnailClassName: 'bg-[#fff1f4]',
  },
];

const getRankClassName = (index: number) => {
  switch (index) {
    case 0:
      return 'bg-[#ff3d5f] text-white';

    case 1:
      return 'bg-[#ff7a1a] text-white';

    case 2:
      return 'bg-[#f5b700] text-white';

    default:
      return 'bg-[#a4a8b0] text-white';
  }
};

/**
 * Header used by dataset and data-service columns.
 *
 * Its height and underline are aligned with YakTab.
 */
function StaticSectionHeader({ title }: { title: string }) {
  return (
    <div className="relative flex h-[35px] shrink-0 border-b border-[#eceef2]">
      <div className="relative flex h-full items-start pb-2 text-[13px] font-semibold text-[#292c35]">
        {title}

        <span className="absolute bottom-[-1px] left-0 right-0 h-[3px] bg-[#252832]" />
      </div>
    </div>
  );
}

/**
 * Shared asset list.
 *
 * Homepage only displays the first five items.
 */
function AssetList({ items }: AssetListProps) {
  const visibleItems = items.slice(0, MAX_VISIBLE_ITEMS);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="mt-2 flex-1">
        {visibleItems.map((item, index) => (
          <div
            key={item.key}
            className="group flex min-w-0 items-center gap-3 py-[9px]"
          >
            <div
              className={[
                'relative flex h-[52px] w-[52px] shrink-0 items-center justify-center',
                'overflow-hidden rounded-[8px]',
                item.thumbnailClassName,
              ].join(' ')}
            >
              <span className="text-[12px] font-semibold tracking-[0.4px] text-[#5d6470]">
                {item.code}
              </span>

              <span
                className={[
                  'absolute left-0 top-0 flex h-[17px] min-w-[17px]',
                  'items-center justify-center px-1',
                  'rounded-br-[5px] text-[10px] font-semibold leading-none',
                  getRankClassName(index),
                ].join(' ')}
              >
                {index + 1}
              </span>
            </div>

            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-medium leading-5 text-[#292d36] transition-colors group-hover:text-[#111318]">
                {item.title}
              </div>

              <div className="mt-1 truncate text-[12px] leading-5 text-[#858a93]">
                {item.description}
              </div>
            </div>
          </div>
        ))}
      </div>

      <button
        type="button"
        className="mx-auto mt-2 flex h-7 shrink-0 items-center gap-1 text-[12px] text-[#7b8089] transition-colors hover:text-[#252832]"
      >
        查看全部

        <span className="text-[16px] leading-none">›</span>
      </button>
    </div>
  );
}

/**
 * Frontend-only homepage data-assets overview.
 *
 * Dataset, dashboard, digital-screen and data-service content currently
 * uses mock data and will be connected to backend APIs separately.
 */
export default function HomeDataAssets() {
  const intl = useIntl();

  /**
   * Controls YakTab immediately.
   *
   * The tab underline responds immediately after clicking.
   */
  const [activeCenterTab, setActiveCenterTab] =
    useState<CenterTabKey>('dashboard');

  /**
   * Controls the actual content being rendered.
   *
   * It is intentionally separated from activeCenterTab so the old content
   * can finish fading out before we replace it with the new content.
   */
  const [renderedCenterTab, setRenderedCenterTab] =
    useState<CenterTabKey>('dashboard');

  const [contentVisible, setContentVisible] = useState(true);

  const switchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const animationFrameRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (switchTimerRef.current) {
        clearTimeout(switchTimerRef.current);
      }

      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, []);

  const handleCenterTabChange = (key: string) => {
    const nextTab = key as CenterTabKey;

    if (nextTab === activeCenterTab) {
      return;
    }

    /**
     * The tab itself switches immediately.
     */
    setActiveCenterTab(nextTab);

    /**
     * Cancel unfinished transitions when users switch tabs quickly.
     */
    if (switchTimerRef.current) {
      clearTimeout(switchTimerRef.current);
      switchTimerRef.current = null;
    }

    if (animationFrameRef.current !== null) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }

    /**
     * Phase 1:
     * Fade out the current list quickly.
     */
    setContentVisible(false);

    switchTimerRef.current = setTimeout(() => {
      /**
       * Phase 2:
       * Replace the content while the container is invisible.
       */
      setRenderedCenterTab(nextTab);

      /**
       * Phase 3:
       * Wait until the browser enters the next frame,
       * then fade the new content back in.
       */
      animationFrameRef.current = requestAnimationFrame(() => {
        setContentVisible(true);
        animationFrameRef.current = null;
      });

      switchTimerRef.current = null;
    }, EXIT_DURATION);
  };

  const centerItems =
    renderedCenterTab === 'dashboard' ? DASHBOARD_ITEMS : SCREEN_ITEMS;

  return (
    <section className="flex h-full min-h-[520px] min-w-0 flex-col rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="shrink-0">
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({
            id: 'pages.home.dataAssets.title',
          })}
        </h2>
      </header>

      <div className="mt-4 grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-3">
        {/* Dataset */}
        <div className="flex min-w-0 flex-col pb-5 lg:pb-0 lg:pr-6">
          <StaticSectionHeader title="数据集列表" />

          <AssetList items={DATASET_ITEMS} />
        </div>

        {/* Dashboard / Digital screen */}
        <div className="flex min-w-0 flex-col border-t border-[#eceef2] py-5 lg:border-l lg:border-t-0 lg:px-6 lg:py-0">
          <YakTab
            className="shrink-0"
            activeKey={activeCenterTab}
            onChange={handleCenterTabChange}
            items={[
              {
                key: 'dashboard',
                label: '仪表盘',
              },
              {
                key: 'screen',
                label: '大屏',
              },
            ]}
          />

          {/*
           * Content transition
           *
           * Switching rhythm:
           *
           * old content
           * opacity 1 -> 0
           *
           * replace data
           *
           * new content
           * opacity 0 -> 1
           * translateY 4px -> 0
           */}
          <div
            className={[
              'flex min-h-0 flex-1 flex-col',
              'transition-[opacity]',
              'ease-[cubic-bezier(0.16,1,0.3,1)]',
              contentVisible
                ? 'translate-y-0 opacity-100'
                : 'translate-y-1 opacity-0',
            ].join(' ')}
            style={{
              transitionDuration: contentVisible
                ? `${ENTER_DURATION}ms`
                : `${EXIT_DURATION}ms`,
            }}
          >
            <AssetList items={centerItems} />
          </div>
        </div>

        {/* Data service */}
        <div className="flex min-w-0 flex-col border-t border-[#eceef2] pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <StaticSectionHeader title="数据服务列表" />

          <AssetList items={SERVICE_ITEMS} />
        </div>
      </div>
    </section>
  );
}