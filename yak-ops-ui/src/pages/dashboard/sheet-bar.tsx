import { BarChart3, LayoutDashboard } from 'lucide-react';
import { useState } from 'react';

export type DashboardEditorSheet = {
  id: string;
  title: string;
};

export function DashboardSheetBar({
  sheets,
  activeSheet,
  activeSheetId,
  onDashboard,
  onChart,
  onReorder,
}: {
  sheets: DashboardEditorSheet[];
  activeSheet: 'dashboard' | 'chart';
  activeSheetId?: string;
  onDashboard: () => void;
  onChart: (sheetId: string) => void;
  onReorder: (sheetIds: string[]) => void;
}) {
  const [draggingId, setDraggingId] = useState<string>();

  const moveBefore = (targetId: string) => {
    if (!draggingId || draggingId === targetId) return;
    const ids = sheets.map((sheet) => sheet.id);
    const from = ids.indexOf(draggingId);
    const to = ids.indexOf(targetId);
    if (from < 0 || to < 0) return;
    ids.splice(from, 1);
    ids.splice(to, 0, draggingId);
    onReorder(ids);
  };

  const baseClass = 'flex h-full shrink-0 items-center gap-1.5 border-x px-3 text-[11px] transition-colors';
  const inactiveClass = 'border-transparent text-[#667085] hover:bg-white/70 hover:text-[#344054]';
  const activeClass = 'border-[#dfe3e8] bg-white font-medium text-[#161823] shadow-[inset_0_2px_0_var(--yak-brand-color)]';

  return (
    <div className="flex h-10 shrink-0 items-stretch border-t border-[#dfe3e8] bg-[#f7f8fa]">
      <button
        type="button"
        className={`${baseClass} ${activeSheet === 'dashboard' ? activeClass : inactiveClass}`}
        onClick={onDashboard}
      >
        <LayoutDashboard size={13} />
        仪表盘
      </button>

      <div className="h-full w-px shrink-0 bg-[#e5e7eb]" />

      <div className="flex min-w-0 flex-1 items-stretch overflow-x-auto overflow-y-hidden">
        {sheets.map((sheet) => {
          const active = activeSheet === 'chart' && activeSheetId === sheet.id;
          return (
            <button
              key={sheet.id}
              type="button"
              draggable
              title={`${sheet.title} · 拖动可调整 Sheet 顺序`}
              className={`${baseClass} max-w-[220px] cursor-pointer ${active ? activeClass : inactiveClass} ${draggingId === sheet.id ? 'opacity-45' : ''}`}
              onClick={() => onChart(sheet.id)}
              onDragStart={(event) => {
                setDraggingId(sheet.id);
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', sheet.id);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                event.dataTransfer.dropEffect = 'move';
                moveBefore(sheet.id);
              }}
              onDrop={(event) => {
                event.preventDefault();
                moveBefore(sheet.id);
              }}
              onDragEnd={() => setDraggingId(undefined)}
            >
              <BarChart3 size={12} className="shrink-0" />
              <span className="truncate">{sheet.title}</span>
            </button>
          );
        })}
      </div>

      <div className="flex shrink-0 items-center border-l border-[#e5e7eb] px-3 text-[10px] text-[#98a2b3]">
        {sheets.length} 个图表
      </div>
    </div>
  );
}
