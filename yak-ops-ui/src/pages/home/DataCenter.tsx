import { history } from '@umijs/max';
import type { EChartsOption } from 'echarts';
import ReactECharts from 'echarts-for-react';
import {
  Check,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  Clock3,
  Copy,
  Database,
  RadioTower,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  homeDataCenterApi,
  type HomeDataCenterOverview,
  type HomeDataCenterPeriod,
  type HomeLatestTask,
  type HomeRecentTask,
  type HomeScheduleItem,
} from './service';

type OverviewTabKey = 'overview' | 'recent' | 'schedule';
type PeriodKey = HomeDataCenterPeriod;

interface OverviewMetric {
  label: string;
  value: string;
  compareLabel: string;
  compareValue: string;
  tone?: 'positive' | 'negative' | 'neutral';
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

const pad2 = (value: number) => String(value).padStart(2, '0');

const formatDate = (date: Date) =>
  `${date.getFullYear()}.${pad2(date.getMonth() + 1)}.${pad2(date.getDate())}`;

const formatIsoDate = (value?: string) => {
  if (!value) return '-';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value.replaceAll('-', '.') : formatDate(date);
};

function buildPeriod(periodKey: PeriodKey, reference = new Date()) {
  const today = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate(),
  );
  const end = new Date(today);
  end.setDate(today.getDate() - 1);
  const count = periodKey === '30d' ? 30 : periodKey === 'yesterday' ? 1 : 7;
  const start = new Date(end);
  start.setDate(end.getDate() - (count - 1));
  return { start, end };
}

