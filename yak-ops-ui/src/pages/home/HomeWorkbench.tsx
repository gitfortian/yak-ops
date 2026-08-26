import { history } from '@umijs/max';
import {
  ArrowRightLeft,
  Braces,
  ChevronRight,
  Cloud,
  Database,
  GitBranch,
  LayoutDashboard,
  Monitor,
  RadioTower,
  ShieldCheck,
  Workflow,
} from 'lucide-react';
import type { ReactNode } from 'react';

type CapabilityTone =
  | 'blue'
  | 'cyan'
  | 'rose'
  | 'amber'
  | 'indigo'
  | 'green'
  | 'violet'
  | 'sky'
  | 'orange'
  | 'purple';

type CapabilityKey =
  | 'offline-sync'
  | 'realtime-sync'
  | 'development'
  | 'workflow'
  | 'dataset'
  | 'quality'
  | 'lineage'
  | 'data-service'
  | 'dashboard'
  | 'digital-screen';

interface CapabilityItem {
  title: string;
  shortTitle?: string;
  description: string;
  route: string;
  icon: ReactNode;
  tone: CapabilityTone;
  metricLabels: readonly [string, string];
}

interface CapabilityGroup {
  key: string;
  title: string;
  description: string;
  dotClassName: string;
  itemKeys: readonly CapabilityKey[];
}

const toneStyles: Record<
  CapabilityTone,
  { icon: string; hover: string }
> = {
  blue: {
    icon: 'bg-[#edf4ff] text-[#5b8cff]',
    hover: 'group-hover:bg-[#e6efff]',
  },
  cyan: {
    icon: 'bg-[#eaf9ff] text-[#2499cc]',
    hover: 'group-hover:bg-[#dff5ff]',
  },
  rose: {
    icon: 'bg-[#fff0f3] text-[#ee5f7b]',
    hover: 'group-hover:bg-[#ffe7ed]',
  },
  amber: {
    icon: 'bg-[#fff7df] text-[#d79913]',
    hover: 'group-hover:bg-[#fff1c7]',
  },
  indigo: {
    icon: 'bg-[#eef0ff] text-[#6875e6]',
    hover: 'group-hover:bg-[#e5e8ff]',
  },
  green: {
    icon: 'bg-[#edf9f2] text-[#2f9d67]',
    hover: 'group-hover:bg-[#e2f6ea]',
  },
  violet: {
    icon: 'bg-[#f3efff] text-[#8264d8]',
    hover: 'group-hover:bg-[#ece6ff]',
  },
  sky: {
    icon: 'bg-[#ebf7ff] text-[#348ed1]',
    hover: 'group-hover:bg-[#e0f2ff]',
  },
  orange: {
    icon: 'bg-[#fff3e8] text-[#dc7b2d]',
    hover: 'group-hover:bg-[#ffead8]',
  },
  purple: {
    icon: 'bg-[#f4efff] text-[#8a63d2]',
    hover: 'group-hover:bg-[#ece4ff]',
  },
};

