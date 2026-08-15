import { Button, Input, Select, Tooltip } from 'antd';
import {
  BarChart3,
  ChevronLeft,
  Eye,
  History,
  Redo2,
  Save,
  Undo2,
  X,
} from 'lucide-react';
import type { DashboardVersionSummary } from './model';

export function DashboardToolbar({
  name,
  dashboardId,
  currentVersionNo,
  versions,
  saving,
  preview,
  dirty,
  canUndo,
  canRedo,
  canAddChart,
  onBack,
  onName,
  onUndo,
  onRedo,
  onAddChart,
  onVersion,
  onPreview,
  onSave,
}: {
  name: string;
  dashboardId: string;
  currentVersionNo?: number;
  versions: DashboardVersionSummary[];
  saving: boolean;
  preview: boolean;
  dirty: boolean;
  canUndo: boolean;
  canRedo: boolean;
  canAddChart: boolean;
  onBack: () => void;
  onName: (name: string) => void;
  onUndo: () => void;
  onRedo: () => void;
  onAddChart: () => void;
  onVersion: (versionNo: number) => void;
  onPreview: () => void;
  onSave: () => void;
}) {
  const persisted = /^\d+$/.test(dashboardId);
  const saveDisabled = persisted && !dirty;

  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#dfe3e8] bg-white px-3">
      <div className="flex min-w-0 items-center gap-2">
        <Button
          size="small"
          type="text"
          icon={<ChevronLeft size={14} />}
          disabled={preview}
          onClick={onBack}
        >
          仪表盘
        </Button>
        <span className="h-5 w-px bg-[#e5e7eb]" />
        <Input
          variant="borderless"
          value={name}
          disabled={preview}
          onChange={(event) => onName(event.target.value)}
          className="w-[260px] px-1 text-[13px] font-semibold text-[#161823]"
        />
        <span className="rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[10px] text-[#667085]">
          {currentVersionNo ? `V${currentVersionNo}` : '未保存'}
        </span>
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
                  disabled={!canUndo || saving}
                  onClick={onUndo}
                />
              </Tooltip>
              <Tooltip title="重做 Ctrl/Cmd + Shift + Z">
                <Button
                  size="small"
                  type="text"
                  className="h-6 w-7 px-0"
                  icon={<Redo2 size={13} />}
                  disabled={!canRedo || saving}
                  onClick={onRedo}
                />
              </Tooltip>
            </div>
            <span className="h-5 w-px bg-[#e5e7eb]" />
            <Button
              size="small"
              disabled={!canAddChart}
              icon={<BarChart3 size={13} />}
              onClick={onAddChart}
            >
              添加图表
            </Button>
          </>
        ) : null}
        {persisted && versions.length ? (
          <Select
            size="small"
            className="w-[104px]"
            suffixIcon={<History size={12} />}
            disabled={preview || saving}
            value={currentVersionNo}
            options={versions.map((item) => ({
              label: `版本 V${item.versionNo}`,
              value: item.versionNo,
            }))}
            onChange={onVersion}
          />
        ) : null}
        <Button
          size="small"
          icon={preview ? <X size={13} /> : <Eye size={13} />}
          onClick={onPreview}
        >
          {preview ? '退出预览' : '预览'}
        </Button>
        {!preview ? (
          <Tooltip title={saveDisabled ? '当前没有需要保存的修改' : '保存 Ctrl/Cmd + S'}>
            <span>
              <Button
                size="small"
                type="primary"
                loading={saving}
                disabled={saveDisabled}
                icon={<Save size={13} />}
                onClick={onSave}
              >
                保存
              </Button>
            </span>
          </Tooltip>
        ) : null}
      </div>
    </header>
  );
}
