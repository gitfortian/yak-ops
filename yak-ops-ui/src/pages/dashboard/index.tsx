import { BRAND_CSS_VARIABLES } from '@/styles/brand';
import { Button, Dropdown, Input, Select, Tooltip, message } from 'antd';
import * as echarts from 'echarts';
import {
  ChartLine,
  ChartPie,
  Copy,
  Eye,
  FileText,
  Filter,
  Gauge,
  GripVertical,
  RefreshCw,
  Save,
  SquarePlus,
  Table2,
  Trash2,
  Type,
  X,
} from 'lucide-react';
import ReactGridLayout, { useContainerWidth } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useEffect, useMemo, useRef, useState } from 'react';

type WidgetType =
  | 'text'
  | 'filter'
  | 'metric'
  | 'gauge'
  | 'bar'
  | 'line'
  | 'pie'
  | 'table';

interface DashboardWidget {
  id: string;
  type: WidgetType;
  title: string;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number;
  minH?: number;
  config?: Record<string, unknown>;
}

const STORAGE_KEY = 'yak-dashboard-designer.v1';
const GRID_COLUMNS = 24;
const GRID_ROW_HEIGHT = 28;

const DEFAULT_WIDGETS: DashboardWidget[] = [
  {
    id: 'title',
    type: 'text',
    title: '营销组织销售分析',
    x: 0,
    y: 0,
    w: 24,
    h: 2,
    minW: 8,
    minH: 2,
    config: { align: 'center', variant: 'title' },
  },
  {
    id: 'filter-month',
    type: 'filter',
    title: '年月',
    x: 0,
    y: 2,
    w: 8,
    h: 2,
    minW: 4,
    minH: 2,
    config: {
      value: '2026-08',
      options: ['2026-06', '2026-07', '2026-08', '2026-09'],
    },
  },
  {
    id: 'filter-employee',
    type: 'filter',
    title: '员工性质',
    x: 8,
    y: 2,
    w: 8,
    h: 2,
    minW: 4,
    minH: 2,
    config: {
      value: '全部',
      options: ['全部', '正式员工', '外包员工', '实习员工'],
    },
  },
  {
    id: 'metric-margin',
    type: 'metric',
    title: '项目利润率',
    x: 16,
    y: 2,
    w: 4,
    h: 4,
    minW: 4,
    minH: 3,
    config: { value: '13.95%', hint: '较上月 +1.26%' },
  },
  {
    id: 'metric-opportunity',
    type: 'metric',
    title: '机会预计金额',
    x: 20,
    y: 2,
    w: 4,
    h: 4,
    minW: 4,
    minH: 3,
    config: { value: '20.65亿', hint: '本月机会池' },
  },
  {
    id: 'gauge-sales',
    type: 'gauge',
    title: '净销售额完成进度',
    x: 0,
    y: 4,
    w: 8,
    h: 6,
    minW: 5,
    minH: 5,
    config: { value: 33.24, label: '33.24%' },
  },
  {
    id: 'gauge-payment',
    type: 'gauge',
    title: '回款净额进度',
    x: 8,
    y: 4,
    w: 8,
    h: 6,
    minW: 5,
    minH: 5,
    config: { value: 31.92, label: '31.92%' },
  },
  {
    id: 'bar-score',
    type: 'bar',
    title: '销售小组组织绩效得分',
    x: 16,
    y: 6,
    w: 8,
    h: 8,
    minW: 6,
    minH: 6,
  },
  {
    id: 'line-trend',
    type: 'line',
    title: '销售额与回款趋势',
    x: 0,
    y: 10,
    w: 12,
    h: 8,
    minW: 6,
    minH: 6,
  },
  {
    id: 'pie-region',
    type: 'pie',
    title: '区域销售额占比',
    x: 12,
    y: 10,
    w: 12,
    h: 8,
    minW: 6,
    minH: 6,
  },
  {
    id: 'table-team',
    type: 'table',
    title: '销售小组组织绩效得分情况',
    x: 0,
    y: 18,
    w: 24,
    h: 9,
    minW: 10,
    minH: 6,
  },
];

