import { Tooltip, message } from 'antd';
import {
  Database,
  LoaderCircle,
  Play,
  Redo2,
  Rocket,
  Save,
  Search,
  Sparkles,
  Undo2,
  Wand2,
} from 'lucide-react';
import type { ReactNode } from 'react';

import type { DevelopmentEditorToolbarContext } from '../types';
import {
  executeSqlEditorCommand,
  type SqlEditorCommand,
} from './commands/sqlEditorCommandBus';
import SqlMetadataContextToolbar from './metadata/SqlMetadataContextToolbar';

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, disabled, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const datasetTooltip = (
  state: DevelopmentEditorToolbarContext['datasetState'],
  loading?: boolean,
) => {
  if (loading) return '正在读取 Dataset 状态';
  if (!state) return '请先发布 SQL 版本，再发布为 Dataset';
  if (!state.datasetId || !state.datasetVersionNo) return `发布 SQL v${state.releaseRevisionNo} 为 Dataset`;
  if (state.datasetSourceRevisionNo !== state.releaseRevisionNo) {
    return `更新 Dataset · DV${state.datasetVersionNo} → SQL v${state.releaseRevisionNo}`;
  }
  const status = state.datasetStatus === 'OFFLINE' ? ' · 已下线' : '';
  return `Dataset DV${state.datasetVersionNo} 已同步 SQL v${state.releaseRevisionNo}${status}`;
};

const SqlToolbar = ({
  node,
  onRun,
  onSave,
  onPublish,
  onPublishDataset,
  datasetState,
  datasetLoading,
  datasetPublishing,
  running,
  saving,
  publishing,
}: DevelopmentEditorToolbarContext) => {
  const execute = (command: SqlEditorCommand, fallback: string) => {
    if (!executeSqlEditorCommand(node.id, command)) {
      message.info(fallback);
    }
  };
  const datasetNeedsUpdate = Boolean(
    datasetState?.datasetId
      && datasetState.datasetSourceRevisionNo !== datasetState.releaseRevisionNo,
  );

  return (
    <div className="flex h-full w-full min-w-0 items-center justify-between gap-3">
      <div className="flex shrink-0 items-center gap-0.5">
        <ToolbarButton title={running ? 'SQL 运行中' : '运行当前 SQL'} disabled={running} onClick={onRun}>
          {running ? <LoaderCircle size={15} className="animate-spin" /> : <Play size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        <ToolbarDivider />
        <ToolbarButton title="保存草稿" disabled={saving || publishing || running} onClick={onSave}>
          {saving ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        <ToolbarButton title="发布版本" disabled={saving || publishing || running} onClick={onPublish}>
          {publishing ? <LoaderCircle size={15} className="animate-spin" /> : <Rocket size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        <ToolbarButton
          title={datasetTooltip(datasetState, datasetLoading)}
          disabled={!onPublishDataset || !datasetState || datasetLoading || datasetPublishing || saving || publishing || running}
          onClick={() => onPublishDataset?.()}
        >
          {datasetLoading || datasetPublishing ? (
            <LoaderCircle size={15} className="animate-spin" />
          ) : (
            <Database
              size={15}
              strokeWidth={1.8}
              className={datasetNeedsUpdate ? 'text-[var(--yak-brand-color)]' : undefined}
            />
          )}
        </ToolbarButton>
        <ToolbarDivider />
        <ToolbarButton title="撤销" disabled={running} onClick={() => execute('undo', 'SQL 编辑器尚未就绪')}>
          <Undo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton title="重做" disabled={running} onClick={() => execute('redo', 'SQL 编辑器尚未就绪')}>
          <Redo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton title="查找" onClick={() => execute('find', 'SQL 编辑器尚未就绪')}>
          <Search size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarDivider />
        <ToolbarButton title="格式化 SQL" disabled={running} onClick={() => execute('format', 'SQL 编辑器尚未就绪')}>
          <Wand2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton title="触发智能提示" onClick={() => execute('suggest', 'SQL 编辑器尚未就绪')}>
          <Sparkles size={15} strokeWidth={1.8} />
        </ToolbarButton>
      </div>
      <SqlMetadataContextToolbar nodeId={node.id} />
    </div>
  );
};

export default SqlToolbar;
