import { Button, Input, Select, Tooltip } from 'antd';
import { ChevronLeft, Eye, History, Plus, Save, X } from 'lucide-react';
import type { DashboardSummary, DashboardVersionSummary } from './model';

export function DashboardToolbar({
  name,
  dashboardId,
  currentVersionNo,
  dashboards,
  versions,
  loading,
  saving,
  preview,
  onName,
  onDashboard,
  onNew,
  onVersion,
  onPreview,
  onSave,
}: {
  name: string;
  dashboardId: string;
  currentVersionNo?: number;
  dashboards: DashboardSummary[];
  versions: DashboardVersionSummary[];
  loading: boolean;
  saving: boolean;
  preview: boolean;
  onName: (name: string) => void;
  onDashboard: (dashboardId: string) => void;
  onNew: () => void;
  onVersion: (versionNo: number) => void;
  onPreview: () => void;
  onSave: () => void;
}) {
  const persisted = /^\d+$/.test(dashboardId);
  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#dfe3e8] bg-white px-3">
      <div className="flex min-w-0 items-center gap-2">
        <Button size="small" type="text" icon={<ChevronLeft size={14} />} disabled={preview}>数据分析</Button>
        <span className="h-5 w-px bg-[#e5e7eb]" />
        <Select
          size="small"
          className="w-[190px]"
          loading={loading}
          disabled={preview}
          placeholder="选择仪表盘"
          value={persisted ? dashboardId : undefined}
          options={dashboards.map((item) => ({ label: item.name, value: item.id }))}
          onChange={onDashboard}
        />
        <Tooltip title="新建仪表盘">
          <Button size="small" type="text" icon={<Plus size={13} />} disabled={preview} onClick={onNew} />
        </Tooltip>
        <span className="h-5 w-px bg-[#e5e7eb]" />
        <Input
          variant="borderless"
          value={name}
          disabled={preview}
          onChange={(event) => onName(event.target.value)}
          className="w-[220px] px-1 text-[13px] font-semibold text-[#161823]"
        />
        <span className="rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[10px] text-[#667085]">
          {currentVersionNo ? `V${currentVersionNo}` : '未保存'}
        </span>
      </div>
      <div className="flex items-center gap-2">
        {persisted && versions.length ? (
          <Select
            size="small"
            className="w-[104px]"
            suffixIcon={<History size={12} />}
            disabled={preview || saving}
            value={currentVersionNo}
            options={versions.map((item) => ({ label: `版本 V${item.versionNo}`, value: item.versionNo }))}
            onChange={onVersion}
          />
        ) : null}
        <Button size="small" icon={preview ? <X size={13} /> : <Eye size={13} />} onClick={onPreview}>
          {preview ? '退出预览' : '预览'}
        </Button>
        {!preview ? (
          <Button size="small" type="primary" loading={saving} icon={<Save size={13} />} onClick={onSave}>
            保存
          </Button>
        ) : null}
      </div>
    </header>
  );
}
