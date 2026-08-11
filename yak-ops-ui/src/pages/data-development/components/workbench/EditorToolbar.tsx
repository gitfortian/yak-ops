import { Tooltip, message } from 'antd';
import { Play, Save } from 'lucide-react';

import { markEditorSessionSaved } from '../../editors/session/editorSessionStore';
import type { DevelopmentEditorDefinition } from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface EditorToolbarProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  onRun: () => void;
}

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)]';

const EditorToolbar = ({
  node,
  directory,
  definition,
  onRun,
}: EditorToolbarProps) => {
  const Toolbar = definition.Toolbar;
  const capabilities = definition.capabilities;

  return (
    <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2">
      {Toolbar ? (
        <div className="flex h-full min-w-0 flex-1 items-center">
          <Toolbar node={node} directory={directory} onRun={onRun} />
        </div>
      ) : (
        <>
          <div className="flex h-full min-w-0 items-center">
            <div className="flex h-full items-center gap-0.5">
              {capabilities.run ? (
                <Tooltip title="运行" mouseEnterDelay={0.35}>
                  <button
                    type="button"
                    aria-label="运行"
                    onClick={onRun}
                    className={iconButtonClassName}
                  >
                    <Play size={15} strokeWidth={1.8} />
                  </button>
                </Tooltip>
              ) : null}
              {capabilities.save ? (
                <Tooltip title="保存本地草稿" mouseEnterDelay={0.35}>
                  <button
                    type="button"
                    aria-label="保存本地草稿"
                    onClick={() => {
                      markEditorSessionSaved(node.id);
                      message.success('本地草稿已保存');
                    }}
                    className={iconButtonClassName}
                  >
                    <Save size={15} strokeWidth={1.8} />
                  </button>
                </Tooltip>
              ) : null}
            </div>
          </div>

          <div className="min-w-0 truncate pl-4 text-[11px] text-[#98a2b3]">
            {directory?.path || '/'} / {node.name}
          </div>
        </>
      )}
    </div>
  );
};

export default EditorToolbar;