const cloneDefaultWidgets = () =>
  DEFAULT_WIDGETS.map((widget) => ({
    ...widget,
    config: widget.config ? { ...widget.config } : undefined,
  }));

const loadInitialWidgets = () => {
  if (typeof window === 'undefined') return cloneDefaultWidgets();
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    if (!stored) return cloneDefaultWidgets();
    const parsed = JSON.parse(stored);
    return Array.isArray(parsed) && parsed.length
      ? (parsed as DashboardWidget[])
      : cloneDefaultWidgets();
  } catch {
    return cloneDefaultWidgets();
  }
};

const widgetTypeLabel: Record<WidgetType, string> = {
  text: '文本',
  filter: '过滤器',
  metric: '指标卡',
  gauge: '仪表盘',
  bar: '柱状图',
  line: '折线图',
  pie: '饼图',
  table: '表格',
};

const widgetTypeIcon = (type: WidgetType) => {
  const props = { size: 14, strokeWidth: 1.8 };
  switch (type) {
    case 'filter':
      return <Filter {...props} />;
    case 'metric':
      return <Gauge {...props} />;
    case 'gauge':
      return <Gauge {...props} />;
    case 'bar':
    case 'line':
      return <ChartLine {...props} />;
    case 'pie':
      return <ChartPie {...props} />;
    case 'table':
      return <Table2 {...props} />;
    default:
      return <Type {...props} />;
  }
};

const nextWidgetDefaults = (type: WidgetType, y: number): DashboardWidget => {
  const id = `${type}-${Date.now()}-${Math.round(Math.random() * 1000)}`;
  if (type === 'metric') {
    return {
      id,
      type,
      title: '新增指标',
      x: 0,
      y,
      w: 6,
      h: 4,
      minW: 4,
      minH: 3,
      config: { value: '128,640', hint: 'Mock 数据' },
    };
  }
  if (type === 'filter') {
    return {
      id,
      type,
      title: '筛选条件',
      x: 0,
      y,
      w: 8,
      h: 2,
      minW: 4,
      minH: 2,
      config: { value: '全部', options: ['全部', '选项一', '选项二'] },
    };
  }
  if (type === 'text') {
    return {
      id,
      type,
      title: '文本组件',
      x: 0,
      y,
      w: 12,
      h: 2,
      minW: 4,
      minH: 2,
      config: { align: 'left' },
    };
  }
  if (type === 'table') {
    return {
      id,
      type,
      title: '数据明细',
      x: 0,
      y,
      w: 16,
      h: 8,
      minW: 8,
      minH: 6,
    };
  }
  return {
    id,
    type,
    title: `新增${widgetTypeLabel[type]}`,
    x: 0,
    y,
    w: 10,
    h: type === 'gauge' ? 6 : 7,
    minW: 5,
    minH: 5,
    config: type === 'gauge' ? { value: 68.5, label: '68.50%' } : undefined,
  };
};

