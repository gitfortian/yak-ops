import {
  ArrowRightLeft,
  BarChart3,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Clock3,
  Code2,
  Copy,
  Database,
  RadioTower,
  ShieldCheck,
  Workflow,
} from 'lucide-react';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

/* =========================================================
 * Types
 * ========================================================= */

type IconTheme =
  | 'video'
  | 'image'
  | 'panorama'
  | 'article'
  | 'avatar'
  | 'workshop';

interface ActionItem {
  key: string;
  title: string;
  description: string;
  theme: IconTheme;
  icon: ReactNode;
}

/* =========================================================
 * Data
 * ========================================================= */

const createItems: ActionItem[] = [
  {
    key: 'data-source',
    title: '数据源管理',
    description: '连接和管理你的数据源',
    theme: 'avatar',
    icon: <Database size={18} strokeWidth={2.2} />,
  },
  {
    key: 'offline-sync',
    title: '离线同步',
    description: '创建和管理离线同步任务',
    theme: 'workshop',
    icon: <ArrowRightLeft size={18} strokeWidth={2.2} />,
  },
];

const publishItems: ActionItem[] = [
  {
    key: 'development',
    title: '数据开发',
    description: '编写、调试和发布数据任务',
    theme: 'video',
    icon: <Code2 size={18} strokeWidth={2.2} />,
  },
  {
    key: 'workflow',
    title: '工作流',
    description: '编排任务并配置运行调度',
    theme: 'image',
    icon: <Workflow size={18} strokeWidth={2.2} />,
  },
  {
    key: 'quality',
    title: '数据质量',
    description: '监控并检查数据质量',
    theme: 'panorama',
    icon: <ShieldCheck size={18} strokeWidth={2.2} />,
  },
  {
    key: 'application',
    title: '数据应用',
    description: '分析、共享和服务数据',
    theme: 'article',
    icon: <BarChart3 size={18} strokeWidth={2.2} />,
  },
];

/* =========================================================
 * Icon Theme
 * ========================================================= */

const themeStyles: Record<
  IconTheme,
  {
    panel: string;
    core: string;
    glow: string;
    iconHover: string;
  }
> = {
  video: {
    panel: 'bg-gradient-to-b from-[#ff94a9] to-[#ffe9ed]',
    core: 'bg-[#fe2c55] shadow-[inset_0_-2px_2px_0_#ff5b6f]',
    glow: 'bg-[#ff58a9]',
    iconHover: 'group-hover:rotate-[360deg]',
  },

  image: {
    panel: 'bg-gradient-to-b from-[#79c8ff] to-[#e4f5ff]',
    core:
      'bg-gradient-to-br from-[#28b8ff] to-[#168cf4] shadow-[inset_0_-2px_2px_rgba(0,93,231,0.28)]',
    glow: 'bg-[#63e3ff]',
    iconHover:
      'group-hover:-translate-y-[1px] group-hover:rotate-[8deg]',
  },

  panorama: {
    panel: 'bg-gradient-to-b from-[#aaa1ff] to-[#eeebff]',
    core:
      'bg-gradient-to-br from-[#8374ff] to-[#6657f5] shadow-[inset_0_-2px_2px_rgba(71,54,228,0.32)]',
    glow: 'bg-[#c677ff]',
    iconHover: 'group-hover:rotate-[360deg]',
  },

  article: {
    panel: 'bg-gradient-to-b from-[#ffd65c] to-[#fff3c9]',
    core:
      'bg-gradient-to-br from-[#ffcb24] to-[#ffb900] shadow-[inset_0_-2px_2px_rgba(225,152,0,0.25)]',
    glow: 'bg-[#fff27d]',
    iconHover:
      'group-hover:-translate-y-[2px] group-hover:rotate-[-4deg]',
  },

  avatar: {
    panel: 'bg-gradient-to-b from-[#89d6f6] to-[#e6f7fe]',
    core:
      'bg-gradient-to-br from-[#3cc2ef] to-[#278ee6] shadow-[inset_0_-2px_2px_rgba(22,112,193,0.24)]',
    glow: 'bg-[#84eeff]',
    iconHover: 'group-hover:rotate-[12deg]',
  },

  workshop: {
    panel: 'bg-gradient-to-b from-[#a5ccff] to-[#eef6ff]',
    core:
      'bg-gradient-to-br from-[#6d9cff] to-[#6468f3] shadow-[inset_0_-2px_2px_rgba(60,73,214,0.25)]',
    glow: 'bg-[#a8c9ff]',
    iconHover: 'group-hover:rotate-[-15deg]',
  },
};

/* =========================================================
 * Layered Icon
 *
 * 对应原页面：
 *
 * 77 × 84 icon layer
 * ├── 60 × 75 后置倾斜玻璃板
 * └── 60 × 75 前置渐变玻璃板
 *      └── 32 × 32 核心 icon
 * ========================================================= */

