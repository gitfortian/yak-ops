import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type {
  ScreenBarComponent,
  ScreenComponent,
  ScreenDataOverrides,
  ScreenLineComponent,
  ScreenPieComponent,
  ScreenTableComponent,
  ScreenTemplate,
  ScreenTheme,
} from './model';
import { SCREEN_MOTION_CSS, ScreenNetworkMap, ScreenTicker } from './PremiumVisuals';

type ScreenChartComponent = ScreenLineComponent | ScreenBarComponent | ScreenPieComponent;

interface ComponentInteraction {
  selected?: boolean;
  onSelect?: () => void;
}

const CHART_COLORS = ['#46d9ff', '#5cf2b5', '#ffc866', '#8f7cff', '#ff668f', '#43e6cf'];

const formatValue = (value: string | number) => {
  if (typeof value !== 'number') return value;
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: Number.isInteger(value) ? 0 : 2 }).format(value);
};

const alpha = (hex: string, suffix: string) => (
  /^#[0-9a-fA-F]{6}$/.test(hex) ? `${hex}${suffix}` : hex
);

const chartOptionFor = (component: ScreenChartComponent, theme: ScreenTheme): EChartsOption => {
  const axisText = theme.mutedTextColor;
  const axisLine = alpha(theme.panelBorderColor, '');
  const splitLine = theme.panelBorderColor;
  const colors = [component.style?.accentColor ?? theme.primaryColor, ...CHART_COLORS];
  const neon = component.options?.neon ?? false;
  const gradient = component.options?.gradient ?? false;

  if (component.type === 'pie') {
    const data = component.data?.items ?? [];
    return {
      color: colors,
      animation: true,
      animationDuration: 1000,
      animationEasing: 'cubicOut',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(3, 13, 25, .94)',
        borderColor: alpha(theme.primaryColor, '55'),
        textStyle: { color: theme.textColor },
      },
      legend: component.options?.showLegend === false
        ? { show: false }
        : {
            right: 6,
            top: 'middle',
            orient: 'vertical',
            itemWidth: 8,
            itemHeight: 8,
            textStyle: { color: axisText, fontSize: 11 },
          },
      series: [{
        type: 'pie',
        radius: component.options?.rose ? ['34%', '76%'] : ['48%', '72%'],
        center: [component.options?.showLegend === false ? '50%' : '39%', '52%'],
        roseType: component.options?.rose ? 'radius' : undefined,
        minAngle: 4,
        padAngle: component.options?.rose ? 2 : 0,
        label: {
          show: component.options?.showLabels ?? false,
          color: theme.textColor,
          formatter: '{b}  {d}%',
        },
        emphasis: {
          scale: true,
          scaleSize: 6,
          itemStyle: { shadowBlur: 22, shadowColor: alpha(theme.primaryColor, '66') },
        },
        itemStyle: {
          borderColor: theme.panelBackground,
          borderWidth: 2,
          borderRadius: component.options?.rose ? 7 : 2,
          shadowBlur: neon ? 10 : 0,
          shadowColor: neon ? alpha(theme.primaryColor, '44') : undefined,
        },
        data,
      }],
    };
  }

  const data = component.data ?? { categories: [], series: [] };
  const isLine = component.type === 'line';
  const horizontal = component.type === 'bar' && (component.options?.horizontal ?? false);
  const categoryAxis = {
    type: 'category' as const,
    boundaryGap: !isLine,
    data: data.categories,
    axisLine: { lineStyle: { color: axisLine } },
    axisTick: { show: false },
    axisLabel: { color: axisText, fontSize: 10, margin: 10 },
  };
  const valueAxis = {
    type: 'value' as const,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: axisText, fontSize: 10 },
    splitLine: {
      show: component.options?.showGrid ?? true,
      lineStyle: { color: splitLine, type: 'dashed' as const, opacity: 0.52 },
    },
  };

  return {
    color: colors,
    animation: true,
    animationDuration: 900,
    animationEasing: 'cubicOut',
    animationDelay: (index: number) => index * 45,
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(3, 13, 25, .94)',
      borderColor: alpha(theme.primaryColor, '55'),
      textStyle: { color: theme.textColor },
      axisPointer: { type: 'line', lineStyle: { color: alpha(theme.primaryColor, '55') } },
    },
    legend: component.options?.showLegend === false
      ? { show: false }
      : {
          top: 0,
          right: 4,
          itemWidth: 10,
          itemHeight: 6,
          textStyle: { color: axisText, fontSize: 11 },
        },
    grid: {
      left: horizontal ? 10 : 12,
      right: 10,
      top: component.options?.showLegend === false ? 14 : 34,
      bottom: 8,
      containLabel: true,
    },
    xAxis: horizontal ? valueAxis : categoryAxis,
    yAxis: horizontal ? categoryAxis : valueAxis,
    series: data.series.map((series, seriesIndex) => {
      const color = colors[seriesIndex % colors.length];
      const lineGradient = new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: alpha(color, '4d') },
        { offset: 1, color: alpha(color, '00') },
      ]);
      const barGradient = new echarts.graphic.LinearGradient(horizontal ? 0 : 0, horizontal ? 0 : 1, horizontal ? 1 : 0, 0, [
        { offset: 0, color: alpha(color, '8c') },
        { offset: 0.6, color },
        { offset: 1, color: '#b7f7ff' },
      ]);
      return {
        name: series.name,
        type: component.type,
        data: series.values,
        smooth: isLine && (component.options?.smooth ?? true),
        symbol: isLine ? 'circle' : undefined,
        showSymbol: isLine ? false : undefined,
        symbolSize: isLine ? 5 : undefined,
        barMaxWidth: component.type === 'bar' ? 24 : undefined,
        barCategoryGap: component.type === 'bar' ? '42%' : undefined,
        label: {
          show: component.options?.showLabels ?? false,
          position: horizontal ? 'right' : 'top',
          color: theme.textColor,
          fontSize: 10,
        },
        lineStyle: isLine
          ? {
              width: neon ? 2.5 : 2,
              color,
              shadowBlur: neon ? 10 : 0,
              shadowColor: neon ? alpha(color, '99') : undefined,
            }
          : undefined,
        areaStyle: isLine
          ? {
              opacity: component.options?.showArea === false ? 0 : 1,
              color: gradient || neon ? lineGradient : alpha(color, '16'),
            }
          : undefined,
        itemStyle: component.type === 'bar'
          ? {
              color: gradient ? barGradient : color,
              borderRadius: horizontal ? [0, 7, 7, 0] : [7, 7, 1, 1],
              shadowBlur: neon ? 8 : 0,
              shadowColor: neon ? alpha(color, '66') : undefined,
            }
          : { color },
        emphasis: {
          focus: 'series',
          itemStyle: { shadowBlur: 16, shadowColor: alpha(color, '99') },
        },
      };
    }),
  };
};