const chartOptionFor = (widget: DashboardWidget) => {
  const axisColor = '#d8dde6';
  const labelColor = '#667085';
  const primary = '#5470c6';
  const secondary = '#91cc75';

  if (widget.type === 'gauge') {
    const value = Number(widget.config?.value ?? 0);
    return {
      animationDuration: 350,
      series: [
        {
          type: 'gauge',
          startAngle: 210,
          endAngle: -30,
          min: 0,
          max: 100,
          radius: '90%',
          center: ['50%', '58%'],
          progress: { show: true, width: 10, roundCap: true },
          axisLine: { lineStyle: { width: 10, color: [[1, '#edf1f6']] } },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          pointer: { show: false },
          anchor: { show: false },
          title: { show: false },
          detail: {
            valueAnimation: true,
            formatter: widget.config?.label || `${value}%`,
            color: '#344054',
            fontSize: 23,
            fontWeight: 600,
            offsetCenter: [0, '10%'],
          },
          data: [{ value }],
        },
      ],
    };
  }

  if (widget.type === 'bar') {
    return {
      grid: { left: 36, right: 14, top: 12, bottom: 28, containLabel: true },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: ['MIC', 'BRAD', 'JA', 'TO', 'LUY', 'GRA', 'STE'],
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
        axisLabel: { color: labelColor, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#eef1f5' } },
        axisLabel: { color: labelColor, fontSize: 11 },
      },
      series: [
        {
          type: 'bar',
          data: [62.3, 58.02, 44.1, 37.31, 30.6, 26.12, 21.74],
          barMaxWidth: 28,
          itemStyle: { color: primary, borderRadius: [3, 3, 0, 0] },
        },
      ],
    };
  }

  if (widget.type === 'line') {
    return {
      grid: { left: 36, right: 16, top: 24, bottom: 28, containLabel: true },
      tooltip: { trigger: 'axis' },
      legend: {
        top: 0,
        right: 4,
        itemWidth: 10,
        itemHeight: 7,
        textStyle: { color: labelColor, fontSize: 11 },
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: ['3月', '4月', '5月', '6月', '7月', '8月'],
        axisLine: { lineStyle: { color: axisColor } },
        axisTick: { show: false },
        axisLabel: { color: labelColor, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#eef1f5' } },
        axisLabel: { color: labelColor, fontSize: 11 },
      },
      series: [
        {
          name: '销售额',
          type: 'line',
          smooth: true,
          symbolSize: 5,
          data: [128, 156, 149, 182, 201, 236],
          lineStyle: { width: 2, color: primary },
          itemStyle: { color: primary },
          areaStyle: { color: 'rgba(84,112,198,.08)' },
        },
        {
          name: '回款额',
          type: 'line',
          smooth: true,
          symbolSize: 5,
          data: [96, 121, 136, 151, 168, 191],
          lineStyle: { width: 2, color: secondary },
          itemStyle: { color: secondary },
        },
      ],
    };
  }

  if (widget.type === 'pie') {
    return {
      tooltip: { trigger: 'item' },
      legend: {
        orient: 'vertical',
        right: 16,
        top: 'middle',
        textStyle: { color: labelColor, fontSize: 11 },
      },
      series: [
        {
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['38%', '52%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: [
            { value: 38, name: '华东' },
            { value: 26, name: '华南' },
            { value: 19, name: '华北' },
            { value: 11, name: '西南' },
            { value: 6, name: '其他' },
          ],
        },
      ],
    };
  }

  return undefined;
};

function EChartWidget({ widget }: { widget: DashboardWidget }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts>();
  const option = useMemo(() => chartOptionFor(widget), [widget]);

  useEffect(() => {
    if (!containerRef.current || !option) return undefined;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.setOption(option, true);

    const observer = new ResizeObserver(() => {
      chart.resize();
    });
    observer.observe(containerRef.current);

    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = undefined;
    };
  }, [option]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

const mockRows = [
  ['MIC', '28.30', '10.00', '24.00', '62.30'],
  ['BRAD', '34.02', '0', '24.00', '58.02'],
  ['JA', '20.10', '0', '24.00', '44.10'],
  ['TO', '19.59', '0', '17.72', '37.31'],
  ['LUY', '21.94', '8.67', '0', '30.60'],
  ['GRA', '0', '7.12', '19.00', '26.12'],
  ['STE', '0', '6.06', '15.68', '21.74'],
  ['WILL', '0', '0', '12.65', '12.65'],
];

function TableWidget() {
  return (
    <div className="h-full overflow-auto">
      <table className="w-full table-fixed border-collapse text-[11px]">
        <thead className="sticky top-0 z-10 bg-[#f6f8fb] text-[#475467]">
          <tr>
            {['销售小组', '回款完成得分', '项目利润率得分', '销售完成率得分', '总得分'].map(
              (title) => (
                <th
                  key={title}
                  className="border-b border-[#e7eaf0] px-3 py-2 text-left font-medium"
                >
                  {title}
                </th>
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {mockRows.map((row) => (
            <tr key={row[0]} className="hover:bg-[#fafbfc]">
              {row.map((cell, index) => (
                <td
                  key={`${row[0]}-${index}`}
                  className="border-b border-[#f0f2f5] px-3 py-1.5 text-[#344054]"
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function WidgetContent({ widget }: { widget: DashboardWidget }) {
  if (widget.type === 'text') {
    const isTitle = widget.config?.variant === 'title';
    return (
      <div
        className={[
          'flex h-full items-center px-4 text-[#161823]',
          widget.config?.align === 'center' ? 'justify-center text-center' : 'justify-start',
          isTitle ? 'text-[24px] font-semibold' : 'text-[14px]',
        ].join(' ')}
      >
        {widget.title}
      </div>
    );
  }

  if (widget.type === 'filter') {
    const options = Array.isArray(widget.config?.options)
      ? (widget.config?.options as string[])
      : ['全部'];
    const value = String(widget.config?.value ?? options[0]);
    return (
      <div className="flex h-full items-center gap-3 px-3">
        <span className="shrink-0 text-[12px] font-medium text-[#344054]">{widget.title}</span>
        <Select
          size="small"
          value={value}
          options={options.map((item) => ({ label: item, value: item }))}
          className="min-w-0 flex-1"
        />
      </div>
    );
  }

  if (widget.type === 'metric') {
    return (
      <div className="flex h-full flex-col justify-center px-5">
        <div className="text-[12px] font-medium text-[#475467]">{widget.title}</div>
        <div className="mt-3 text-[28px] font-semibold tracking-[-0.02em] text-[#172b4d]">
          {String(widget.config?.value ?? '--')}
        </div>
        <div className="mt-2 text-[11px] text-[#98a2b3]">
          {String(widget.config?.hint ?? 'Mock 数据')}
        </div>
      </div>
    );
  }

  if (widget.type === 'table') return <TableWidget />;
  return <EChartWidget widget={widget} />;
}

interface WidgetShellProps {
  widget: DashboardWidget;
  selected: boolean;
  preview: boolean;
  onSelect: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
}

function WidgetShell({
  widget,
  selected,
  preview,
  onSelect,
  onDuplicate,
  onDelete,
}: WidgetShellProps) {
  const contentOnly = widget.type === 'text' || widget.type === 'filter' || widget.type === 'metric';

  return (
    <div
      onMouseDown={onSelect}
      className={[
        'group relative flex h-full min-h-0 flex-col overflow-hidden bg-white',
        preview
          ? 'border border-[#e7eaf0]'
          : selected
            ? 'border border-[var(--yak-brand-color)] shadow-[0_0_0_1px_var(--yak-brand-color-soft)]'
            : 'border border-[#e3e7ed] hover:border-[#cbd2dc]',
      ].join(' ')}
    >
      {!preview ? (
        <div
          className={[
            'dashboard-widget__drag-handle absolute left-1 top-1 z-20',
            'flex h-6 w-6 cursor-move items-center justify-center rounded-[3px]',
            'bg-white/95 text-[#98a2b3] opacity-0 shadow-sm transition-opacity',
            selected ? 'opacity-100' : 'group-hover:opacity-100',
          ].join(' ')}
          title="拖动组件"
        >
          <GripVertical size={13} strokeWidth={1.8} />
        </div>
      ) : null}

      {!preview ? (
        <div
          className={[
            'absolute right-1 top-1 z-20 flex items-center gap-0.5 rounded-[3px]',
            'bg-white/95 p-0.5 opacity-0 shadow-sm transition-opacity',
            selected ? 'opacity-100' : 'group-hover:opacity-100',
          ].join(' ')}
        >
          <Tooltip title="复制">
            <button
              type="button"
              aria-label="复制组件"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onDuplicate();
              }}
              className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7] hover:text-[#344054]"
            >
              <Copy size={12} strokeWidth={1.8} />
            </button>
          </Tooltip>
          <Tooltip title="删除">
            <button
              type="button"
              aria-label="删除组件"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={(event) => {
                event.stopPropagation();
                onDelete();
              }}
              className="flex h-6 w-6 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#fff1f2] hover:text-[#d92d20]"
            >
              <Trash2 size={12} strokeWidth={1.8} />
            </button>
          </Tooltip>
        </div>
      ) : null}

      {!contentOnly ? (
        <div className="dashboard-widget__drag-handle flex h-9 shrink-0 cursor-move items-center border-b border-[#f0f2f5] px-3 text-[12px] font-medium text-[#344054]">
          <span className="min-w-0 flex-1 truncate pr-16">{widget.title}</span>
        </div>
      ) : null}

      <div className="min-h-0 flex-1 overflow-hidden">
        <WidgetContent widget={widget} />
      </div>
    </div>
  );
}

function DashboardPropertyPanel({
  widget,
  onChange,
  onDuplicate,
  onDelete,
  onClose,
}: {
  widget: DashboardWidget;
  onChange: (patch: Partial<DashboardWidget>) => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  return (
    <aside className="flex w-[272px] shrink-0 flex-col border-l border-[#e5e7eb] bg-white">
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
        <div className="text-[13px] font-semibold text-[#30323b]">组件属性</div>
        <button
          type="button"
          aria-label="关闭属性面板"
          onClick={onClose}
          className="flex h-7 w-7 items-center justify-center border-0 bg-transparent text-[#667085] hover:bg-[#f5f5f6] hover:text-[#344054]"
        >
          <X size={14} strokeWidth={1.8} />
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-4">
        <div className="mb-5 flex items-center gap-2 text-[12px] text-[#667085]">
          <span className="flex h-7 w-7 items-center justify-center rounded-[4px] bg-[#f5f6f7] text-[#475467]">
            {widgetTypeIcon(widget.type)}
          </span>
          <div>
            <div className="font-medium text-[#344054]">{widgetTypeLabel[widget.type]}</div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">{widget.id}</div>
          </div>
        </div>

        <label className="mb-1.5 block text-[11px] font-medium text-[#667085]">标题</label>
        <Input
          size="small"
          value={widget.title}
          onChange={(event) => onChange({ title: event.target.value })}
        />

        <div className="mt-5 grid grid-cols-2 gap-2">
          <div className="rounded-[4px] bg-[#f7f8fa] px-3 py-2">
            <div className="text-[10px] text-[#98a2b3]">位置</div>
            <div className="mt-1 font-mono text-[11px] text-[#475467]">
              x {widget.x} · y {widget.y}
            </div>
          </div>
          <div className="rounded-[4px] bg-[#f7f8fa] px-3 py-2">
            <div className="text-[10px] text-[#98a2b3]">尺寸</div>
            <div className="mt-1 font-mono text-[11px] text-[#475467]">
              {widget.w} × {widget.h}
            </div>
          </div>
        </div>

        <div className="mt-6 border-t border-[#edf0f3] pt-4">
          <div className="mb-2 text-[11px] font-medium text-[#667085]">数据配置</div>
          <div className="rounded-[4px] border border-dashed border-[#d8dde6] bg-[#fafbfc] px-3 py-3 text-[11px] leading-5 text-[#98a2b3]">
            当前使用 Mock 数据。后续接入 SQL 任务 / 分析数据时，只需要替换 Widget 的 dataSource 配置，画布结构无需调整。
          </div>
        </div>
      </div>
      <div className="flex shrink-0 gap-2 border-t border-[#e5e7eb] p-3">
        <Button size="small" icon={<Copy size={13} />} onClick={onDuplicate} className="flex-1">
          复制
        </Button>
        <Button size="small" danger icon={<Trash2 size={13} />} onClick={onDelete} className="flex-1">
          删除
        </Button>
      </div>
    </aside>
  );
}

export default function DashboardPage() {
  const [widgets, setWidgets] = useState<DashboardWidget[]>(loadInitialWidgets);
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(false);
  const { width, containerRef, mounted } = useContainerWidth();

  const selectedWidget = widgets.find((widget) => widget.id === selectedId);

  const layout = useMemo(
    () =>
      widgets.map((widget) => ({
        i: widget.id,
        x: widget.x,
        y: widget.y,
        w: widget.w,
        h: widget.h,
        minW: widget.minW,
        minH: widget.minH,
      })),
    [widgets],
  );

  const syncLayout = (nextLayout: readonly { i: string; x: number; y: number; w: number; h: number }[]) => {
    const nextMap = new Map(nextLayout.map((item) => [item.i, item]));
    setWidgets((current) =>
      current.map((widget) => {
        const next = nextMap.get(widget.id);
        return next
          ? { ...widget, x: next.x, y: next.y, w: next.w, h: next.h }
          : widget;
      }),
    );
  };

  const maxY = () =>
    widgets.reduce((value, widget) => Math.max(value, widget.y + widget.h), 0);

  const addWidget = (type: WidgetType) => {
    const next = nextWidgetDefaults(type, maxY());
    setWidgets((current) => [...current, next]);
    setSelectedId(next.id);
  };

  const updateWidget = (id: string, patch: Partial<DashboardWidget>) => {
    setWidgets((current) =>
      current.map((widget) => (widget.id === id ? { ...widget, ...patch } : widget)),
    );
  };

  const deleteWidget = (id: string) => {
    setWidgets((current) => current.filter((widget) => widget.id !== id));
    setSelectedId((current) => (current === id ? undefined : current));
  };

  const duplicateWidget = (id: string) => {
    const source = widgets.find((widget) => widget.id === id);
    if (!source) return;
    const next: DashboardWidget = {
      ...source,
      id: `${source.type}-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      y: maxY(),
      config: source.config ? { ...source.config } : undefined,
    };
    setWidgets((current) => [...current, next]);
    setSelectedId(next.id);
  };

  const save = () => {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets));
    message.success('仪表盘布局已保存到本地');
  };

  const reset = () => {
    const next = cloneDefaultWidgets();
    setWidgets(next);
    setSelectedId(undefined);
    window.localStorage.removeItem(STORAGE_KEY);
    message.success('已恢复示例仪表盘');
  };

  const componentMenu = {
    items: [
      { key: 'metric', label: '指标卡', icon: <Gauge size={14} /> },
      { key: 'gauge', label: '仪表盘', icon: <Gauge size={14} /> },
      { key: 'bar', label: '柱状图', icon: <ChartLine size={14} /> },
      { key: 'line', label: '折线图', icon: <ChartLine size={14} /> },
      { key: 'pie', label: '饼图', icon: <ChartPie size={14} /> },
      { key: 'table', label: '表格', icon: <Table2 size={14} /> },
    ],
    onClick: ({ key }: { key: string }) => addWidget(key as WidgetType),
  };

  return (
    <div
      className="flex h-[calc(100vh-48px)] min-h-[640px] flex-col overflow-hidden bg-[#f4f6f8]"
      style={BRAND_CSS_VARIABLES}
    >
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#dfe3e8] bg-white px-3">
        <div className="flex items-center gap-1">
          <Dropdown menu={componentMenu} trigger={['click']} disabled={preview}>
            <Button size="small" type="text" icon={<SquarePlus size={14} strokeWidth={1.8} />}>
              组件
            </Button>
          </Dropdown>
          <Button
            size="small"
            type="text"
            icon={<Filter size={14} strokeWidth={1.8} />}
            disabled={preview}
            onClick={() => addWidget('filter')}
          >
            过滤组件
          </Button>
          <Button
            size="small"
            type="text"
            icon={<Type size={14} strokeWidth={1.8} />}
            disabled={preview}
            onClick={() => addWidget('text')}
          >
            文本
          </Button>
          <span className="mx-1 h-5 w-px bg-[#e5e7eb]" />
          <Tooltip title="恢复示例布局">
            <Button
              size="small"
              type="text"
              icon={<RefreshCw size={14} strokeWidth={1.8} />}
              disabled={preview}
              onClick={reset}
            />
          </Tooltip>
        </div>

        <div className="flex items-center gap-2">
          <span className="hidden text-[11px] text-[#98a2b3] xl:inline">24 栅格 · Mock 数据</span>
          <Button
            size="small"
            icon={preview ? <X size={13} /> : <Eye size={13} />}
            onClick={() => {
              setPreview((current) => !current);
              setSelectedId(undefined);
            }}
          >
            {preview ? '退出预览' : '预览'}
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<Save size={13} />}
            disabled={preview}
            onClick={save}
          >
            保存
          </Button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <main
          className="min-w-0 flex-1 overflow-auto"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setSelectedId(undefined);
          }}
        >
          <div className="min-h-full p-3">
            <div
              ref={containerRef}
              className={[
                'mx-auto min-h-[calc(100vh-84px)] min-w-[760px] bg-white',
                preview
                  ? 'shadow-[0_1px_4px_rgba(16,24,40,.08)]'
                  : 'dashboard-grid-canvas border border-[#dfe3e8]',
              ].join(' ')}
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) setSelectedId(undefined);
              }}
            >
              {mounted && width > 0 ? (
                <ReactGridLayout
                  width={width}
                  layout={layout}
                  gridConfig={{
                    cols: GRID_COLUMNS,
                    rowHeight: GRID_ROW_HEIGHT,
                    margin: [8, 8],
                    containerPadding: [8, 8],
                  }}
                  dragConfig={{
                    enabled: !preview,
                    handle: '.dashboard-widget__drag-handle',
                  }}
                  resizeConfig={{ enabled: !preview }}
                  onLayoutChange={(nextLayout) => syncLayout(nextLayout)}
                >
                  {widgets.map((widget) => (
                    <div key={widget.id}>
                      <WidgetShell
                        widget={widget}
                        selected={selectedId === widget.id}
                        preview={preview}
                        onSelect={() => {
                          if (!preview) setSelectedId(widget.id);
                        }}
                        onDuplicate={() => duplicateWidget(widget.id)}
                        onDelete={() => deleteWidget(widget.id)}
                      />
                    </div>
                  ))}
                </ReactGridLayout>
              ) : null}
            </div>
          </div>
        </main>

        {!preview && selectedWidget ? (
          <DashboardPropertyPanel
            widget={selectedWidget}
            onChange={(patch) => updateWidget(selectedWidget.id, patch)}
            onDuplicate={() => duplicateWidget(selectedWidget.id)}
            onDelete={() => deleteWidget(selectedWidget.id)}
            onClose={() => setSelectedId(undefined)}
          />
        ) : null}
      </div>

      <style>{`
        .dashboard-grid-canvas {
          background-color: #fff;
          background-image:
            linear-gradient(to right, rgba(15, 23, 42, 0.035) 1px, transparent 1px),
            linear-gradient(to bottom, rgba(15, 23, 42, 0.035) 1px, transparent 1px);
          background-size: calc(100% / 24) 36px;
        }

        .react-grid-item.react-grid-placeholder {
          background: var(--yak-brand-color-soft) !important;
          border: 1px dashed var(--yak-brand-color) !important;
          border-radius: 2px;
          opacity: 1 !important;
        }

        .react-grid-item > .react-resizable-handle::after {
          border-right-color: var(--yak-brand-color) !important;
          border-bottom-color: var(--yak-brand-color) !important;
        }

        .react-grid-item.react-draggable-dragging,
        .react-grid-item.resizing {
          z-index: 50;
        }
      `}</style>
    </div>
  );
}
