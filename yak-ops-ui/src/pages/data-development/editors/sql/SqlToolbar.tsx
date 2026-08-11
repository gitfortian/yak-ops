import { Dropdown, Tooltip, message } from 'antd';
import {
  Play,
  Redo2,
  Save,
  Search,
  Settings,
  Sparkles,
  Undo2,
  Wand2,
} from 'lucide-react';
import type { ReactNode } from 'react';

import { markEditorSessionSaved } from '../session/editorSessionStore';
import type { DevelopmentEditorToolbarContext } from '../types';
import {
  executeSqlEditorCommand,
  type SqlEditorCommand,
} from './commands/sqlEditorCommandBus';
import SqlMetadataContextToolbar from './metadata/SqlMetadataContextToolbar';

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)]';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const SqlToolbar = ({ node, onRun }: DevelopmentEditorToolbarContext) => {
  const execute = (command: SqlEditorCommand, fallback: string) => {
    if (!executeSqlEditorCommand(node.id, command)) {
      message.info(fallback);
    }
  };

  return (
    <div className="flex h-full w-full min-w-0 items-center justify-between gap-3">
      <div className="flex shrink-0 items-center gap-0.5">
        <ToolbarButton title="运行" onClick={onRun}>
          <Play size={15} strokeWidth={1.8} />
        </ToolbarButton>

        <ToolbarDivider />

        <ToolbarButton
          title="保存本地草稿"
          onClick={() => {
            markEditorSessionSaved(node.id);
            message.success('本地草稿已保存');
          }}
        >
          <Save size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton
          title="撤销"
          onClick={() => execute('undo', 'SQL 编辑器尚未就绪')}
        >
          <Undo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton
          title="重做"
          onClick={() => execute('redo', 'SQL 编辑器尚未就绪')}
        >
          <Redo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton
          title="查找"
          onClick={() => execute('find', 'SQL 编辑器尚未就绪')}
        >
          <Search size={15} strokeWidth={1.8} />
        </ToolbarButton>

        <ToolbarDivider />

        <ToolbarButton
          title="格式化 SQL"
          onClick={() => execute('format', 'SQL 编辑器尚未就绪')}
        >
          <Wand2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton
          title="触发智能提示"
          onClick={() => execute('suggest', 'SQL 编辑器尚未就绪')}
        >
          <Sparkles size={15} strokeWidth={1.8} />
        </ToolbarButton>

        <Dropdown
          trigger={['click']}
          placement="bottomLeft"
          menu={{
            items: [
              { key: 'toggle-word-wrap', label: '切换自动换行' },
              { key: 'toggle-minimap', label: '切换缩略图' },
            ],
            onClick: ({ key }) =>
              execute(key as SqlEditorCommand, 'SQL 编辑器尚未就绪'),
          }}
        >
          <Tooltip title="编辑器设置" mouseEnterDelay={0.35}>
            <button
              type="button"
              aria-label="编辑器设置"
              className={iconButtonClassName}
            >
              <Settings size={15} strokeWidth={1.8} />
            </button>
          </Tooltip>
        </Dropdown>
      </div>

      <SqlMetadataContextToolbar nodeId={node.id} />
    </div>
  );
};

export default SqlToolbar;
