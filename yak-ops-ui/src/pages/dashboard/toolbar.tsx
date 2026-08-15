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
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#e8eaee] bg-white px-4">
      <div className="flex min-w-0 items-center gap-3">
        <Tooltip title="退出编辑器">
          <Button
            type="text"
            className="!flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7] hover:!text-[#344054]"
            icon={<ChevronLeft size={16} />}
            disabled={preview || busy}
            onClick={onBack}
          />
        </Tooltip>
        <div className="h-7 w-px bg-[#eceef1]" />
        <div className="min-w-0">
          <Input
            variant="borderless"
            value={name}
            disabled={preview || busy}
            onChange={(event) => onName(event.target.value)}
            className="!h-6 !w-[280px] !px-0 !text-[14px] !font-semibold !leading-6 !text-[#161823]"
          />
          <div className="mt-0.5 flex h-4 items-center gap-2 text-[10px] leading-4 text-[#98a2b3]">
            <span>{lifecycleText}</span>
            {dirty ? (
              <>
                <span className="h-1 w-1 rounded-full bg-[#c2c6cc]" />
                <span className="text-[#667085]">有未保存修改</span>
              </>
            ) : null}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-1.5">
        {!preview ? (
          <>
            <div className="mr-1 flex items-center rounded-[7px] bg-[#f6f7f8] p-0.5">
              <Tooltip title="撤销 Ctrl/Cmd + Z">
                <Button type="text" className="!h-7 !w-7 !min-w-0 !rounded-[6px] !p-0" icon={<Undo2 size={13} />} disabled={!canUndo || busy} onClick={onUndo} />
              </Tooltip>
              <Tooltip title="重做 Ctrl/Cmd + Shift + Z">
                <Button type="text" className="!h-7 !w-7 !min-w-0 !rounded-[6px] !p-0" icon={<Redo2 size={13} />} disabled={!canRedo || busy} onClick={onRedo} />
              </Tooltip>
            </div>
            <Button size="small" className="!h-8 !rounded-[7px] !border-[#e4e7ec] !px-3" disabled={!canAddChart || busy} icon={<BarChart3 size={13} />} onClick={onAddChart}>
              添加图表
            </Button>
          </>
        ) : null}

        {persisted && currentVersionNo ? (
          <Tooltip title="历史版本">
            <Button type="text" className="!flex !h-8 !w-8 !min-w-0 !items-center !justify-center !rounded-[7px] !p-0 !text-[#667085] hover:!bg-[#f5f6f7]" disabled={busy} icon={<History size={14} />} onClick={onHistory} />
          </Tooltip>
        ) : null}

        <Button size="small" className="!h-8 !rounded-[7px] !border-[#e4e7ec] !px-3" disabled={busy} icon={preview ? <X size={13} /> : <Eye size={13} />} onClick={onPreview}>
          {preview ? '退出预览' : '预览'}
        </Button>

        {!preview ? (
          <>
            <Tooltip title={saveDisabled ? '当前没有需要保存到草稿的修改' : '保存草稿 Ctrl/Cmd + S'}>
              <span>
                <Button size="small" className="!h-8 !rounded-[7px] !border-[#e4e7ec] !px-3" loading={saving} disabled={saveDisabled || publishing} icon={<Save size={13} />} onClick={onSaveDraft}>
                  保存草稿
                </Button>
              </span>
            </Tooltip>
            <Tooltip title={!canPublish ? '当前草稿已经是已发布版本' : undefined}>
              <span>
                <Button size="small" type="primary" className="!h-8 !rounded-[7px] !px-3.5 !shadow-none" loading={publishing} disabled={!canPublish || saving} icon={<Send size={13} />} onClick={onPublish}>
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