function ScreenChart({ component, theme }: { component: ScreenChartComponent; theme: ScreenTheme }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const option = useMemo(() => chartOptionFor(component, theme), [component, theme]);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const chart = echarts.init(containerRef.current);
    chart.setOption(option, true);
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(containerRef.current);
    return () => {
      observer.disconnect();
      chart.dispose();
    };
  }, [option]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
}

function ComponentFrame({
  component,
  theme,
  children,
  selected = false,
  onSelect,
}: {
  component: ScreenComponent;
  theme: ScreenTheme;
  children: ReactNode;
} & ComponentInteraction) {
  const style = component.style;
  const transparent = component.type === 'text';
  const frame = style?.frame ?? 'standard';
  const borderColor = style?.borderColor ?? theme.panelBorderColor;
  const accentColor = style?.accentColor ?? theme.primaryColor;
  const background = style?.background ?? (transparent ? 'transparent' : theme.panelBackground);
  const glowShadow = style?.glow
    ? `0 0 28px ${alpha(accentColor, '12')}, inset 0 0 34px ${alpha(accentColor, '08')}`
    : undefined;

  return (
    <div
      data-screen-component={component.id}
      data-screen-selected={selected || undefined}
      onClick={onSelect}
      className={[
        'absolute box-border flex min-h-0 flex-col overflow-hidden transition-[outline,filter] duration-150',
        onSelect ? 'cursor-pointer hover:brightness-[1.05]' : '',
      ].join(' ')}
      style={{
        left: component.x,
        top: component.y,
        width: component.width,
        height: component.height,
        padding: style?.padding ?? (transparent ? 0 : 20),
        background,
        border: transparent ? undefined : `1px solid ${borderColor}`,
        borderRadius: style?.borderRadius ?? (transparent ? 0 : frame === 'hud' ? 4 : 10),
        boxShadow: style?.shadow ?? glowShadow,
        color: style?.color ?? theme.textColor,
        outline: selected ? '3px solid rgba(254, 44, 85, 0.92)' : undefined,
        outlineOffset: selected ? -3 : undefined,
        backdropFilter: frame === 'glass' ? 'blur(12px)' : undefined,
        animation: style?.effect === 'pulse' ? 'yak-screen-pulse 3.8s ease-in-out infinite' : undefined,
      }}
    >
      {frame === 'hud' && !transparent ? (
        <>
          <span className="pointer-events-none absolute left-[-1px] top-[-1px] h-4 w-4 border-l-2 border-t-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute right-[-1px] top-[-1px] h-4 w-4 border-r-2 border-t-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute bottom-[-1px] left-[-1px] h-4 w-4 border-b-2 border-l-2" style={{ borderColor: accentColor }} />
          <span className="pointer-events-none absolute bottom-[-1px] right-[-1px] h-4 w-4 border-b-2 border-r-2" style={{ borderColor: accentColor }} />
          <span
            className="pointer-events-none absolute left-5 top-0 h-px w-24"
            style={{ background: `linear-gradient(90deg, ${accentColor}, transparent)` }}
          />
        </>
      ) : null}
      {style?.effect === 'scan' && component.type !== 'map' ? (
        <span
          className="pointer-events-none absolute left-0 right-0 top-0 h-12"
          style={{
            animation: 'yak-screen-scan 7s linear infinite',
            background: `linear-gradient(180deg, transparent, ${alpha(accentColor, '18')}, transparent)`,
          }}
        />
      ) : null}
      {component.title ? (
        <div className="relative z-[2] mb-3 shrink-0">
          <div className="flex items-center gap-2">
            {frame === 'hud' ? <span className="h-1.5 w-1.5 rotate-45" style={{ background: accentColor, boxShadow: `0 0 8px ${accentColor}` }} /> : null}
            <div
              className="text-[16px] font-semibold leading-6 tracking-[0.02em]"
              style={{ color: style?.titleColor ?? theme.textColor }}
            >
              {component.title}
            </div>
          </div>
          {component.subtitle ? (
            <div className="mt-1 text-[11px] tracking-[0.04em]" style={{ color: style?.subtitleColor ?? theme.mutedTextColor }}>
              {component.subtitle}
            </div>
          ) : null}
        </div>
      ) : null}
      <div className="relative z-[1] min-h-0 flex-1">{children}</div>
    </div>
  );
}

