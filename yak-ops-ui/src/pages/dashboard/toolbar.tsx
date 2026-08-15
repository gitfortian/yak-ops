import { Button, Input, Tooltip } from 'antd';
import {
  BarChart3,
  ChevronLeft,
  Eye,
  History,
  Redo2,
  Save,
  Send,
  Undo2,
  X,
} from 'lucide-react';

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
  onHistory: () => void;
  onPreview: () => void;
  onSaveDraft: () => void;
  onPublish: () => void;
}) {
  const persisted = /^\d+$/.test(dashboardId);
  const saveDisabled = persisted && !dirty;
  const busy = saving || publishing;

  const lifecycle = (() => {
    if (!persisted || !currentVersionNo) {
      return <span className="rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[10px] text-[#667085]">未保存</span>;
    }
    if (hasPublishedVersion && !hasUnpublishedDraft) {
      return (
        <span className="rounded-[3px] bg-[#f0f9f4] px-2 py-0.5 text-[10px] font-medium text-[#1d7a4b]">
          已发布 V{publishedVersionNo}
        </span>
      );
    }
    return (
      <div className="flex shrink-0 items-center gap-1.5">
        <span className="rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[10px] font-medium text-[#475467]">
          草稿 V{currentVersionNo}
        </span>
        <span className="text-[10px] text-[#98a2b3]">
          {hasPublishedVersion ? `已发布 V${publishedVersionNo}` : '未发布'}
        </span>
      </div>
    );
  })();

  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#dfe3e8] bg-white px-3">
      <div className="flex min-w-0 items-center gap-2">
        <Button
          size="small"
          type="text"
          icon={<ChevronLeft size={14} />}
          disabled={preview || busy}
          onClick={onBack}
        >
          仪表盘
        </Button>
        <span className="h-5 w-px bg-[#e5e7eb]" />
        <Input
          variant="borderless"
          value={name}
          disabled={preview || busy}
          onChange={(event) => onName(event.target.value)}
          className="w-[260px] px-1 text-[13px] font-semibold text-[#161823]"
        />
        {lifecycle}
        {dirty ? (
          <span className="flex shrink-0 items-center gap-1.5 text-[10px] text-[#667085]">
            <span className="h-1.5 w-1.5 rounded-full bg-[#98a2b3]" />
            有未保存修改
          </span>
        ) : null}
      </div>

      <div className="flex items-center gap-2">
        {!preview ? (
          <>
            <div className="flex items-center rounded-[5px] border border-[#e5e7eb] bg-white p-0.5">
              <Tooltip title="撤销 Ctrl/Cmd + Z">
                <Button
                  size="small"
                  type="text"
                  className="h-6 w-7 px-0"
                  icon={<Undo2 size={13} />}
                  disabled={!canUndo || busy}
                  onClick={onUndo}
                />
              </Tooltip>
              <Tooltip title="重做 Ctrl/Cmd + Shift + Z">
                <Button
                  size="small"
                  type="text"
                  className="h-6 w-7 px-0"
                  icon={<Redo2 size={13} />}
                  disabled={!canRedo || busy}
                  onClick={onRedo}
                />
              </Tooltip>
            </div>
            <span className="h-5 w-px bg-[#e5e7eb]" />
            <Button
              size="small"
              disabled={!canAddChart || busy}
              icon={<BarChart3 size={13} />}
              onClick={onAddChart}
            >
              添加图表
            </Button>
          </>
        ) : null}

        {persisted && currentVersionNo ? (
          <Button
            size="small"
            disabled={busy}
            icon={<History size={13} />}
            onClick={onHistory}
          >
            历史版本
          </Button>
        ) : null}

        <Button
          size="small"
          disabled={busy}
          icon={preview ? <X size={13} /> : <Eye size={13} />}
          onClick={onPreview}
        >
          {preview ? '退出预览' : '预览'}
        </Button>

        {!preview ? (
          <>
            <Tooltip title={saveDisabled ? '当前没有需要保存到草稿的修改' : '保存草稿 Ctrl/Cmd + S'}>
              <span>
                <Button
                  size="small"
                  loading={saving}
                  disabled={saveDisabled || publishing}
                  icon={<Save size={13} />}
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
                  loading={publishing}
                  disabled={!canPublish || saving}
                  icon={<Send size={13} />}
                  onClick={onPublish}
                >
                  {hasPublishedVersion ? '发布更新' : '发布'}
                </Button>
              </span>
            </Tooltip>
          </>
        ) : null}
      </div>
    </header>
  );
}