const capabilities: Record<CapabilityKey, CapabilityItem> = {
  'offline-sync': {
    title: '离线同步',
    description: '批量与定时同步数据',
    route: '/sync/batch-link-up',
    icon: <ArrowRightLeft size={17} strokeWidth={1.9} />,
    tone: 'blue',
    metricLabels: ['同步任务', '运行中'],
  },
  'realtime-sync': {
    title: '实时同步',
    description: '持续采集并同步变更数据',
    route: '/sync/realtime',
    icon: <RadioTower size={17} strokeWidth={1.9} />,
    tone: 'cyan',
    metricLabels: ['实时任务', '运行中'],
  },
  development: {
    title: '数据开发',
    description: '开发 SQL、Shell、Python 任务',
    route: '/data-development',
    icon: <Braces size={17} strokeWidth={1.9} />,
    tone: 'rose',
    metricLabels: ['开发任务', '今日运行'],
  },
  workflow: {
    title: '工作流',
    description: '编排任务依赖与调度周期',
    route: '/workflow/definitions',
    icon: <Workflow size={17} strokeWidth={1.9} />,
    tone: 'amber',
    metricLabels: ['工作流', '今日调度'],
  },
  dataset: {
    title: '数据集',
    description: '统一管理数据资产与元数据',
    route: '/data-analysis/data-catalog',
    icon: <Database size={17} strokeWidth={1.9} />,
    tone: 'indigo',
    metricLabels: ['数据集', '数据表'],
  },
  quality: {
    title: '数据质量',
    description: '配置规则并跟踪质量结果',
    route: '/data-quality/table-config',
    icon: <ShieldCheck size={17} strokeWidth={1.9} />,
    tone: 'green',
    metricLabels: ['监控表', '异常'],
  },
  lineage: {
    title: '数据血缘',
    description: '查看数据上下游依赖关系',
    route: '/data-analysis/lineage',
    icon: <GitBranch size={17} strokeWidth={1.9} />,
    tone: 'violet',
    metricLabels: ['节点', '关系'],
  },
  'data-service': {
    title: '数据服务',
    description: '发布、调试与管理数据 API',
    route: '/data-service',
    icon: <Cloud size={17} strokeWidth={1.9} />,
    tone: 'sky',
    metricLabels: ['已发布 API', '今日调用'],
  },
  dashboard: {
    title: '仪表盘',
    description: '构建业务分析看板与图表',
    route: '/dashboard',
    icon: <LayoutDashboard size={17} strokeWidth={1.9} />,
    tone: 'orange',
    metricLabels: ['仪表盘', '图表'],
  },
  'digital-screen': {
    title: '数字化大屏',
    shortTitle: '数字大屏',
    description: '集中展示关键业务指标',
    route: '/digital-screen',
    icon: <Monitor size={17} strokeWidth={1.9} />,
    tone: 'purple',
    metricLabels: ['数字大屏', '已发布'],
  },
};

const capabilityGroups: readonly CapabilityGroup[] = [
  {
    key: 'production',
    title: '数据生产',
    description: '完成数据接入、开发与任务编排',
    dotClassName: 'bg-[#5b8cff]',
    itemKeys: ['offline-sync', 'realtime-sync', 'development', 'workflow'],
  },
  {
    key: 'governance',
    title: '数据治理',
    description: '沉淀数据资产、质量与血缘关系',
    dotClassName: 'bg-[#7d6ce0]',
    itemKeys: ['dataset', 'quality', 'lineage'],
  },
  {
    key: 'consumption',
    title: '数据消费',
    description: '通过服务与可视化释放数据价值',
    dotClassName: 'bg-[#43a98a]',
    itemKeys: ['data-service', 'dashboard', 'digital-screen'],
  },
];

const quickNavigationKeys: readonly CapabilityKey[] = [
  'dataset',
  'quality',
  'data-service',
  'lineage',
  'dashboard',
  'digital-screen',
];

const EMPTY_METRIC_VALUE = '--';

const snapshotKeys: readonly CapabilityKey[] = [
  'dataset',
  'quality',
  'data-service',
  'lineage',
  'dashboard',
];

function CapabilityIcon({
  item,
  size = 'normal',
}: {
  item: CapabilityItem;
  size?: 'small' | 'normal' | 'large';
}) {
  const styles = toneStyles[item.tone];
  const sizeClassName =
    size === 'small'
      ? 'h-8 w-8 rounded-[9px]'
      : size === 'large'
        ? 'h-10 w-10 rounded-[11px]'
        : 'h-9 w-9 rounded-[10px]';

  return (
    <span
      className={`flex shrink-0 items-center justify-center transition-colors duration-200 ${sizeClassName} ${styles.icon} ${styles.hover}`}
    >
      {item.icon}
    </span>
  );
}

function CapabilityRow({ itemKey }: { itemKey: CapabilityKey }) {
  const item = capabilities[itemKey];

  return (
    <button
      type="button"
      onClick={() => history.push(item.route)}
      className="group flex min-h-[78px] w-full items-center gap-3 rounded-[10px] border-0 bg-transparent px-2.5 py-2.5 text-left transition-colors duration-200 hover:bg-[#f7f8fa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#dce8ff]"
    >
      <CapabilityIcon item={item} />

      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center">
          <strong className="min-w-0 flex-1 truncate text-[13px] font-semibold leading-5 text-[#353942]">
            {item.title}
          </strong>
          <ChevronRight
            size={13}
            strokeWidth={1.8}
            className="ml-1 shrink-0 -translate-x-1 text-[#a4a8b0] opacity-0 transition-[opacity,transform] duration-200 group-hover:translate-x-0 group-hover:opacity-100"
          />
        </span>
        <span className="mt-0.5 block truncate text-[11px] leading-[18px] text-[#92969f]">
          {item.description}
        </span>
        <span className="mt-1 flex items-center gap-3 text-[10px] leading-4 text-[#a2a6ae]">
          {item.metricLabels.map((label) => (
            <span key={label} className="whitespace-nowrap">
              {label}
              <strong className="ml-1 font-semibold text-[#6f747e]">
                {EMPTY_METRIC_VALUE}
              </strong>
            </span>
          ))}
        </span>
      </span>
    </button>
  );
}