const formatCount = (value: number) => {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1).replace(/\.0$/, '')}亿`;
  if (value >= 10000) return `${(value / 10000).toFixed(1).replace(/\.0$/, '')}万`;
  return String(value);
};

const formatDuration = (millis?: number) => {
  const seconds = Math.max(0, Math.round((millis || 0) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
};

const formatCardDuration = (millis?: number) => {
  const totalSeconds = Math.max(0, Math.round((millis || 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  return `${pad2(minutes)}:${pad2(totalSeconds % 60)}`;
};

const signedNumber = (value: number) => `${value > 0 ? '+' : ''}${value}`;
const signedDuration = (value: number) => `${value > 0 ? '+' : value < 0 ? '-' : ''}${formatDuration(Math.abs(value))}`;
const signedRate = (value: number) => `${value > 0 ? '+' : ''}${Number(value || 0).toFixed(1)}%`;

const compareLabelFor = (periodKey: PeriodKey) =>
  periodKey === 'yesterday' ? '较前1日' : periodKey === '30d' ? '较前30日' : '较前7日';

const positiveWhenUp = (value: number): OverviewMetric['tone'] =>
  value > 0 ? 'positive' : value < 0 ? 'negative' : 'neutral';

const positiveWhenDown = (value: number): OverviewMetric['tone'] =>
  value < 0 ? 'positive' : value > 0 ? 'negative' : 'neutral';

const toOverviewMetrics = (
  overview: HomeDataCenterOverview | undefined,
  periodKey: PeriodKey,
): OverviewMetric[] => {
  const metrics = overview?.metrics;
  const compare = overview?.compare;
  const compareLabel = compareLabelFor(periodKey);
  return [
    {
      label: '成功任务',
      value: String(metrics?.successCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.successCount ?? 0),
      tone: positiveWhenUp(compare?.successCount ?? 0),
    },
    {
      label: '运行中',
      value: String(metrics?.runningCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.runningCount ?? 0),
      tone: positiveWhenDown(compare?.runningCount ?? 0),
    },
    {
      label: '失败任务',
      value: String(metrics?.failedCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.failedCount ?? 0),
      tone: positiveWhenDown(compare?.failedCount ?? 0),
    },
    {
      label: '调度次数',
      value: String(metrics?.scheduleCount ?? 0),
      compareLabel,
      compareValue: signedNumber(compare?.scheduleCount ?? 0),
      tone: 'neutral',
    },
    {
      label: '处理记录',
      value: formatCount(metrics?.processedRecords ?? 0),
      compareLabel,
      compareValue: signedRate(compare?.processedRecordsRate ?? 0),
      tone: positiveWhenUp(compare?.processedRecordsRate ?? 0),
    },
    {
      label: '平均耗时',
      value: formatDuration(metrics?.avgDurationMs ?? 0),
      compareLabel,
      compareValue: signedDuration(compare?.avgDurationMs ?? 0),
      tone: positiveWhenDown(compare?.avgDurationMs ?? 0),
    },
  ];
};

const taskTypeLabel = (taskType?: string) => {
  if (taskType === 'OFFLINE_SYNC') return '离线同步';
  if (taskType === 'WORKFLOW') return '工作流';
  if (taskType === 'DATA_QUALITY') return '数据质量';
  return '任务';
};

const statusLabel = (status?: string) => {
  const normalized = status?.toUpperCase();
  if (['SUCCEEDED', 'SUCCESS', 'SUCCESS_WITH_WARNINGS', 'COMPLETED', 'FINISHED', 'WARNING'].includes(normalized || '')) return '成功';
  if (['FAILED', 'ERROR', 'TIMED_OUT', 'LOST'].includes(normalized || '')) return '失败';
  if (['CREATED', 'SUBMITTED', 'QUEUED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'].includes(normalized || '')) return '运行中';
  if (['CANCELED', 'CANCELLED'].includes(normalized || '')) return '已取消';
  return status || '-';
};

const statusClassName = (status?: string) => {
  const label = statusLabel(status);
  if (label === '成功') return 'text-[#20a464]';
  if (label === '失败') return 'text-[#f04c5a]';
  return 'text-[#7b8089]';
};

const formatRunTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16);
  const now = new Date();
  const time = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  if (
    date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate()
  ) return `今日 ${time}`;
  return `${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${time}`;
};

const goToDetail = (path?: string) => {
  if (path) history.push(path);
};

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

function LatestTaskCard({ task }: { task?: HomeLatestTask }) {
  return (
    <aside className="w-full shrink-0 lg:w-[220px] lg:border-r lg:border-[#edf0f3] lg:pr-5">
      <div className="mb-2 text-[13px] font-semibold leading-5 text-[#353842]">
        最新任务
      </div>

      <button
        type="button"
        onClick={() => goToDetail(task?.detailPath)}
        className="group relative h-[266px] w-full overflow-hidden rounded-[10px] border border-[#e8ebef] bg-[linear-gradient(150deg,#6c737d_0%,#9197a0_48%,#c0c4ca_100%)] text-left text-white transition-[border-color,transform] duration-200 hover:-translate-y-px hover:border-[#dfe3e8] lg:w-[198px]"
      >
        <div className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(255,255,255,.28)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.22)_1px,transparent_1px)] [background-size:22px_22px]" />
        <div className="pointer-events-none absolute -right-9 top-10 h-32 w-32 rounded-full border border-white/20" />
        <div className="pointer-events-none absolute -right-2 top-16 h-24 w-24 rounded-full border border-white/20" />

        <div className="absolute left-4 top-3 z-10">
          <div className="text-[12px] font-semibold text-white/95">{taskTypeLabel(task?.taskType)}</div>
          <div className="mt-0.5 text-[11px] text-white/80">{formatCardDuration(task?.durationMs)}</div>
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
            {task?.taskName || '暂无运行任务'}
          </div>
        </div>

        <div className="absolute inset-x-0 bottom-0 z-10 h-[70px] bg-black/20 px-4 backdrop-blur-[12px]">
          <div className="flex h-1/2 items-center justify-between border-b border-white/12">
            <span className="text-[11px] text-white/78">运行次数</span>
            <strong className="text-[12px] font-semibold">{task?.runCount ?? 0}</strong>
          </div>
          <div className="flex h-1/2 items-center justify-between">
            <span className="text-[11px] text-white/78">异常</span>
            <strong className="text-[12px] font-semibold">{task?.exceptionCount ?? 0}</strong>
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

function OverviewMetrics({ metrics }: { metrics: OverviewMetric[] }) {
  return (
    <div className="mt-1 grid grid-cols-2 gap-x-2 gap-y-1 sm:grid-cols-3">
      {metrics.map((metric) => (
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

function RecentTasksPanel({ items }: { items: HomeRecentTask[] }) {
  return (
    <div className="min-h-[263px] pt-2">
      {items.length === 0 && (
        <div className="flex min-h-[240px] items-center justify-center text-[12px] text-[#9da1a8]">
          暂无近期任务
        </div>
      )}
      {items.map((item) => (
        <button
          key={`${item.taskType}-${item.taskId}`}
          type="button"
          onClick={() => goToDetail(item.detailPath)}
          className="group grid w-full grid-cols-[minmax(230px,1.5fr)_repeat(4,minmax(72px,.55fr))_150px] items-center gap-3 rounded-[8px] px-3 py-3 text-left transition-colors hover:bg-[#f7f8fa]"
        >
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#edf4ff] text-[#5b8cff]">
              <Database size={16} strokeWidth={1.9} />
            </div>
            <div className="min-w-0">
              <div className="truncate text-[12px] font-medium text-[#363a43]">
                {item.taskName}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[11px] text-[#969aa3]">
                <Clock3 size={11} strokeWidth={1.8} />
                {formatRunTime(item.lastRunTime)}
              </div>
            </div>
          </div>

          {[
            ['运行', String(item.runCount)],
            ['成功', String(item.successCount)],
            ['失败', String(item.failedCount)],
            ['耗时', formatDuration(item.lastDurationMs)],
          ].map(([label, value]) => (
            <div key={label} className="min-w-0">
              <span className="text-[11px] text-[#969aa3]">{label}</span>
              <strong className="ml-2 text-[12px] font-semibold text-[#3b3f48]">
                {value}
              </strong>
            </div>
          ))}

          <div className="flex items-center justify-end gap-4">
            <span className={`text-[11px] font-medium ${statusClassName(item.lastStatus)}`}>
              {statusLabel(item.lastStatus)}
            </span>
            <span className="text-[12px] font-semibold text-[#323640] transition-colors group-hover:text-[#5b8cff]">
              查看详情
            </span>
          </div>
        </button>
      ))}
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

function SchedulePanel({ items, periodLabel }: { items: HomeScheduleItem[]; periodLabel: string }) {
  if (items.length === 0) return <EmptySchedulePanel periodLabel={periodLabel} />;
  return (
    <div className="min-h-[263px] pt-2">
      {items.map((item) => (
        <button
          key={`${item.taskType}-${item.taskId}`}
          type="button"
          onClick={() => goToDetail(item.detailPath)}
          className="group grid w-full grid-cols-[minmax(230px,1.5fr)_repeat(4,minmax(72px,.55fr))_150px] items-center gap-3 rounded-[8px] px-3 py-3 text-left transition-colors hover:bg-[#f7f8fa]"
        >
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[8px] bg-[#edf4ff] text-[#5b8cff]">
              <RadioTower size={16} strokeWidth={1.9} />
            </div>
            <div className="min-w-0">
              <div className="truncate text-[12px] font-medium text-[#363a43]">
                {item.taskName}
              </div>
              <div className="mt-1 flex items-center gap-1 text-[11px] text-[#969aa3]">
                <Clock3 size={11} strokeWidth={1.8} />
                {taskTypeLabel(item.taskType)}
              </div>
            </div>
          </div>

          {[
            ['周期', item.cronExpression || '-'],
            ['上次', formatRunTime(item.lastScheduleTime)],
            ['下次', formatRunTime(item.nextScheduleTime)],
            ['状态', statusLabel(item.status)],
          ].map(([label, value]) => (
            <div key={label} className="min-w-0">
              <span className="text-[11px] text-[#969aa3]">{label}</span>
              <strong className="ml-2 truncate text-[12px] font-semibold text-[#3b3f48]">
                {value}
              </strong>
            </div>
          ))}

          <div className="flex items-center justify-end gap-4">
            <span className={`text-[11px] font-medium ${statusClassName(item.status)}`}>
              {item.status || '-'}
            </span>
            <span className="text-[12px] font-semibold text-[#323640] transition-colors group-hover:text-[#5b8cff]">
              查看详情
            </span>
          </div>
        </button>
      ))}
    </div>
  );
}

export default function DataCenter() {
  const [activeTab, setActiveTab] = useState<OverviewTabKey>('overview');
  const [periodKey, setPeriodKey] = useState<PeriodKey>('7d');
  const [overview, setOverview] = useState<HomeDataCenterOverview>();
  const [recentTasks, setRecentTasks] = useState<HomeRecentTask[]>([]);
  const [scheduleItems, setScheduleItems] = useState<HomeScheduleItem[]>([]);
  const fallbackPeriod = useMemo(() => buildPeriod(periodKey), [periodKey]);
  const periodLabel = periodOptions.find((item) => item.key === periodKey)!.label;
  const overviewMetrics = useMemo(
    () => toOverviewMetrics(overview, periodKey),
    [overview, periodKey],
  );

  useEffect(() => {
    let active = true;
    void homeDataCenterApi.overview(periodKey).then((response) => {
      if (active) setOverview(response.data);
    }).catch(() => {
      if (active) setOverview(undefined);
    });
    return () => { active = false; };
  }, [periodKey]);

  useEffect(() => {
    if (activeTab !== 'recent') return undefined;
    let active = true;
    void homeDataCenterApi.recent().then((response) => {
      if (active) setRecentTasks(response.data?.items || []);
    }).catch(() => {
      if (active) setRecentTasks([]);
    });
    return () => { active = false; };
  }, [activeTab]);

  useEffect(() => {
    if (activeTab !== 'schedule') return undefined;
    let active = true;
    void homeDataCenterApi.schedule(periodKey).then((response) => {
      if (active) setScheduleItems(response.data?.items || []);
    }).catch(() => {
      if (active) setScheduleItems([]);
    });
    return () => { active = false; };
  }, [activeTab, periodKey]);

  const periodStart = overview?.period?.start
    ? formatIsoDate(overview.period.start)
    : formatDate(fallbackPeriod.start);
  const periodEnd = overview?.period?.end
    ? formatIsoDate(overview.period.end)
    : formatDate(fallbackPeriod.end);
  const trendLabels = overview?.trend?.labels || [];
  const trendValues = overview?.trend?.values || [];

  return (
    <section className="min-w-0 rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <h2 className="shrink-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
            数据中心
          </h2>
          <CircleHelp size={14} strokeWidth={1.9} className="shrink-0 text-[#a0a4ac]" />
          <span className="ml-0.5 hidden text-[12px] leading-5 text-[#8d929b] sm:inline">
            统计周期：{periodStart}-{periodEnd}（每天12点更新）
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
        <LatestTaskCard task={overview?.latestTask} />

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
                values={trendValues}
                labels={trendLabels}
                name="运行次数"
              />
              <OverviewMetrics metrics={overviewMetrics} />
            </div>
          )}

          {activeTab === 'recent' && <RecentTasksPanel items={recentTasks} />}

          {activeTab === 'schedule' && (
            <SchedulePanel items={scheduleItems} periodLabel={periodLabel} />
          )}
        </div>
      </div>
    </section>
  );
}
