import { Button, Input, Tooltip } from 'antd';
import { ChevronLeft, Eye, RefreshCw, Save, X } from 'lucide-react';

export function DashboardToolbar({
  name,
  preview,
  onName,
  onReset,
  onPreview,
  onSave,
}: {
  name: string;
  preview: boolean;
  onName: (name: string) => void;
  onReset: () => void;
  onPreview: () => void;
  onSave: () => void;
}) {
  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#dfe3e8] bg-white px-3">
      <div className="flex min-w-0 items-center gap-2">
        <Button size="small" type="text" icon={<ChevronLeft size={14} />} disabled={preview}>数据分析</Button>
        <span className="h-5 w-px bg-[#e5e7eb]" />
        <Input variant="borderless" value={name} disabled={preview} onChange={(event) => onName(event.target.value)} className="w-[220px] px-1 text-[13px] font-semibold text-[#161823]" />
        <span className="hidden rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[10px] text-[#667085] xl:inline">草稿</span>
      </div>
      <div className="flex items-center gap-2">
        {!preview ? <span className="hidden text-[10px] text-[#98a2b3] xl:inline">数据开发发布数据集 · 24 栅格</span> : null}
        {!preview ? <Tooltip title="恢复示例"><Button size="small" type="text" icon={<RefreshCw size={13} />} onClick={onReset} /></Tooltip> : null}
        <Button size="small" icon={preview ? <X size={13} /> : <Eye size={13} />} onClick={onPreview}>{preview ? '退出预览' : '预览'}</Button>
        {!preview ? <Button size="small" type="primary" icon={<Save size={13} />} onClick={onSave}>保存</Button> : null}
      </div>
    </header>
  );
}