function CapabilityGroupColumn({ group }: { group: CapabilityGroup }) {
  return (
    <div className="py-4 first:pt-0 last:pb-0 lg:px-5 lg:py-0 lg:first:pl-0 lg:last:pr-0">
      <div className="flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${group.dotClassName}`} />
        <h3 className="text-[14px] font-semibold leading-5 text-[#343842]">
          {group.title}
        </h3>
      </div>
      <p className="mt-1 text-[11px] leading-[18px] text-[#969aa3]">
        {group.description}
      </p>

      <div className="mt-3 space-y-1">
        {group.itemKeys.map((itemKey) => (
          <CapabilityRow key={itemKey} itemKey={itemKey} />
        ))}
      </div>
    </div>
  );
}

function QuickNavigation() {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          快捷导航
        </h2>
        <span className="text-[11px] text-[#9a9ea6]">常用能力</span>
      </header>

      <div className="mt-4 grid grid-cols-3 gap-x-2 gap-y-3">
        {quickNavigationKeys.map((itemKey) => {
          const item = capabilities[itemKey];
          return (
            <button
              key={itemKey}
              type="button"
              onClick={() => history.push(item.route)}
              className="group flex min-w-0 flex-col items-center rounded-[12px] border-0 bg-transparent px-1 py-2 text-center transition-colors duration-200 hover:bg-[#f7f8fa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#dce8ff]"
            >
              <span className="transition-transform duration-200 group-hover:-translate-y-0.5">
                <CapabilityIcon item={item} size="large" />
              </span>
              <span className="mt-2 max-w-full truncate text-[11px] font-medium leading-4 text-[#555a64]">
                {item.shortTitle || item.title}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function CapabilitySnapshot() {
  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-4 pt-5">
      <header>
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          核心总览
        </h2>
        <p className="mt-1 text-[11px] leading-5 text-[#969aa3]">
          资产、质量与消费状态
        </p>
      </header>

      <div className="mt-2 divide-y divide-[#f0f1f3]">
        {snapshotKeys.map((itemKey) => {
          const item = capabilities[itemKey];
          return (
            <button
              key={itemKey}
              type="button"
              onClick={() => history.push(item.route)}
              className="group grid w-full grid-cols-[minmax(0,1fr)_70px_70px_14px] items-center gap-2 border-0 bg-transparent py-2.5 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#dce8ff]"
            >
              <span className="flex min-w-0 items-center gap-2.5">
                <CapabilityIcon item={item} size="small" />
                <strong className="truncate text-[12px] font-semibold text-[#454952] transition-colors duration-200 group-hover:text-[#252832]">
                  {item.title}
                </strong>
              </span>

              {item.metricLabels.map((label) => (
                <span key={label} className="text-right">
                  <span className="block truncate text-[9px] leading-4 text-[#a0a4ac]">
                    {label}
                  </span>
                  <strong className="block text-[12px] font-semibold leading-4 text-[#555a64]">
                    {EMPTY_METRIC_VALUE}
                  </strong>
                </span>
              ))}

              <ChevronRight
                size={13}
                strokeWidth={1.8}
                className="text-[#b0b4bb] transition-transform duration-200 group-hover:translate-x-0.5"
              />
            </button>
          );
        })}
      </div>
    </section>
  );
}

export default function HomeWorkbench() {
  return (
    <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_410px]">
      <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
        <header>
          <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            数据运营中心
          </h2>
          <p className="mt-1 text-[12px] leading-5 text-[#8f949d]">
            从数据生产、治理到消费，统一查看平台能力
          </p>
        </header>

        <div className="mt-5 grid grid-cols-1 divide-y divide-[#eef0f3] lg:grid-cols-3 lg:divide-x lg:divide-y-0">
          {capabilityGroups.map((group) => (
            <CapabilityGroupColumn key={group.key} group={group} />
          ))}
        </div>
      </section>

      <aside className="grid content-start gap-4">
        <QuickNavigation />
        <CapabilitySnapshot />
      </aside>
    </div>
  );
}
