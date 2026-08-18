import { Dropdown, Input, Modal, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  FilePenLine,
  LayoutDashboard,
  MessageSquareText,
  MoreHorizontal,
  Plus,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export type DashboardEditorSheet = {
  id: string;
  title: string;
};

type DashboardSheetMeta = {
  note?: string;
  hidden?: boolean;
};

type DashboardSheetMetaMap = Record<string, DashboardSheetMeta>;

const SHEET_META_STORAGE_PREFIX = 'yak.dashboard.sheet-meta';

const readSheetMeta = (dashboardKey: string): DashboardSheetMetaMap => {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(`${SHEET_META_STORAGE_PREFIX}:${dashboardKey}`);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

export function DashboardSheetBar({
  dashboardKey,
  sheets,
  activeSheet,
  activeSheetId,
  canAddChart = true,
  onDashboard,
  onChart,
  onReorder,
  onAddChart,
  onRename,
  onDuplicate,
  onDelete,
}: {
  dashboardKey: string;
  sheets: DashboardEditorSheet[];
  activeSheet: 'dashboard' | 'chart';
  activeSheetId?: string;
  canAddChart?: boolean;
  onDashboard: () => void;
  onChart: (sheetId: string) => void;
  onReorder: (sheetIds: string[]) => void;
  onAddChart?: () => void;
  onRename?: (sheetId: string, title: string) => void;
  onDuplicate?: (sheetId: string) => void;
  onDelete?: (sheetId: string) => void;
}) {
  const [draggingId, setDraggingId] = useState<string>();
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);
  const [sheetMeta, setSheetMeta] = useState<DashboardSheetMetaMap>({});
  const [loadedMetaKey, setLoadedMetaKey] = useState<string>();
  const [renameSheet, setRenameSheet] = useState<DashboardEditorSheet>();
  const [renameDraft, setRenameDraft] = useState('');
  const [noteSheet, setNoteSheet] = useState<DashboardEditorSheet>();
  const [noteDraft, setNoteDraft] = useState('');
  const viewportRef = useRef<HTMLDivElement>(null);
  const dashboardRef = useRef<HTMLButtonElement>(null);
  const tabRefs = useRef(new Map<string, HTMLButtonElement>());

  useEffect(() => {
    setLoadedMetaKey(undefined);
    setSheetMeta(readSheetMeta(dashboardKey));
    setLoadedMetaKey(dashboardKey);
  }, [dashboardKey]);

  useEffect(() => {
    if (loadedMetaKey !== dashboardKey || typeof window === 'undefined') return;
    try {
      window.localStorage.setItem(
        `${SHEET_META_STORAGE_PREFIX}:${dashboardKey}`,
        JSON.stringify(sheetMeta),
      );
    } catch {
      // Sheet notes and visibility are editor conveniences. Storage failure must not block editing.
    }
  }, [dashboardKey, loadedMetaKey, sheetMeta]);

  const visibleSheets = useMemo(
    () => sheets.filter((sheet) => !sheetMeta[sheet.id]?.hidden),
    [sheetMeta, sheets],
  );
  const hiddenSheets = useMemo(
    () => sheets.filter((sheet) => sheetMeta[sheet.id]?.hidden),
    [sheetMeta, sheets],
  );

  const patchSheetMeta = useCallback((sheetId: string, patch: DashboardSheetMeta) => {
    setSheetMeta((current) => ({
      ...current,
      [sheetId]: { ...current[sheetId], ...patch },
    }));
  }, []);

  const updateScrollState = useCallback(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const maximum = Math.max(0, viewport.scrollWidth - viewport.clientWidth);
    setCanScrollLeft(viewport.scrollLeft > 1);
    setCanScrollRight(viewport.scrollLeft < maximum - 1);
  }, []);

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
  }, [visibleSheets.length, updateScrollState]);

  useEffect(() => {
    if (activeSheet !== 'chart' || !activeSheetId || sheetMeta[activeSheetId]?.hidden) return;
    tabRefs.current.get(activeSheetId)?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest',
    });
    window.requestAnimationFrame(updateScrollState);
  }, [activeSheet, activeSheetId, sheetMeta, updateScrollState]);

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

  const focusSheet = (id: string) => {
    window.requestAnimationFrame(() => {
      if (id === 'dashboard') dashboardRef.current?.focus();
      else tabRefs.current.get(id)?.focus();
    });
  };

  const handleNavigation = (
    event: React.KeyboardEvent<HTMLButtonElement>,
    currentId: string,
  ) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const ids = ['dashboard', ...visibleSheets.map((sheet) => sheet.id)];
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

  const openRename = (sheet: DashboardEditorSheet) => {
    setRenameSheet(sheet);
    setRenameDraft(sheet.title);
  };

  const openNote = (sheet: DashboardEditorSheet) => {
    setNoteSheet(sheet);
    setNoteDraft(sheetMeta[sheet.id]?.note ?? '');
  };

  const hideSheet = (sheet: DashboardEditorSheet) => {
    patchSheetMeta(sheet.id, { hidden: true });
    if (activeSheet === 'chart' && activeSheetId === sheet.id) onDashboard();
  };

  const restoreSheet = (sheetId: string) => {
    patchSheetMeta(sheetId, { hidden: false });
    onChart(sheetId);
  };

  const confirmDelete = (sheet: DashboardEditorSheet) => {
    Modal.confirm({
      title: '删除 Sheet？',
      content: `“${sheet.title}”对应的图表组件也会从当前仪表盘中删除。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        if (activeSheet === 'chart' && activeSheetId === sheet.id) onDashboard();
        onDelete?.(sheet.id);
      },
    });
  };

  const menuFor = (sheet: DashboardEditorSheet): MenuProps => ({
    items: [
      {
        key: 'rename',
        icon: <FilePenLine size={14} />,
        label: '重命名',
        disabled: !onRename,
      },
      {
        key: 'duplicate',
        icon: <Copy size={14} />,
        label: '复制',
        disabled: !onDuplicate,
      },
      {
        key: 'note',
        icon: <MessageSquareText size={14} />,
        label: '备注',
      },
      { type: 'divider' },
      {
        key: 'hide',
        icon: <EyeOff size={14} />,
        label: '隐藏',
      },
      {
        key: 'delete',
        icon: <Trash2 size={14} />,
        label: '删除',
        danger: true,
        disabled: !onDelete,
      },
    ],
    onClick: ({ key, domEvent }) => {
      domEvent.stopPropagation();
      if (key === 'rename') openRename(sheet);
      else if (key === 'duplicate') onDuplicate?.(sheet.id);
      else if (key === 'note') openNote(sheet);
      else if (key === 'hide') hideSheet(sheet);
      else if (key === 'delete') confirmDelete(sheet);
    },
  });

  const hiddenMenu: MenuProps = {
    items: hiddenSheets.map((sheet) => ({
      key: sheet.id,
      icon: <Eye size={14} />,
      label: (
        <div className="flex min-w-[150px] items-center justify-between gap-4">
          <span className="max-w-[190px] truncate">{sheet.title}</span>
          <span className="text-[10px] text-[#98a2b3]">恢复</span>
        </div>
      ),
    })),
    onClick: ({ key }) => restoreSheet(String(key)),
  };

  const dashboardActive = activeSheet === 'dashboard';

  return (
    <>
      <div
        className="flex h-9 shrink-0 items-stretch border-t border-[#d9dde3] bg-[#f4f6f8] shadow-[0_-1px_0_rgba(16,24,40,.02)]"
        aria-label="仪表盘编辑 Sheet"
      >
        <button
          ref={dashboardRef}
          type="button"
          role="tab"
          aria-selected={dashboardActive}
          className={[
            'relative flex h-full shrink-0 items-center gap-1.5 border-r border-[#dfe3e8] px-3.5 text-[11px] outline-none transition-colors',
            'focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-[var(--yak-brand-color)]',
            dashboardActive
              ? 'bg-white font-medium text-[#161823] after:absolute after:inset-x-0 after:top-0 after:h-[2px] after:bg-[var(--yak-brand-color)]'
              : 'text-[#626b78] hover:bg-white/80 hover:text-[#344054]',
          ].join(' ')}
          onClick={onDashboard}
          onKeyDown={(event) => handleNavigation(event, 'dashboard')}
        >
          <LayoutDashboard size={13} className={dashboardActive ? 'text-[var(--yak-brand-color)]' : ''} />
          <span>仪表盘</span>
        </button>

        <div
          ref={viewportRef}
          className="flex min-w-0 flex-1 items-stretch overflow-x-auto overflow-y-hidden [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          role="tablist"
        >
          {visibleSheets.length ? visibleSheets.map((sheet) => {
            const active = activeSheet === 'chart' && activeSheetId === sheet.id;
            const note = sheetMeta[sheet.id]?.note?.trim();
            return (
              <div
                key={sheet.id}
                draggable
                title={note ? `${sheet.title}\n备注：${note}` : `${sheet.title} · 拖动可调整 Sheet 顺序`}
                className={[
                  'group relative flex h-full min-w-[116px] max-w-[210px] shrink-0 items-stretch border-r border-[#dfe3e8] transition-colors',
                  active
                    ? 'bg-white text-[#161823] after:absolute after:inset-x-0 after:top-0 after:h-[2px] after:bg-[var(--yak-brand-color)]'
                    : 'bg-transparent text-[#626b78] hover:bg-white/80 hover:text-[#344054]',
                  draggingId === sheet.id ? 'opacity-45' : '',
                ].join(' ')}
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
                <button
                  ref={(node) => {
                    if (node) tabRefs.current.set(sheet.id, node);
                    else tabRefs.current.delete(sheet.id);
                  }}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  className="flex min-w-0 flex-1 items-center gap-1.5 bg-transparent py-0 pl-3 pr-1 text-left text-[11px] outline-none focus-visible:ring-1 focus-visible:ring-inset focus-visible:ring-[var(--yak-brand-color)]"
                  onClick={() => onChart(sheet.id)}
                  onKeyDown={(event) => handleNavigation(event, sheet.id)}
                >
                  <BarChart3
                    size={12}
                    className={active ? 'shrink-0 text-[var(--yak-brand-color)]' : 'shrink-0 text-[#7d8591]'}
                  />
                  <span className={active ? 'truncate font-medium' : 'truncate'}>{sheet.title}</span>
                  {note ? (
                    <span
                      className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#aeb5bf]"
                      aria-label="已有备注"
                    />
                  ) : null}
                </button>

                <Dropdown menu={menuFor(sheet)} trigger={['click']} placement="topLeft">
                  <button
                    type="button"
                    aria-label={`${sheet.title} Sheet 操作`}
                    draggable={false}
                    className={[
                      'mr-1 flex w-6 shrink-0 items-center justify-center self-center rounded-[4px] text-[#818995] transition-all',
                      'opacity-0 hover:bg-[#eef0f3] hover:text-[#344054] focus:opacity-100 focus:outline-none group-hover:opacity-100',
                    ].join(' ')}
                    onClick={(event) => event.stopPropagation()}
                    onMouseDown={(event) => event.stopPropagation()}
                  >
                    <MoreHorizontal size={14} />
                  </button>
                </Dropdown>
              </div>
            );
          }) : (
            <div className="flex h-full items-center px-3 text-[10px] text-[#98a2b3]">
              暂无图表 Sheet
            </div>
          )}
        </div>

        <div className="flex shrink-0 items-stretch border-l border-[#dfe3e8] bg-[#f4f6f8]">
          <Tooltip title="向左滚动">
            <button
              type="button"
              aria-label="向左滚动图表 Sheet"
              disabled={!canScrollLeft}
              className="flex w-7 items-center justify-center bg-transparent text-[#687180] transition-colors hover:bg-white disabled:cursor-default disabled:text-[#c7ccd4]"
              onClick={() => scrollSheets(-1)}
            >
              <ChevronLeft size={13} />
            </button>
          </Tooltip>
          <Tooltip title="向右滚动">
            <button
              type="button"
              aria-label="向右滚动图表 Sheet"
              disabled={!canScrollRight}
              className="flex w-7 items-center justify-center bg-transparent text-[#687180] transition-colors hover:bg-white disabled:cursor-default disabled:text-[#c7ccd4]"
              onClick={() => scrollSheets(1)}
            >
              <ChevronRight size={13} />
            </button>
          </Tooltip>

          {hiddenSheets.length ? (
            <Dropdown menu={hiddenMenu} trigger={['click']} placement="topRight">
              <Tooltip title={`${hiddenSheets.length} 个隐藏 Sheet`}>
                <button
                  type="button"
                  aria-label="显示隐藏 Sheet"
                  className="relative flex w-8 items-center justify-center border-l border-[#dfe3e8] bg-transparent text-[#687180] transition-colors hover:bg-white"
                >
                  <EyeOff size={13} />
                  <span className="absolute right-0.5 top-0.5 min-w-[12px] rounded-full bg-[#8d95a1] px-0.5 text-center text-[8px] leading-[12px] text-white">
                    {hiddenSheets.length}
                  </span>
                </button>
              </Tooltip>
            </Dropdown>
          ) : null}

          <Tooltip title="新建图表 Sheet">
            <button
              type="button"
              aria-label="新建图表 Sheet"
              disabled={!onAddChart || !canAddChart}
              className="flex w-9 items-center justify-center border-l border-[#dfe3e8] bg-transparent text-[#596271] transition-colors hover:bg-white hover:text-[var(--yak-brand-color)] disabled:cursor-not-allowed disabled:text-[#c7ccd4]"
              onClick={onAddChart}
            >
              <Plus size={14} />
            </button>
          </Tooltip>
        </div>
      </div>

      <Modal
        open={Boolean(renameSheet)}
        title="重命名 Sheet"
        okText="确定"
        cancelText="取消"
        width={400}
        okButtonProps={{ disabled: !renameDraft.trim() }}
        onCancel={() => setRenameSheet(undefined)}
        onOk={() => {
          const value = renameDraft.trim();
          if (!renameSheet || !value) return;
          onRename?.(renameSheet.id, value);
          setRenameSheet(undefined);
        }}
      >
        <Input
          autoFocus
          value={renameDraft}
          maxLength={60}
          placeholder="输入 Sheet 名称"
          onChange={(event) => setRenameDraft(event.target.value)}
          onPressEnter={() => {
            const value = renameDraft.trim();
            if (!renameSheet || !value) return;
            onRename?.(renameSheet.id, value);
            setRenameSheet(undefined);
          }}
        />
      </Modal>

      <Modal
        open={Boolean(noteSheet)}
        title={noteSheet ? `备注 · ${noteSheet.title}` : 'Sheet 备注'}
        okText="保存"
        cancelText="取消"
        width={440}
        onCancel={() => setNoteSheet(undefined)}
        onOk={() => {
          if (!noteSheet) return;
          patchSheetMeta(noteSheet.id, { note: noteDraft.trim() });
          setNoteSheet(undefined);
        }}
      >
        <Input.TextArea
          autoFocus
          value={noteDraft}
          maxLength={300}
          autoSize={{ minRows: 4, maxRows: 8 }}
          placeholder="记录这个 Sheet 的用途、口径或待办事项"
          onChange={(event) => setNoteDraft(event.target.value)}
        />
        <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
          Sheet 备注仅保存在当前浏览器，不进入仪表盘服务端版本。
        </div>
      </Modal>
    </>
  );
}