interface LayeredIconProps {
  theme: IconTheme;
  icon: ReactNode;
}

function LayeredIcon({ theme, icon }: LayeredIconProps) {
  const styles = themeStyles[theme];

  return (
    <div
      className="
        pointer-events-none
        absolute
        -left-px
        -top-[5px]
        z-[1]
        h-[84px]
        w-[77px]
      "
    >
      {/* 后置倾斜玻璃层 */}
      <div
        className="
          absolute
          left-[3px]
          top-0
          h-[75px]
          w-[60px]
          rotate-[10deg]
          skew-x-[-1.54deg]
          rounded-[12px]
          border
          border-white
          bg-[#d6d7d9]/40
          backdrop-blur-[6px]
        "
      />

      {/* 前置渐变玻璃层 */}
      <div
        className={`
          absolute
          left-0
          top-[5px]
          flex
          h-[75px]
          w-[60px]
          items-center
          justify-center
          overflow-hidden
          rounded-[12px]
          border
          border-white
          backdrop-blur-[6px]
          ${styles.panel}
        `}
      >
        {/* 核心 32 × 32 */}
        <div
          className={`
            relative
            flex
            h-8
            w-8
            shrink-0
            items-center
            justify-center
            overflow-hidden
            rounded-lg
            text-white
            ${styles.core}
          `}
        >
          {/* 左上光斑 */}
          <span
            className={`
              absolute
              -left-[7px]
              -top-2
              h-[23px]
              w-[23px]
              rounded-full
              blur-[5px]
              transition-all
              duration-500
              ease-in-out
              group-hover:translate-x-[5px]
              group-hover:translate-y-[3px]
              ${styles.glow}
            `}
          />

          {/* 右下白色光斑 */}
          <span
            className="
              absolute
              left-[18px]
              top-[21px]
              h-[18px]
              w-[18px]
              rounded-full
              bg-white/50
              blur-[5px]
              transition-all
              duration-500
              ease-in-out
              group-hover:-translate-x-[4px]
              group-hover:-translate-y-[4px]
            "
          />

          {/* 白色图形 */}
          <span
            className={`
              relative
              z-[3]
              flex
              h-5
              w-5
              items-center
              justify-center
              text-white
              drop-shadow-[0_1px_1px_rgba(0,0,0,0.05)]
              transition-transform
              duration-500
              ease-in-out
              ${styles.iconHover}
            `}
          >
            {icon}
          </span>
        </div>
      </div>
    </div>
  );
}

/* =========================================================
 * Action Card
 * ========================================================= */

interface ActionCardProps {
  item: ActionItem;
  onClick?: () => void;
}

function ActionCard({ item, onClick }: ActionCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="
        group
        relative
        flex
        h-[75px]
        w-full
        items-center
        overflow-visible
        rounded-[18px]
        border
        border-[rgba(31,35,41,0.075)]
        bg-white/[0.96]
        pr-4
        text-left
        shadow-[0_5px_12px_rgba(31,35,41,0.055),0_1px_2px_rgba(31,35,41,0.025)]
        transition-[background-color,border-color,box-shadow]
        duration-200
        ease-out
        hover:border-[rgba(31,35,41,0.10)]
        hover:bg-white
        hover:shadow-[0_6px_15px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.025)]
      "
    >
      {/* Icon 占位 */}
      <div className="relative h-[75px] w-[77px] shrink-0">
        <LayeredIcon theme={item.theme} icon={item.icon} />
      </div>

      {/* Text */}
      <div className="ml-2 min-w-0">
        <div
          className="
            truncate
            text-[15px]
            font-semibold
            leading-[22px]
            text-[#292c35]
          "
        >
          {item.title}
        </div>

        <div
          className="
            mt-0.5
            truncate
            text-[13px]
            font-normal
            leading-5
            text-[#9498a1]
          "
        >
          {item.description}
        </div>
      </div>
    </button>
  );
}

/* =========================================================
 * Profile Stat
 * ========================================================= */

interface ProfileStatProps {
  label: string;
  value: number;
  arrow?: boolean;
}

function ProfileStat({
  label,
  value,
  arrow = false,
}: ProfileStatProps) {
  return (
    <button
      type="button"
      className="
        flex
        items-center
        border-0
        bg-transparent
        p-0
        text-sm
        leading-[22px]
        text-[#747983]
        transition-colors
        duration-200
        hover:text-[#292c35]
      "
    >
      <span>{label}</span>

      <strong className="ml-[5px] font-semibold text-[#282b34]">
        {value}
      </strong>

      {arrow && (
        <ChevronRight
          size={14}
          strokeWidth={1.8}
          className="ml-[3px] text-[#9599a2]"
        />
      )}
    </button>
  );
}

/* =========================================================
 * Section
 * ========================================================= */

interface SectionProps {
  title: string;
  children: ReactNode;
  className?: string;
}

function Section({
  title,
  children,
  className = '',
}: SectionProps) {
  return (
    <section
      className={`
        min-h-[258px]
        rounded-[22px]
        border
        border-[#f1f1f1]
        bg-white/[0.68]
        px-[22px]
        pb-6
        pt-6
        backdrop-blur-[8px]
        ${className}
      `}
    >
      <h2
        className="
          mb-5
          text-xl
          font-semibold
          leading-7
          tracking-[-0.35px]
          text-[#252832]
        "
      >
        {title}
      </h2>

      {children}
    </section>
  );
}

/* =========================================================
 * Data Center
 * ========================================================= */

type OverviewTabKey = 'overview' | 'recent' | 'schedule';
type PeriodKey = 'yesterday' | '7d' | '30d';

interface OverviewMetric {
  label: string;
  value: string;
  compareLabel: string;
  compareValue: string;
  tone?: 'positive' | 'negative' | 'neutral';
}

interface ActivityOverviewItem {
  title: string;
  type: string;
  date: string;
}

const overviewTabs: Array<{ key: OverviewTabKey; label: string }> = [
  { key: 'overview', label: '运行总览' },
  { key: 'recent', label: '近期任务' },
  { key: 'schedule', label: '调度数据' },
];

const periodOptions: Array<{ key: PeriodKey; label: string }> = [
  { key: 'yesterday', label: '昨天' },
  { key: '7d', label: '近7日' },
  { key: '30d', label: '近30日' },
];

const overviewMetrics: OverviewMetric[] = [
  {
    label: '成功任务',
    value: '128',
    compareLabel: '较前7日',
    compareValue: '+12',
    tone: 'positive',
  },
  {
    label: '运行中',
    value: '3',
    compareLabel: '较前7日',
    compareValue: '+1',
    tone: 'negative',
  },
  {
    label: '失败任务',
    value: '1',
    compareLabel: '较前7日',
    compareValue: '-2',
    tone: 'positive',
  },
  {
    label: '调度次数',
    value: '162',
    compareLabel: '较前7日',
    compareValue: '+8',
    tone: 'neutral',
  },
  {
    label: '处理记录',
    value: '24.8万',
    compareLabel: '较前7日',
    compareValue: '+6.4%',
    tone: 'positive',
  },
  {
    label: '平均耗时',
    value: '42s',
    compareLabel: '较前7日',
    compareValue: '-5s',
    tone: 'positive',
  },
];

const activityOverviewItems: ActivityOverviewItem[] = [
  {
    title: '数据源连接巡检',
    type: '数据源',
    date: '08-01~08-31',
  },
  {
    title: '离线同步任务周巡检',
    type: '集成',
    date: '08-10~08-16',
  },
  {
    title: '数据质量规则扫描',
    type: '质量',
    date: '08-17~08-23',
  },
];

const pad2 = (value: number) => String(value).padStart(2, '0');

const formatDate = (date: Date) =>
  `${date.getFullYear()}.${pad2(date.getMonth() + 1)}.${pad2(date.getDate())}`;

const formatAxisDate = (date: Date) =>
  `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

function buildPeriod(periodKey: PeriodKey, reference = new Date()) {
  const today = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate(),
  );
  const end = new Date(today);
  end.setDate(today.getDate() - 1);

  if (periodKey === 'yesterday') {
    const hours = ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'];
    return {
      start: end,
      end,
      labels: hours,
      values: [8, 14, 11, 18, 15, 22, 17],
    };
  }

  const count = periodKey === '30d' ? 30 : 7;
  const start = new Date(end);
  start.setDate(end.getDate() - (count - 1));

  const days = Array.from({ length: count }, (_, index) => {
    const current = new Date(start);
    current.setDate(start.getDate() + index);
    return current;
  });

  const values =
    periodKey === '30d'
      ? [
          12, 13, 15, 14, 16, 18, 17, 19, 18, 21,
          20, 22, 24, 23, 25, 24, 26, 28, 27, 29,
          30, 28, 31, 34, 36, 35, 33, 31, 29, 27,
        ]
      : [18, 18, 18, 18, 20, 36, 22];

  return {
    start,
    end,
    labels: days.map(formatAxisDate),
    values,
  };
}

interface TrendChartProps {
  values: number[];
  labels: string[];
  name: string;
}

function TrendChart({ values, labels, name }: TrendChartProps) {
  const option = useMemo<EChartsOption>(
    () => ({
      animation: true,
      animationDuration: 720,
      animationDurationUpdate: 620,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
      grid: {
        left: 0,
        right: 2,
        top: 18,
        bottom: 26,
        containLabel: false,
      },
      tooltip: {
        trigger: 'axis',
        confine: true,
        appendToBody: false,
        backgroundColor: 'rgba(255,255,255,0.98)',
        borderColor: '#edf0f4',
        borderWidth: 1,
        padding: [10, 12],
        textStyle: {
          color: '#30333b',
          fontSize: 12,
        },
        extraCssText:
          'box-shadow:0 8px 28px rgba(31,35,41,.10);border-radius:8px;',
        axisPointer: {
          type: 'line',
          lineStyle: {
            color: '#dfe6f4',
            width: 1,
          },
        },
        formatter: (params: any) => {
          const item = Array.isArray(params) ? params[0] : params;
          if (!item) return '';
          return `
            <div style="min-width:120px">
              <div style="font-size:12px;color:#555a64;margin-bottom:8px">${item.axisValue}</div>
              <div style="display:flex;align-items:center;justify-content:space-between;gap:20px">
                <span style="display:flex;align-items:center;color:#30333b;font-weight:600">
                  <i style="display:inline-block;width:7px;height:7px;border-radius:999px;background:#5b8cff;margin-right:6px"></i>
                  ${name}
                </span>
                <b style="font-size:12px;color:#30333b">${item.data}</b>
              </div>
            </div>
          `;
        },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: labels,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: {
          color: '#8d929b',
          fontSize: 11,
          margin: 9,
          interval: labels.length > 10 ? 4 : 0,
          showMinLabel: true,
          showMaxLabel: true,
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: Math.max(...values, 1) * 1.2,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { show: false },
        splitLine: { show: false },
      },
      series: [
        {
          name,
          type: 'line',
          data: values,
          smooth: 0.42,
          showSymbol: false,
          symbol: 'circle',
          symbolSize: 6,
          lineStyle: {
            color: '#5b8cff',
            width: 1.5,
          },
          itemStyle: {
            color: '#5b8cff',
            borderColor: '#ffffff',
            borderWidth: 2,
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(91,140,255,0.18)' },
                { offset: 1, color: 'rgba(91,140,255,0.00)' },
              ],
            },
          },
          emphasis: {
            focus: 'series',
            scale: true,
          },
        },
      ],
    }),
    [labels, name, values],
  );

  return (
    <ReactECharts
      option={option}
      notMerge
      lazyUpdate
      style={{ height: 152, width: '100%' }}
    />
  );
}

function LatestTaskCard() {
  return (
    <aside className="w-full shrink-0 lg:w-[220px] lg:border-r lg:border-[#edf0f3] lg:pr-5">
      <div className="mb-2 text-[13px] font-semibold leading-5 text-[#353842]">
        最新任务
      </div>

      <button
        type="button"
        className="group relative h-[266px] w-full overflow-hidden rounded-[10px] border border-[#e8ebef] bg-[linear-gradient(150deg,#6c737d_0%,#9197a0_48%,#c0c4ca_100%)] text-left text-white transition-[border-color,transform] duration-200 hover:-translate-y-px hover:border-[#dfe3e8] lg:w-[198px]"
      >
        <div className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(255,255,255,.28)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.22)_1px,transparent_1px)] [background-size:22px_22px]" />
        <div className="pointer-events-none absolute -right-9 top-10 h-32 w-32 rounded-full border border-white/20" />
        <div className="pointer-events-none absolute -right-2 top-16 h-24 w-24 rounded-full border border-white/20" />

        <div className="absolute left-4 top-3 z-10">
          <div className="text-[12px] font-semibold text-white/95">SQL</div>
          <div className="mt-0.5 text-[11px] text-white/80">00:42</div>
        </div>

        <Copy
          size={14}
          strokeWidth={1.8}
          className="absolute right-3 top-3 z-10 text-white/90"
        />

        <div className="absolute inset-x-0 top-[50px] z-10 flex justify-center">
          <div className="relative flex h-[122px] w-[122px] items-center justify-center">
            <span className="absolute h-[112px] w-[112px] rounded-full border border-white/25" />
            <span className="absolute h-[82px] w-[82px] rounded-full border border-white/18" />
            <span className="absolute h-[52px] w-[52px] rounded-full bg-white/12 backdrop-blur-[2px]" />
            <Database size={52} strokeWidth={1.15} className="relative text-white/95" />
          </div>
        </div>

        <div className="absolute inset-x-0 bottom-[70px] z-10 px-4">
          <div className="truncate text-[12px] font-medium text-white/95">
            ods_user_profile_sync
          </div>
        </div>

        <div className="absolute inset-x-0 bottom-0 z-10 h-[70px] bg-black/20 px-4 backdrop-blur-[12px]">
          <div className="flex h-1/2 items-center justify-between border-b border-white/12">
            <span className="text-[11px] text-white/78">运行次数</span>
            <strong className="text-[12px] font-semibold">17</strong>
          </div>
          <div className="flex h-1/2 items-center justify-between">
            <span className="text-[11px] text-white/78">异常</span>
            <strong className="text-[12px] font-semibold">1</strong>
          </div>
        </div>
      </button>
    </aside>
  );
}

function PeriodSelect({
  value,
  onChange,
}: {
  value: PeriodKey;
  onChange: (value: PeriodKey) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const current = periodOptions.find((option) => option.key === value)!;

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, []);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((currentOpen) => !currentOpen)}
        className={`
          flex h-[27px] items-center rounded-[7px] border px-2.5 text-[12px]
          transition-colors
          ${
            open
              ? 'border-[#9db7ef] bg-[#f7f9fd] text-[#454a54]'
              : 'border-transparent bg-[#f4f5f7] text-[#5f646e] hover:bg-[#eceef2]'
          }
        `}
      >
        <span className="pr-2 text-[#727781]">时间</span>
        <span className="mr-1.5 h-[12px] w-px bg-[#dcdfe4]" />
        <span className="min-w-[34px] text-left font-medium text-[#4d525c]">
          {current.label}
        </span>
        <ChevronDown
          size={13}
          strokeWidth={1.8}
          className={`ml-1 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div className="absolute right-0 top-[31px] z-30 w-[116px] overflow-hidden rounded-[8px] border border-[#eceef2] bg-white py-1 shadow-[0_8px_22px_rgba(31,35,41,0.10)]">
          {periodOptions.map((option) => {
            const selected = option.key === value;
            return (
              <button
                key={option.key}
                type="button"
                onClick={() => {
                  onChange(option.key);
                  setOpen(false);
                }}
                className="flex h-[34px] w-full items-center px-3 text-left text-[12px] text-[#444952] transition-colors hover:bg-[#f6f7f9]"
              >
                <span className="flex w-5 items-center">
                  {selected && <Check size={14} strokeWidth={2} />}
                </span>
                {option.label}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function OverviewMetrics() {
  return (
    <div className="mt-1 grid grid-cols-2 gap-x-2 gap-y-1 sm:grid-cols-3">
      {overviewMetrics.map((metric) => (
        <div
          key={metric.label}
          className="group min-w-0 rounded-[6px] px-3 py-2 transition-colors duration-150 hover:bg-[#f7f8fa]"
        >
          <div className="text-[12px] font-semibold leading-5 text-[#454951]">
            {metric.label}
          </div>
          <div className="mt-0.5 flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <strong className="text-[20px] font-semibold leading-7 tracking-[-0.4px] text-[#272a33]">
              {metric.value}
            </strong>
            <span className="text-[11px] text-[#989ca4]">
              {metric.compareLabel}
              <span
                className={`ml-0.5 font-medium ${
                  metric.tone === 'positive'
                    ? 'text-[#20a464]'
                    : metric.tone === 'negative'
                      ? 'text-[#f04c5a]'
                      : 'text-[#7b8089]'
                }`}
              >
                {metric.compareValue}
              </span>
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

function RecentTasksPanel() {
  return (
    <div className="min-h-[263px] pt-2">
      <button
        type="button"
        className="group grid w-full grid-cols-[minmax(230px,1.5fr)_repeat(4,minmax(72px,.55fr))_150px] items-center gap-3 rounded-[8px] px-3 py-3 text-left transition-colors hover:bg-[#f7f8fa]"
      >
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#edf4ff] text-[#5b8cff]">
            <Database size={16} strokeWidth={1.9} />
          </div>
          <div className="min-w-0">
            <div className="truncate text-[12px] font-medium text-[#363a43]">
              ods_user_profile_sync
            </div>
            <div className="mt-1 flex items-center gap-1 text-[11px] text-[#969aa3]">
              <Clock3 size={11} strokeWidth={1.8} />
              今日 10:42
            </div>
          </div>
        </div>

        {[
          ['运行', '17'],
          ['成功', '16'],
          ['失败', '1'],
          ['耗时', '42s'],
        ].map(([label, value]) => (
          <div key={label} className="min-w-0">
            <span className="text-[11px] text-[#969aa3]">{label}</span>
            <strong className="ml-2 text-[12px] font-semibold text-[#3b3f48]">
              {value}
            </strong>
          </div>
        ))}

        <div className="flex items-center justify-end gap-4">
          <span className="text-[11px] font-medium text-[#20a464]">成功</span>
          <span className="text-[12px] font-semibold text-[#323640] transition-colors group-hover:text-[#5b8cff]">
            查看详情
          </span>
        </div>
      </button>
    </div>
  );
}

function EmptySchedulePanel({ periodLabel }: { periodLabel: string }) {
  return (
    <div className="flex min-h-[263px] items-center justify-center pb-4">
      <div className="text-center">
        <div className="relative mx-auto flex h-[78px] w-[112px] items-center justify-center">
          <span className="absolute left-[19px] top-[5px] flex h-7 w-7 items-center justify-center rounded-full bg-[#d9e7ff] text-[#4b7df3]">
            <CircleHelp size={18} strokeWidth={2.1} />
          </span>
          <span className="absolute bottom-2 left-[38px] h-[40px] w-[54px] rounded-[12px] border border-[#dfe3e9] bg-[#fafbfc]" />
          <RadioTower
            size={45}
            strokeWidth={1.25}
            className="absolute bottom-[7px] right-[22px] text-[#9ba2ad]"
          />
        </div>
        <div className="mt-1 text-[12px] text-[#8a8f98]">
          {periodLabel}暂无调度数据
        </div>
        <div className="mt-1 text-[11px] text-[#b0b4bb]">
          当前周期内没有可展示的调度记录
        </div>
      </div>
    </div>
  );
}

function DataCenter() {
  const [activeTab, setActiveTab] = useState<OverviewTabKey>('overview');
  const [periodKey, setPeriodKey] = useState<PeriodKey>('7d');
  const period = useMemo(() => buildPeriod(periodKey), [periodKey]);
  const periodLabel = periodOptions.find((item) => item.key === periodKey)!.label;

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <h2 className="shrink-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            数据中心
          </h2>
          <CircleHelp size={14} strokeWidth={1.9} className="shrink-0 text-[#a0a4ac]" />
          <span className="ml-0.5 hidden text-[12px] leading-5 text-[#8d929b] sm:inline">
            统计周期：{formatDate(period.start)}-{formatDate(period.end)}（每天12点更新）
          </span>
        </div>

        <button
          type="button"
          className="flex items-center gap-0.5 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
        >
          查看更多
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      </header>

      <div className="mt-4 flex flex-col gap-5 lg:flex-row lg:gap-6">
        <LatestTaskCard />

        <div className="min-w-0 flex-1">
          <div className="flex min-h-[35px] items-end justify-between border-b border-[#eceef2]">
            <div className="flex items-center gap-5 sm:gap-7">
              {overviewTabs.map((tab) => {
                const active = activeTab === tab.key;
                return (
                  <button
                    key={tab.key}
                    type="button"
                    onClick={() => setActiveTab(tab.key)}
                    className={`
                      relative h-[35px] pb-2 text-[13px] transition-colors
                      ${
                        active
                          ? 'font-semibold text-[#292c35]'
                          : 'font-normal text-[#858a93] hover:text-[#4a4f59]'
                      }
                    `}
                  >
                    {tab.label}
                    <span
                      className={`absolute inset-x-0 -bottom-px h-[2px] origin-center rounded-full bg-[#252832] transition-transform duration-200 ${
                        active ? 'scale-x-100' : 'scale-x-0'
                      }`}
                    />
                  </button>
                );
              })}
            </div>

            {activeTab !== 'recent' && (
              <div className="mb-1.5">
                <PeriodSelect value={periodKey} onChange={setPeriodKey} />
              </div>
            )}
          </div>

          {activeTab === 'overview' && (
            <div>
              <div className="mt-2 flex items-center justify-end gap-1.5 text-[12px] text-[#7f848e]">
                <span className="h-2 w-2 rounded-full bg-[#5b8cff]" />
                运行次数
              </div>
              <TrendChart
                key={`trend-${periodKey}`}
                values={period.values}
                labels={period.labels}
                name="运行次数"
              />
              <OverviewMetrics />
            </div>
          )}

          {activeTab === 'recent' && <RecentTasksPanel />}

          {activeTab === 'schedule' && (
            <EmptySchedulePanel periodLabel={periodLabel} />
          )}
        </div>
      </div>
    </section>
  );
}

/* =========================================================
 * Activity Center
 * ========================================================= */

interface CalendarCell {
  day: number | null;
  date: Date | null;
  isToday: boolean;
}

function buildCalendarWeeks(cursor: Date): CalendarCell[][] {
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = new Date();

  const cells: CalendarCell[] = Array.from({ length: 42 }, (_, index) => {
    const day = index - firstDay + 1;

    if (day < 1 || day > daysInMonth) {
      return {
        day: null,
        date: null,
        isToday: false,
      };
    }

    const date = new Date(year, month, day);
    const isToday =
      today.getFullYear() === year &&
      today.getMonth() === month &&
      today.getDate() === day;

    return {
      day,
      date,
      isToday,
    };
  });

  return Array.from({ length: 6 }, (_, weekIndex) =>
    cells.slice(weekIndex * 7, weekIndex * 7 + 7),
  );
}

function CalendarWeek({ cells }: { cells: CalendarCell[] }) {
  const activeIndexes = cells
    .map((cell, index) => (cell.day ? index : -1))
    .filter((index) => index >= 0);
  const start = activeIndexes[0];
  const end = activeIndexes[activeIndexes.length - 1];

  return (
    <div className="relative grid h-[39px] grid-cols-7 items-center">
      {cells.map((cell, index) => (
        <div
          key={`${cell.day ?? 'empty'}-${index}`}
          className="relative flex h-full items-center justify-center text-[12px] text-[#353943]"
        >
          {cell.day && (
            <span
              className={`
                flex h-7 min-w-7 items-center justify-center rounded-full px-1
                ${cell.isToday ? 'bg-[#eef4ff] font-semibold text-[#356fe8]' : ''}
              `}
            >
              {pad2(cell.day)}
            </span>
          )}
        </div>
      ))}

      {start !== undefined && end !== undefined && (
        <span
          className="pointer-events-none absolute bottom-0 h-[3px] rounded-full bg-[#bfd3ff]"
          style={{
            left: `calc(${(start / 7) * 100}% + 8px)`,
            width: `calc(${((end - start + 1) / 7) * 100}% - 16px)`,
          }}
        />
      )}
    </div>
  );
}

function ActivityCenter() {
  const now = useMemo(() => new Date(), []);
  const [cursor, setCursor] = useState(
    () => new Date(now.getFullYear(), now.getMonth(), 1),
  );
  const weeks = useMemo(() => buildCalendarWeeks(cursor), [cursor]);
  const monthLabel = `${cursor.getFullYear()}年${pad2(cursor.getMonth() + 1)}月`;

  const moveMonth = (offset: number) => {
    setCursor(
      (current) =>
        new Date(current.getFullYear(), current.getMonth() + offset, 1),
    );
  };

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-start justify-between gap-4">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          活动中心
        </h2>

        <div className="flex flex-col items-end gap-2">
          <button
            type="button"
            className="flex items-center gap-0.5 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
          >
            查看更多
            <ChevronRight size={14} strokeWidth={1.8} />
          </button>
          <span className="flex items-center gap-1.5 text-[11px] text-[#8b9099]">
            <span className="h-2 w-2 rounded-full bg-[#5b8cff]" />
            进行中
          </span>
        </div>
      </header>

      <div className="mt-4">
        <div className="flex items-center justify-center gap-2">
          <button
            type="button"
            onClick={() => moveMonth(-1)}
            className="flex h-7 w-7 items-center justify-center rounded-full text-[#91959d] transition-colors hover:bg-[#f5f6f8] hover:text-[#4a4f58]"
            aria-label="上个月"
          >
            <ChevronLeft size={16} strokeWidth={1.8} />
          </button>

          <div className="min-w-[112px] text-center text-[14px] font-semibold text-[#333741]">
            {monthLabel}
          </div>

          <button
            type="button"
            onClick={() => moveMonth(1)}
            className="flex h-7 w-7 items-center justify-center rounded-full text-[#91959d] transition-colors hover:bg-[#f5f6f8] hover:text-[#4a4f58]"
            aria-label="下个月"
          >
            <ChevronRight size={16} strokeWidth={1.8} />
          </button>
        </div>

        <div className="mt-2 grid grid-cols-7 text-center text-[11px] text-[#6d727c]">
          {['日', '一', '二', '三', '四', '五', '六'].map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>

        <div className="mt-1">
          {weeks.map((week, index) => (
            <CalendarWeek key={index} cells={week} />
          ))}
        </div>
      </div>

      <div className="mt-4 border-t border-[#eef0f3] pt-4">
        <div className="flex items-center justify-between">
          <strong className="text-[13px] font-semibold text-[#3c4049]">
            {cursor.getMonth() + 1}月活动总览
          </strong>
          <span className="text-[11px] text-[#999da5]">共9个进行中</span>
        </div>

        <div className="mt-2.5 space-y-2">
          {activityOverviewItems.map((item) => (
            <button
              key={item.title}
              type="button"
              className="group flex w-full items-center gap-2 text-left"
            >
              <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#5b8cff]" />
              <span className="min-w-0 flex-1 truncate text-[12px] text-[#464a53] transition-colors group-hover:text-[#252832]">
                {item.title}
              </span>
              <span className="shrink-0 rounded border border-[#eceef2] px-1.5 py-0.5 text-[10px] leading-4 text-[#8d929a]">
                {item.type}
              </span>
              <span className="w-[74px] shrink-0 text-right text-[10px] text-[#9da1a8]">
                {item.date}
              </span>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}

/* =========================================================
 * Home secondary panels
 * ========================================================= */

function HomeSecondaryPanels() {
  return (
    <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_410px]">
      <DataCenter />
      <ActivityCenter />
    </div>
  );
}


/* =========================================================
 * Page
 * ========================================================= */

export default function CreatorWorkbenchPage() {
  const handleAction = (key: string) => {
    console.log('creator action:', key);

    switch (key) {
      case 'data-source':
        // TODO 数据源管理
        break;

      case 'offline-sync':
        // TODO 离线同步
        break;

      case 'development':
        // TODO 数据开发
        break;

      case 'workflow':
        // TODO 工作流
        break;

      case 'quality':
        // TODO 数据质量
        break;

      case 'application':
        // TODO 数据应用
        break;

      default:
        break;
    }
  };

  return (
    <div
      className="
        relative
        min-h-screen
        w-full
        overflow-hidden
        bg-[#f7f8fa]
        text-[#242731]
      "
    >
      {/* =====================================================
          背景
      ====================================================== */}

      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        {/* 顶部基础背景 */}
        <div
          className="
            absolute
            inset-x-0
            top-0
            h-[170px]
            bg-gradient-to-b
            from-[#f9fafb]
            to-transparent
          "
        />

        {/* 右上浅蓝 */}
        <div
          className="
            absolute
            -right-[60px]
            -top-[180px]
            h-[420px]
            w-[76%]
            bg-[radial-gradient(ellipse_at_center,rgba(159,215,239,0.48)_0%,rgba(187,224,240,0.23)_45%,rgba(255,255,255,0)_74%)]
            blur-[12px]
          "
        />

        {/* 右下浅黄 */}
        <div
          className="
            absolute
            -bottom-[180px]
            -right-[160px]
            h-[420px]
            w-[620px]
            bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.47),rgba(255,255,255,0)_70%)]
            blur-[16px]
          "
        />

        {/* 顶部细纹理 */}
        <div
          className="
            absolute
            right-0
            top-0
            h-[118px]
            w-[73%]
            opacity-[0.16]
            [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)]
            [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_100%)]
          "
        />
      </div>

      {/* =====================================================
          Content
      ====================================================== */}

      <div className="relative z-10">
        {/* ===================================================
            Header
        ==================================================== */}

        <header className="flex h-[116px] items-center px-4">
          <div className="flex items-center">
            {/* Avatar */}
            <div
              className="
                flex
                h-[66px]
                w-[66px]
                shrink-0
                items-center
                justify-center
                overflow-hidden
                rounded-full
                border
                border-white/90
                bg-gradient-to-br
                from-[#dde6ef]
                via-[#a6c8e2]
                to-[#5e93d4]
                text-white/95
                shadow-[0_2px_4px_rgba(31,35,41,0.04)]
              "
            >
              <Database size={30} strokeWidth={1.5} />
            </div>

            {/* Info */}
            <div className="ml-4 min-w-0">
              {/* 第一行 */}
              <div className="flex min-h-[22px] items-center">
                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-medium
                    leading-[22px]
                    text-[#252830]
                  "
                >
                  Yak Ops
                </span>

                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />

                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  Data Operations Platform
                </span>

                <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />

                <span
                  className="
                    whitespace-nowrap
                    text-sm
                    font-normal
                    leading-[22px]
                    text-[#777b84]
                  "
                >
                  让数据接入、开发、编排、质量与消费变得更简单
                </span>
              </div>

              {/* 第二行 */}
              <div className="mt-2.5 flex items-center gap-[27px]">
                <ProfileStat
                  label="数据源"
                  value={0}
                  arrow
                />

                <ProfileStat
                  label="运行中"
                  value={0}
                  arrow
                />

                <ProfileStat
                  label="今日异常"
                  value={0}
                />
              </div>
            </div>
          </div>
        </header>

        {/* ===================================================
            Main
        ==================================================== */}

        <main className="px-4 pb-4">
          <div
            className="
              grid
              grid-cols-1
              gap-4
              min-[901px]:grid-cols-[330px_minmax(0,1fr)]
              min-[1101px]:grid-cols-[398px_minmax(0,1fr)]
            "
          >
            {/* =================================================
                数据接入
            ================================================== */}

            <Section title="数据接入">
              <div className="grid gap-[13px]">
                {createItems.map((item) => (
                  <ActionCard
                    key={item.key}
                    item={item}
                    onClick={() => handleAction(item.key)}
                  />
                ))}
              </div>
            </Section>

            {/* =================================================
                数据工作
            ================================================== */}

            <Section
              title="数据工作"
              className="bg-white/[0.74]"
            >
              <div
                className="
                  grid
                  grid-cols-1
                  gap-x-[14px]
                  gap-y-[13px]
                  sm:grid-cols-2
                "
              >
                {publishItems.map((item) => (
                  <ActionCard
                    key={item.key}
                    item={item}
                    onClick={() => handleAction(item.key)}
                  />
                ))}
              </div>
            </Section>
          </div>

          <HomeSecondaryPanels />
        </main>
      </div>
    </div>
  );
}