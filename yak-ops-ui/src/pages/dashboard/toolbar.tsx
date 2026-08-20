import { Button, Input, Tooltip } from 'antd';
import {
  BarChart3,
  ChevronLeft,
  Eye,
  Gauge,
  History,
  Palette,
  Redo2,
  Save,
  Send,
  Undo2,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { DashboardPerformanceModal } from './performance-modal';

export function DashboardToolbar({
  name,
  dashboardId,
  currentVersionNo,
  publishedVersionNo,
  saving,
  publishing,
  preview,
  dirty,
  canUndo,
  canRedo,
  canAddChart,
  canPublish,
  hasPublishedVersion,
  hasUnpublishedDraft,
  onBack,
  onName,
  onUndo,
  onRedo,
  onAddChart,
  onDashboardStyle,
  onHistory,
  onPreview,
  onSaveDraft,
  onPublish,
}: {
  name: string;
  dashboardId: string;
  currentVersionNo?: number;
  publishedVersionNo?: number;
  saving: boolean;
  publishing: boolean;
  preview: boolean;
  dirty: boolean;
  canUndo: boolean;
  canRedo: boolean;
  canAddChart: boolean;
  canPublish: boolean;
  hasPublishedVersion: boolean;
  hasUnpublishedDraft: boolean;
  onBack: () => void;
  onName: (name: string) => void;
  onUndo: () => void;
  onRedo: () => void;
  onAddChart: () => void;
  onDashboardStyle: () => void;
  onHistory: () => void;
  onPreview: () => void;
  onSaveDraft: () => void;
  onPublish: () => void;
}) {
  const [performanceOpen, setPerformanceOpen] = useState(false);
  const previewAnchorRef = useRef<HTMLDivElement>(null);
  const persisted = /^\d+$/.test(dashboardId);
  const saveDisabled = persisted && !dirty;
  const busy = saving || publishing;

  const lifecycleText = (() => {
    if (!persisted || !currentVersionNo) return '未保存';
    if (hasPublishedVersion && !hasUnpublishedDraft) return `已发布 V${publishedVersionNo}`;
    return hasPublishedVersion
      ? `草稿 V${currentVersionNo} · 已发布 V${publishedVersionNo}`
      : `草稿 V${currentVersionNo} · 未发布`;
  })();

  useEffect(() => {
    if (!preview) return undefined;
    const root = previewAnchorRef.current?.parentElement;
    if (!root) return undefined;

    const previousBodyOverflow = document.body.style.overflow;
    root.classList.add('dashboard-preview-fullscreen');
    document.body.style.overflow = 'hidden';

    return () => {
      root.classList.remove('dashboard-preview-fullscreen');
      document.body.style.overflow = previousBodyOverflow;
    };
  }, [preview]);

  if (preview) {
    return (
      <>
        <div ref={previewAnchorRef} className="dashboard-preview-anchor">
          <Button
            type="text"
            size="small"
            className="!h-8 !rounded-[7px] !border !border-white/20 !bg-[rgba(17,24,39,.72)] !px-2.5 !text-[11px] !font-medium !text-white !shadow-[0_4px_16px_rgba(15,23,42,.16)] backdrop-blur-sm hover:!bg-[rgba(17,24,39,.86)] hover:!text-white"
            icon={<X size={13} />}
            onClick={onPreview}
          >
            退出预览
          </Button>
        </div>

        <style>{`
          .dashboard-preview-fullscreen {
            position: fixed !important;
            inset: 0 !important;
            z-index: 1200 !important;
            width: 100vw !important;
            height: 100vh !important;
            min-height: 0 !important;
            background: var(--dashboard-canvas-bg, #f5f6f8) !important;
          }
          .dashboard-preview-fullscreen > .dashboard-preview-anchor {
            position: fixed;
            top: 12px;
            right: 14px;
            z-index: 1300;
            pointer-events: none;
          }
          .dashboard-preview-fullscreen > .dashboard-preview-anchor > button {
            pointer-events: auto;
          }
          .dashboard-preview-fullscreen main > div {
            min-height: 100% !important;
            padding: 0 !important;
          }
          .dashboard-preview-fullscreen main > div > div {
            width: 100% !important;
            max-width: none !important;
            min-height: 100% !important;
            margin: 0 !important;
            border: 0 !important;
            box-shadow: none !important;
          }
        `}</style>
      </>
    );
  }

  return (
    <>
      <header className="shrink-0 border-b border-[#dce3ea] bg-[#eef3f8]">
        <div className="flex h-10 items-center justify-between border-b border-[#dce4ee] bg-[#eef3f8] px-3">
          <div className="flex min-w-0 items-center gap-2.5">
            <Tooltip title="退出编辑器">
              <Button
                type="text"
                className="!flex !h-7 !w-7 !min-w-0 !items-center !justify-center !rounded-[6px] !p-0 !text-[#526075] hover:!bg-[#e1e8f1] hover:!text-[#1f2a44]"
                icon={<ChevronLeft size={15} />}
                disabled={preview || busy}
                onClick={onBack}
              />
            </Tooltip>
            <div className="h-5 w-px bg-[#ccd6e2]" />
            <Input
              variant="borderless"
              value={name}
              disabled={preview || busy}
              onChange={(event) => onName(event.target.value)}
              className="!h-6 !w-[250px] !bg-transparent !px-0 !text-[13px] !font-semibold !leading-6 !text-[#172033]"
            />
            <div className="hidden items-center gap-2 whitespace-nowrap text-[10px] text-[#6f7d91] lg:flex">
              <span>{lifecycleText}</span>
              {dirty ? (
                <>
                  <span className="h-1 w-1 rounded-full bg-[#9ca9ba]" />
                  <span className="text-[#526075]">有未保存修改</span>
                </>
              ) : null}
            </div>
          </div>

          {!preview ? (
            <div className="flex items-center gap-1.5">
              <Tooltip title={saveDisabled ? '当前没有需要保存到草稿的修改' : '保存草稿 Ctrl/Cmd + S'}>
                <span>
                  <Button
                    size="small"
                    className="!h-7 !rounded-[6px] !border-[#cfd8e4] !bg-[rgba(255,255,255,.72)] !px-2.5 !text-[11px] !text-[#344054] hover:!border-[#bfcad8] hover:!bg-white"
                    loading={saving}
                    disabled={saveDisabled || publishing}
                    icon={<Save size={12} />}
                    onClick={onSaveDraft}
                  >
                    保存草稿
                  </Button>
                </span>
              </Tooltip>
              <Tooltip title={!canPublish ? '当前草稿已经是已发布版本' : undefined}>
                <span>
                  <Button
                    size="small"
                    type="primary"
                    className="!h-7 !rounded-[6px] !px-3 !text-[11px] !shadow-none"
                    loading={publishing}
                    disabled={!canPublish || saving}
                    icon={<Send size={12} />}
                    onClick={onPublish}
                  >
                    {hasPublishedVersion ? '发布更新' : '发布'}
                  </Button>
                </span>
              </Tooltip>
            </div>
          ) : null}
        </div>

        <div className="flex h-8 items-center justify-between bg-white px-3">
          <div className="flex items-center gap-1">
            {!preview ? (
              <>
                <Button
                  type="text"
                  size="small"
                  className="!h-7 !rounded-[5px] !px-2 !text-[12px] !font-medium !text-[var(--yak-brand-color)] hover:!bg-[var(--yak-brand-color-soft)] hover:!text-[var(--yak-brand-color)]"
                  disabled={!canAddChart || busy}
                  icon={<BarChart3 size={13} />}
                  onClick={onAddChart}
                >
                  添加图表
                </Button>
                <div className="mx-1 h-4 w-px bg-[#e1e5ea]" />
                <Tooltip title="撤销 Ctrl/Cmd + Z">
                  <Button
                    type="text"
                    className="!h-7 !w-7 !min-w-0 !rounded-[5px] !p-0 !text-[#667085] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
                    icon={<Undo2 size={13} />}
                    disabled={!canUndo || busy}
                    onClick={onUndo}
                  />
                </Tooltip>
                <Tooltip title="重做 Ctrl/Cmd + Shift + Z">
                  <Button
                    type="text"
                    className="!h-7 !w-7 !min-w-0 !rounded-[5px] !p-0 !text-[#667085] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
                    icon={<Redo2 size={13} />}
                    disabled={!canRedo || busy}
                    onClick={onRedo}
                  />
                </Tooltip>
              </>
            ) : (
              <span className="text-[12px] font-medium text-[#161823]">预览模式</span>
            )}
          </div>

          <div className="flex items-center gap-1">
            {!preview ? (
              <>
                <Button
                  type="text"
                  size="small"
                  className="!h-7 !rounded-[5px] !px-2 !text-[12px] !font-medium !text-[#161823] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
                  icon={<Palette size={13} />}
                  onClick={onDashboardStyle}
                >
                  仪表盘样式
                </Button>
                <Tooltip title={persisted ? undefined : '请先保存仪表盘，再查看 Dataset 查询性能'}>
                  <span>
                    <Button
                      type="text"
                      size="small"
                      className="!h-7 !rounded-[5px] !px-2 !text-[12px] !font-medium !text-[#161823] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
                      icon={<Gauge size={13} />}
                      disabled={!persisted}
                      onClick={() => setPerformanceOpen(true)}
                    >
                      性能分析
                    </Button>
                  </span>
                </Tooltip>
              </>
            ) : null}

            {persisted && currentVersionNo && !preview ? (
              <Tooltip title="历史版本">
                <Button
                  type="text"
                  className="!flex !h-7 !w-7 !min-w-0 !items-center !justify-center !rounded-[5px] !p-0 !text-[#344054] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
                  disabled={busy}
                  icon={<History size={13} />}
                  onClick={onHistory}
                />
              </Tooltip>
            ) : null}
            <Button
              type="text"
              size="small"
              className="!h-7 !rounded-[5px] !px-2 !text-[12px] !font-medium !text-[#161823] hover:!bg-[#f3f5f7] hover:!text-[#161823]"
              disabled={busy}
              icon={preview ? <X size={13} /> : <Eye size={13} />}
              onClick={onPreview}
            >
              {preview ? '退出预览' : '预览'}
            </Button>
          </div>
        </div>
      </header>

      {persisted ? (
        <DashboardPerformanceModal
          open={performanceOpen}
          dashboardId={dashboardId}
          dashboardName={name}
          onClose={() => setPerformanceOpen(false)}
        />
      ) : null}
    </>
  );
}