function MetricComponent({
  component,
  theme,
  ...interaction
}: {
  component: Extract<ScreenComponent, { type: 'metric' }>;
  theme: ScreenTheme;
} & ComponentInteraction) {
  const data = component.data;
  const direction = data?.trendDirection ?? 'flat';
  const trendColor = direction === 'up' ? '#5cf2b5' : direction === 'down' ? '#ff668f' : theme.mutedTextColor;
  const trendPrefix = direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→';
  const accentColor = component.style?.accentColor ?? theme.primaryColor;

  return (
    <ComponentFrame component={component} theme={theme} {...interaction}>
      <div className="flex h-full min-h-0 flex-col justify-center">
        <div className="flex items-end gap-2">
          <span
            className="text-[36px] font-semibold leading-none tracking-[-0.04em]"
            style={{
              color: component.style?.valueColor ?? theme.textColor,
              textShadow: component.style?.glow ? `0 0 16px ${alpha(accentColor, '66')}` : undefined,
            }}
          >
            {formatValue(data?.value ?? '--')}
          </span>
          {data?.unit ? <span className="pb-1 text-[12px]" style={{ color: theme.mutedTextColor }}>{data.unit}</span> : null}
        </div>
        {typeof data?.trend === 'number' || data?.trendLabel ? (
          <div className="mt-3 flex items-center gap-2 text-[11px]">
            {typeof data?.trend === 'number' ? (
              <span className="font-medium" style={{ color: trendColor }}>
                {trendPrefix} {Math.abs(data.trend)}%
              </span>
            ) : null}
            {data?.trendLabel ? <span style={{ color: theme.mutedTextColor }}>{data.trendLabel}</span> : null}
          </div>
        ) : null}
      </div>
    </ComponentFrame>
  );
}

