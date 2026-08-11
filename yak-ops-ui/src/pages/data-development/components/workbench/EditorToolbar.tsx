import { Button, message } from 'antd';
import {
  Play,
  RefreshCw,
  Rocket,
  Save,
  Share2,
  Square,
} from 'lucide-react';

import type { DevelopmentEditorDefinition } from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface EditorToolbarProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  onRun: () => void;
}

const placeholder = (label: string) => {
  message.info(`${label}能力将在后续编辑器阶段接入`);
};

const EditorToolbar = ({
  node,
  directory,
  definition,
  onRun,
}: EditorToolbarProps) => {
  const capabilities = definition.capabilities;

  return (
    <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-3">
      <div className="flex items-center gap-1">
        {capabilities.run ? (
          <Button
            type="text"
            size="small"
            icon={<Play size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={onRun}
          >
            运行
          </Button>
        ) : null}
        {capabilities.stop ? (
          <Button
            type="text"
            size="small"
            icon={<Square size={13} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => placeholder('停止')}
          >
            停止
          </Button>
        ) : null}
        {capabilities.save ? (
          <Button
            type="text"
            size="small"
            icon={<Save size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => placeholder('保存')}
          >
            保存
          </Button>
        ) : null}
        {capabilities.refresh ? (
          <Button
            type="text"
            size="small"
            icon={<RefreshCw size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => placeholder('刷新')}
          >
            刷新
          </Button>
        ) : null}
        {capabilities.publish ? (
          <Button
            type="text"
            size="small"
            icon={<Rocket size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => placeholder('发布')}
          >
            发布
          </Button>
        ) : null}
        {capabilities.share ? (
          <Button
            type="text"
            size="small"
            icon={<Share2 size={14} strokeWidth={1.8} />}
            className="!h-8 !px-2.5"
            onClick={() => placeholder('分享')}
          >
            分享
          </Button>
        ) : null}
      </div>

      <div className="truncate pl-4 text-[11px] text-[#98a2b3]">
        {directory?.path || '/'} / {node.name}
      </div>
    </div>
  );
};

export default EditorToolbar;
