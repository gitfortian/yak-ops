import { BarChart3, ChevronLeft, ChevronRight, LayoutDashboard } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

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
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const viewportRef = useRef<HTMLDivElement>(null);
  const dashboardRef = useRef<HTMLButtonElement>(null);
  const tabRefs = useRef(new Map<string, HTMLButtonElement>());

  const updateScrollState = () => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const maximum = Math.max(0, viewport.scrollWidth - viewport.clientWidth);
    setCanScrollLeft(viewport.scrollLeft > 1);
    setCanScrollRight(viewport.scrollLeft < maximum - 1);
  };

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return undefined;
    updateScrollState();
    const observer = new ResizeObserver(updateScrollState);
    observer.observe(viewport);
    viewport.addEventListener('scroll', updateScrollState, { passive: true });
    return () => {
      observer.disconnect();
      viewport.removeEventListener('scroll', updateScrollState);
    };
  }, [sheets.length]);

  useEffect(() => {
    if (activeSheet !== 'chart' || !activeSheetId) return;
    tabRefs.current.get(activeSheetId)?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest',
    });
    window.requestAnimationFrame(updateScrollState);
  }, [activeSheet, activeSheetId]);

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

  const focusSheet = (id: 'dashboard' | string) => {
    window.requestAnimationFrame(() => {
      if (id === 'dashboard') dashboardRef.current?.focus();
      else tabRefs.current.get(id)?.focus();
    });
  };

  const handleNavigation = (
    event: React.KeyboardEvent<HTMLButtonElement>,
    currentId: 'dashboard' | string,
  ) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const ids = ['dashboard', ...sheets.map((sheet) => sheet.id)];
    const currentIndex = Math.max(0, ids.indexOf(currentId));
    let nextIndex = currentIndex;
    if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = ids.length - 1;
    else if (event.key === 'ArrowLeft') nextIndex = Math.max(0, currentIndex - 1);
    else if (event.key === 'ArrowRight') nextIndex = Math.min(ids.length - 1, currentIndex + 1);
    const nextId = ids[nextIndex];
    if (!nextId || nextId === currentId) return;
    if (nextId === 'dashboard') onDashboard();
    else onChart(nextId);
    focusSheet(nextId);
  };

  const scrollSheets = (direction: -1 | 1) => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    viewport.scrollBy({
      left: direction * Math.max(180, Math.round(viewport.clientWidth * 0.65)),
      behavior: 'smooth',
    });
  };

  const baseClass = 'flex h-full shrink-0 items-center gap-1.5 border-x px-3 text-[11px] transition-colors focus-visible:z-10 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-[var(--yak-brand-color)]';
  const inactiveClass = 'border-transparent text-[#667085] hover:bg-white/70 hover:text-[#344054]';
  const activeClass = 'border-[#dfe3e8] bg-white font-medium text-[#161823] shadow-[inset_0_2px_0_var(--yak-brand-color)]';

  return (
    <div className="flex h-10 shrink-0 items-stretch border-t border-[#dfe3e8] bg-[#f7f8fa]" role="tablist" aria-label="仪表盘编辑 Sheet">
      <button
        ref={dashboardRef}
        type="button"
        role="tab"
        aria-selected={activeSheet === 'dashboard'}
        className={`${baseClass} ${activeSheet === 'dashboard' ? activeClass : inactiveClass}`}
        onClick={onDashboard}
        onKeyDown={(event) => handleNavigation(event, 'dashboard')}
      >
        <LayoutDashboard size={13} />
        仪表盘
      </button>

      <div className="h-full w-px shrink-0 bg-[#e5e7eb]" />

      <button
        type="button"
        aria-label="向左滚动图表 Sheet"
        disabled={!canScrollLeft}
        className="flex w-7 shrink-0 items-center justify-center border-0 border-r border-[#e5e7eb] bg-transparent text-[#667085] hover:bg-white disabled:cursor-default disabled:text-[#c7ccd4]"
        onClick={() => scrollSheets(-1)}
      >
        <ChevronLeft size={13} />
      </button>

      <div
        ref={viewportRef}
        className="flex min-w-0 flex-1 items-stretch overflow-x-auto overflow-y-hidden [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {sheets.length ? sheets.map((sheet) => {
          const active = activeSheet === 'chart' && activeSheetId === sheet.id;
          return (
            <button
              key={sheet.id}
              ref={(node) => {
                if (node) tabRefs.current.set(sheet.id, node);
                else tabRefs.current.delete(sheet.id);
              }}
              type="button"
              role="tab"
              aria-selected={active}
              draggable
              title={`${sheet.title} · 拖动可调整 Sheet 顺序`}
              className={`${baseClass} max-w-[220px] cursor-pointer ${active ? activeClass : inactiveClass} ${draggingId === sheet.id ? 'opacity-45' : ''}`}
              onClick={() => onChart(sheet.id)}
              onKeyDown={(event) => handleNavigation(event, sheet.id)}
              onDragStart={(event) => {
                setDraggingId(sheet.id);
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', sheet.id);
              }}
              onDragEnter={() => moveBefore(sheet.id)}
              onDragOver={(event) => {
                event.preventDefault();
                event.dataTransfer.dropEffect = 'move';
              }}
              onDrop={(event) => event.preventDefault()}
              onDragEnd={() => setDraggingId(undefined)}
            >
              <BarChart3 size={12} className="shrink-0" />
              <span className="truncate">{sheet.title}</span>
            </button>
          );
        }) : (
          <div className="flex h-full items-center px-3 text-[10px] text-[#98a2b3]">
            暂无图表 Sheet
          </div>
        )}
      </div>

      <button
        type="button"
        aria-label="向右滚动图表 Sheet"
        disabled={!canScrollRight}
        className="flex w-7 shrink-0 items-center justify-center border-0 border-l border-[#e5e7eb] bg-transparent text-[#667085] hover:bg-white disabled:cursor-default disabled:text-[#c7ccd4]"
        onClick={() => scrollSheets(1)}
      >
        <ChevronRight size={13} />
      </button>

      <div className="flex shrink-0 items-center border-l border-[#e5e7eb] px-3 text-[10px] text-[#98a2b3]">
        {sheets.length} 个图表
      </div>
    </div>
  );
}
