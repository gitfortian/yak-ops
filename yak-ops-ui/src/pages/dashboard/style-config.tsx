import type { AnalysisSpec } from '@/components/analysis/model';
import { Input, InputNumber, Switch } from 'antd';
import { Maximize2 } from 'lucide-react';
import type { DashboardWidget } from './model';

export function StyleConfigPanel({
  spec,
  title,
  widget,
  onSpec,
  onTitle,
  onLayout,
}: {
  spec: AnalysisSpec;
  title: string;
  widget: DashboardWidget;
  onSpec: (patch: Partial<AnalysisSpec>) => void;
  onTitle: (title: string) => void;
  onLayout: (patch: Partial<DashboardWidget>) => void;
}) {
  const style = spec.style;
  const updateStyle = (patch: Partial<AnalysisSpec['style']>) => onSpec({ style: { ...style, ...patch } });
  return (
    <div className="p-3">
      <label className="mb-1.5 block text-[11px] font-medium text-[#667085]">图表标题</label>
      <Input size="small" value={title} onChange={(event) => onTitle(event.target.value)} />
      <div className="mt-4 border-t border-[#edf0f3] pt-4">
        <div className="mb-3 text-[11px] font-medium text-[#667085]">图表样式</div>
        <div className="space-y-3 text-[11px] text-[#475467]">
          {spec.type !== 'metric' && spec.type !== 'table' ? <label className="flex items-center justify-between"><span>显示图例</span><Switch size="small" checked={style.showLegend} onChange={(showLegend) => updateStyle({ showLegend })} /></label> : null}
          {spec.type !== 'metric' && spec.type !== 'table' ? <label className="flex items-center justify-between"><span>显示数据标签</span><Switch size="small" checked={style.showDataLabels} onChange={(showDataLabels) => updateStyle({ showDataLabels })} /></label> : null}
          {spec.type === 'line' ? <label className="flex items-center justify-between"><span>平滑曲线</span><Switch size="small" checked={style.smooth} onChange={(smooth) => updateStyle({ smooth })} /></label> : null}
          {spec.type === 'line' || spec.type === 'bar' ? <label className="flex items-center justify-between"><span>显示网格线</span><Switch size="small" checked={style.showGrid} onChange={(showGrid) => updateStyle({ showGrid })} /></label> : null}
        </div>
      </div>
      <div className="mt-5 border-t border-[#edf0f3] pt-4">
        <div className="mb-3 flex items-center gap-1.5 text-[11px] font-medium text-[#667085]"><Maximize2 size={12} />布局</div>
        <div className="grid grid-cols-2 gap-2">
          <div><label className="mb-1 block text-[10px] text-[#98a2b3]">宽度</label><InputNumber size="small" min={widget.minW ?? 4} max={24} className="w-full" value={widget.w} onChange={(w) => onLayout({ w: Number(w ?? widget.w) })} /></div>
          <div><label className="mb-1 block text-[10px] text-[#98a2b3]">高度</label><InputNumber size="small" min={widget.minH ?? 3} max={30} className="w-full" value={widget.h} onChange={(h) => onLayout({ h: Number(h ?? widget.h) })} /></div>
          <div><label className="mb-1 block text-[10px] text-[#98a2b3]">X</label><InputNumber size="small" min={0} max={23} className="w-full" value={widget.x} onChange={(x) => onLayout({ x: Number(x ?? widget.x) })} /></div>
          <div><label className="mb-1 block text-[10px] text-[#98a2b3]">Y</label><InputNumber size="small" min={0} className="w-full" value={widget.y} onChange={(y) => onLayout({ y: Number(y ?? widget.y) })} /></div>
        </div>
      </div>
    </div>
  );
}