function TableComponent({
  component,
  theme,
  ...interaction
}: {
  component: ScreenTableComponent;
  theme: ScreenTheme;
} & ComponentInteraction) {
  const data = component.data;
  return (
    <ComponentFrame component={component} theme={theme} {...interaction}>
      {!data ? (
        <div className="flex h-full items-center justify-center text-[13px]" style={{ color: theme.mutedTextColor }}>
          暂无预览数据
        </div>
      ) : (
        <div className="h-full overflow-hidden">
          <table className="w-full table-fixed border-collapse text-[12px]">
            <thead>
              <tr style={{ color: theme.mutedTextColor }}>
                {data.columns.map((column) => (
                  <th
                    key={column.key}
                    className="border-b px-3 py-2.5 font-medium"
                    style={{
                      width: column.width,
                      textAlign: column.align ?? 'left',
                      borderColor: theme.panelBorderColor,
                    }}
                  >
                    {column.title}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {data.columns.map((column) => (
                    <td
                      key={column.key}
                      className="truncate border-b px-3 py-3"
                      style={{
                        textAlign: column.align ?? 'left',
                        borderColor: alpha(theme.panelBorderColor, '80'),
                        color: theme.textColor,
                      }}
                    >
                      {String(row[column.key] ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ComponentFrame>
  );
}

function renderScreenComponent(
  component: ScreenComponent,
  theme: ScreenTheme,
  interaction: ComponentInteraction,
) {
  switch (component.type) {
    case 'metric':
      return <MetricComponent component={component} theme={theme} {...interaction} />;
    case 'line':
    case 'bar':
    case 'pie':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <ScreenChart component={component} theme={theme} />
        </ComponentFrame>
      );
    case 'table':
      return <TableComponent component={component} theme={theme} {...interaction} />;
    case 'map':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <ScreenNetworkMap component={component} theme={theme} />
        </ComponentFrame>
      );
    case 'ticker':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <ScreenTicker component={component} theme={theme} />
        </ComponentFrame>
      );
    case 'text':
      return (
        <ComponentFrame component={component} theme={theme} {...interaction}>
          <div
            className="flex h-full items-center"
            style={{
              color: component.style?.color ?? theme.textColor,
              fontSize: component.style?.fontSize ?? 16,
              fontWeight: component.style?.fontWeight,
              letterSpacing: component.style?.letterSpacing,
              justifyContent: component.style?.textAlign === 'center'
                ? 'center'
                : component.style?.textAlign === 'right'
                  ? 'flex-end'
                  : 'flex-start',
              textAlign: component.style?.textAlign,
              whiteSpace: 'pre-wrap',
              textShadow: component.style?.glow
                ? `0 0 18px ${alpha(component.style?.accentColor ?? theme.primaryColor, '66')}`
                : undefined,
            }}
          >
            {component.data?.content ?? ''}
          </div>
        </ComponentFrame>
      );
    default:
      return null;
  }
}

const withRuntimeData = (component: ScreenComponent, overrides?: ScreenDataOverrides): ScreenComponent => {
  const data = overrides?.[component.id];
  return data ? ({ ...component, data } as ScreenComponent) : component;
};

function RuntimeScreenComponent({
  component,
  theme,
  selected,
  onSelect,
}: {
  component: ScreenComponent;
  theme: ScreenTheme;
} & ComponentInteraction) {
  return renderScreenComponent(component, theme, { selected, onSelect });
}

export interface ScreenRendererProps {
  template: ScreenTemplate;
  data?: ScreenDataOverrides;
  className?: string;
  selectedComponentId?: string;
  onComponentClick?: (component: ScreenComponent) => void;
}

/**
 * Renders the fixed design canvas and scales it to the available width. Layout,
 * visual style and preview data all come from the template document.
 */
export function ScreenRenderer({
  template,
  data,
  className = '',
  selectedComponentId,
  onComponentClick,
}: ScreenRendererProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    if (!containerRef.current) return undefined;
    const updateScale = () => {
      const width = containerRef.current?.clientWidth ?? template.width;
      setScale(width > 0 ? width / template.width : 1);
    };
    updateScale();
    const observer = new ResizeObserver(updateScale);
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [template.width]);

  return (
    <div
      ref={containerRef}
      className={`relative w-full overflow-hidden ${className}`}
      style={{
        aspectRatio: `${template.width} / ${template.height}`,
        background: template.theme.background,
      }}
    >
      <style>{SCREEN_MOTION_CSS}</style>
      <div
        className="absolute left-0 top-0 overflow-hidden"
        style={{
          width: template.width,
          height: template.height,
          transform: `scale(${scale})`,
          transformOrigin: 'left top',
          background: template.theme.background,
          color: template.theme.textColor,
          fontFamily: template.theme.fontFamily,
        }}
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-60"
          style={{
            backgroundImage: `linear-gradient(${alpha(template.theme.primaryColor, '05')} 1px, transparent 1px), linear-gradient(90deg, ${alpha(template.theme.primaryColor, '05')} 1px, transparent 1px)`,
            backgroundSize: '48px 48px',
            maskImage: 'linear-gradient(180deg, rgba(0,0,0,.55), transparent 88%)',
          }}
        />
        {template.components.map((component) => {
          const runtimeComponent = withRuntimeData(component, data);
          return (
            <RuntimeScreenComponent
              key={component.id}
              component={runtimeComponent}
              theme={template.theme}
              selected={component.id === selectedComponentId}
              onSelect={onComponentClick ? () => onComponentClick(runtimeComponent) : undefined}
            />
          );
        })}
      </div>
    </div>
  );
}
