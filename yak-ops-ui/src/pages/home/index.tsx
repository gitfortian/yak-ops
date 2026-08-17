import {
  ArrowRightLeft,
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Code2,
  Database,
  ShieldCheck,
  Workflow,
} from 'lucide-react';
import { useMemo, useState, type ReactNode } from 'react';
import DataCenter from './DataCenter';

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
    iconHover: 'group-hover:-translate-y-[1px] group-hover:rotate-[8deg]',
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
    iconHover: 'group-hover:-translate-y-[2px] group-hover:rotate-[-4deg]',
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
        pointer-events-none absolute -left-px -top-[5px] z-[1]
        h-[84px] w-[77px]
      "
    >
      <div
        className="
          absolute left-[3px] top-0 h-[75px] w-[60px]
          rotate-[10deg] skew-x-[-1.54deg] rounded-[12px]
          border border-white bg-[#d6d7d9]/40 backdrop-blur-[6px]
        "
      />
      <div
        className={`
          absolute left-0 top-[5px] flex h-[75px] w-[60px]
          items-center justify-center overflow-hidden rounded-[12px]
          border border-white backdrop-blur-[6px] ${styles.panel}
        `}
      >
        <div
          className={`
            relative flex h-8 w-8 shrink-0 items-center justify-center
            overflow-hidden rounded-lg text-white ${styles.core}
          `}
        >
          <span
            className={`
              absolute -left-[7px] -top-2 h-[23px] w-[23px] rounded-full
              blur-[5px] transition-all duration-500 ease-in-out
              group-hover:translate-x-[5px] group-hover:translate-y-[3px]
              ${styles.glow}
            `}
          />
          <span
            className="
              absolute left-[18px] top-[21px] h-[18px] w-[18px] rounded-full
              bg-white/50 blur-[5px] transition-all duration-500 ease-in-out
              group-hover:-translate-x-[4px] group-hover:-translate-y-[4px]
            "
          />
          <span
            className={`
              relative z-[3] flex h-5 w-5 items-center justify-center text-white
              drop-shadow-[0_1px_1px_rgba(0,0,0,0.05)] transition-transform
              duration-500 ease-in-out ${styles.iconHover}
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
        group relative flex h-[75px] w-full items-center overflow-visible
        rounded-[18px] border border-[rgba(31,35,41,0.075)] bg-white/[0.96]
        pr-4 text-left shadow-[0_5px_12px_rgba(31,35,41,0.055),0_1px_2px_rgba(31,35,41,0.025)]
        transition-[background-color,border-color,box-shadow] duration-200 ease-out
        hover:border-[rgba(31,35,41,0.10)] hover:bg-white
        hover:shadow-[0_6px_15px_rgba(31,35,41,0.065),0_1px_2px_rgba(31,35,41,0.025)]
      "
    >
      <div className="relative h-[75px] w-[77px] shrink-0">
        <LayeredIcon theme={item.theme} icon={item.icon} />
      </div>
      <div className="ml-2 min-w-0">
        <div className="truncate text-[15px] font-semibold leading-[22px] text-[#292c35]">
          {item.title}
        </div>
        <div className="mt-0.5 truncate text-[13px] font-normal leading-5 text-[#9498a1]">
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

function ProfileStat({ label, value, arrow = false }: ProfileStatProps) {
  return (
    <button
      type="button"
      className="
        flex items-center border-0 bg-transparent p-0 text-sm leading-[22px]
        text-[#747983] transition-colors duration-200 hover:text-[#292c35]
      "
    >
      <span>{label}</span>
      <strong className="ml-[5px] font-semibold text-[#282b34]">{value}</strong>
      {arrow && (
        <ChevronRight size={14} strokeWidth={1.8} className="ml-[3px] text-[#9599a2]" />
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

function Section({ title, children, className = '' }: SectionProps) {
  return (
    <section
      className={`
        min-h-[258px] rounded-[22px] border border-[#f1f1f1] bg-white/[0.68]
        px-[22px] pb-6 pt-6 backdrop-blur-[8px] ${className}
      `}
    >
      <h2
        className="
          mb-5 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]
        "
      >
        {title}
      </h2>
      {children}
    </section>
  );
}

/* =========================================================
 * Activity Center
 * ========================================================= */

interface ActivityOverviewItem {
  title: string;
  type: string;
  date: string;
}

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
      return { day: null, date: null, isToday: false };
    }
    const date = new Date(year, month, day);
    const isToday =
      today.getFullYear() === year
      && today.getMonth() === month
      && today.getDate() === day;
    return { day, date, isToday };
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
        relative min-h-screen w-full overflow-hidden bg-[#f7f8fa] text-[#242731]
      "
    >
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div
          className="
            absolute inset-x-0 top-0 h-[170px]
            bg-gradient-to-b from-[#f9fafb] to-transparent
          "
        />
        <div
          className="
            absolute -right-[60px] -top-[180px] h-[420px] w-[76%]
            bg-[radial-gradient(ellipse_at_center,rgba(159,215,239,0.48)_0%,rgba(187,224,240,0.23)_45%,rgba(255,255,255,0)_74%)]
            blur-[12px]
          "
        />
        <div
          className="
            absolute -bottom-[180px] -right-[160px] h-[420px] w-[620px]
            bg-[radial-gradient(circle_at_center,rgba(231,238,178,0.47),rgba(255,255,255,0)_70%)]
            blur-[16px]
          "
        />
        <div
          className="
            absolute right-0 top-0 h-[118px] w-[73%] opacity-[0.16]
            [background-image:repeating-linear-gradient(90deg,rgba(255,255,255,0.95)_0px,rgba(255,255,255,0.95)_1px,transparent_1px,transparent_5px)]
            [mask-image:linear-gradient(90deg,transparent_0%,rgba(0,0,0,0.2)_20%,rgba(0,0,0,1)_100%)]
          "
        />
      </div>

      <div className="relative z-10">
        <header className="flex h-[116px] items-center px-4">
          <div className="flex items-center">
            <div
              className="
                flex h-[66px] w-[66px] shrink-0 items-center justify-center overflow-hidden
                rounded-full border border-white/90 bg-gradient-to-br from-[#dde6ef]
                via-[#a6c8e2] to-[#5e93d4] text-white/95
                shadow-[0_2px_4px_rgba(31,35,41,0.04)]
              "
            >
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
                <ProfileStat label="数据源" value={0} arrow />
                <ProfileStat label="运行中" value={0} arrow />
                <ProfileStat label="今日异常" value={0} />
              </div>
            </div>
          </div>
        </header>

        <main className="px-4 pb-4">
          <div
            className="
              grid grid-cols-1 gap-4 min-[901px]:grid-cols-[330px_minmax(0,1fr)]
              min-[1101px]:grid-cols-[398px_minmax(0,1fr)]
            "
          >
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

            <Section title="数据工作" className="bg-white/[0.74]">
              <div
                className="
                  grid grid-cols-1 gap-x-[14px] gap-y-[13px] sm:grid-cols-2
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
