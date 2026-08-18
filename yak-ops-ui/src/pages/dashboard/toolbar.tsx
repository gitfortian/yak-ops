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

  const lifecycleText = (() => {
    if (!persisted || !currentVersionNo) return '未保存';
    if (hasPublishedVersion && !hasUnpublishedDraft) return `已发布 V${publishedVersionNo}`;
    return hasPublishedVersion
      ? `草稿 V${currentVersionNo} · 已发布 V${publishedVersionNo}`
      : `草稿 V${currentVersionNo} · 未发布`;
  })();

  return (
    <header className="shrink-0 border-b border-[#e4e7ec] bg-white">
      <div className="flex h-10 items-center justify-between px-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <Tooltip title="退出编辑器">
            <Button
              type="text"
              className="!flex !h-7 !w-7 !min-w-0 !items-center !justify-center !rounded-[6px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
              icon={<ChevronLeft size={15} />}
              disabled={preview || busy}
              onClick={onBack}
            />
          </Tooltip>
          <div className="h-5 w-px bg-[#eceef1]" />
          <Input
            variant="borderless"
            value={name}
            disabled={preview || busy}
            onChange={(event) => onName(event.target.value)}
            className="!h-6 !w-[250px] !px-0 !text-[13px] !font-semibold !leading-6 !text-[#161823]"
          />
          <div className="hidden items-center gap-2 whitespace-nowrap text-[10px] text-[#8b929c] lg:flex">
            <span>{lifecycleText}</span>
            {dirty ? (
              <>
                <span className="h-1 w-1 rounded-full bg-[#c2c6cc]" />
                <span className="text-[#667085]">有未保存修改</span>
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
                  className="!h-7 !rounded-[6px] !border-[#e4e7ec] !px-2.5 !text-[11px]"
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

      <div className="flex h-8 items-center justify-between border-t border-[#f0f1f3] bg-[#fbfcfd] px-3">
        <div className="flex items-center gap-1">
          {!preview ? (
            <>
              <Button
                type="text"
                size="small"
                className="!h-7 !rounded-[5px] !px-2 !text-[11px] !text-[#344054] hover:!bg-[#f0f2f5]"
                disabled={!canAddChart || busy}
                icon={<BarChart3 size={12} />}
                onClick={onAddChart}
              >
                添加图表
              </Button>
              <div className="mx-1 h-4 w-px bg-[#e4e7ec]" />
              <Tooltip title="撤销 Ctrl/Cmd + Z">
                <Button
                  type="text"
                  className="!h-7 !w-7 !min-w-0 !rounded-[5px] !p-0 !text-[#667085] hover:!bg-[#f0f2f5] hover:!text-[#344054]"
                  icon={<Undo2 size={12} />}
                  disabled={!canUndo || busy}
                  onClick={onUndo}
                />
              </Tooltip>
              <Tooltip title="重做 Ctrl/Cmd + Shift + Z">
                <Button
                  type="text"
                  className="!h-7 !w-7 !min-w-0 !rounded-[5px] !p-0 !text-[#667085] hover:!bg-[#f0f2f5] hover:!text-[#344054]"
                  icon={<Redo2 size={12} />}
                  disabled={!canRedo || busy}
                  onClick={onRedo}
                />
              </Tooltip>
            </>
          ) : (
            <span className="text-[11px] font-medium text-[#667085]">预览模式</span>
          )}
        </div>

        <div className="flex items-center gap-1">
          {persisted && currentVersionNo && !preview ? (
            <Tooltip title="历史版本">
              <Button
                type="text"
                className="!flex !h-7 !w-7 !min-w-0 !items-center !justify-center !rounded-[5px] !p-0 !text-[#667085] hover:!bg-[#f0f2f5] hover:!text-[#344054]"
                disabled={busy}
                icon={<History size={12} />}
                onClick={onHistory}
              />
            </Tooltip>
          ) : null}
          <Button
            type="text"
            size="small"
            className="!h-7 !rounded-[5px] !px-2 !text-[11px] !text-[#344054] hover:!bg-[#f0f2f5]"
            disabled={busy}
            icon={preview ? <X size={12} /> : <Eye size={12} />}
            onClick={onPreview}
          >
            {preview ? '退出预览' : '预览'}
          </Button>
        </div>
      </div>
    </header>
  );
}
