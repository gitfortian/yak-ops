import { Code2, TerminalSquare } from 'lucide-react';

import type { DevelopmentTaskType } from '../types';
import { ShellEditor, ShellRunConfig, ShellRunResult } from './shell/ShellEditor';
import { SqlEditor, SqlRunConfig, SqlRunResult } from './sql/SqlEditor';
import SqlToolbar from './sql/SqlToolbar';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorDefinition,
} from './types';

const commonCapabilities = {
  run: false,
  stop: false,
  save: true,
  refresh: true,
  publish: false,
  share: true,
  properties: true,
  runConfig: true,
  scheduleConfig: true,
  versions: true,
} as const;

const UnsupportedEditor = ({ node }: DevelopmentEditorContext) => (
  <div className="flex h-full min-h-0 items-center justify-center overflow-auto bg-white">
    <div className="text-center">
      <div className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-[#f5f5f6] text-[#667085]">
        <Code2 size={18} strokeWidth={1.8} />
      </div>
      <div className="mt-3 text-[15px] font-semibold text-[#344054]">
        {node.type} 编辑器区域
      </div>
      <div className="mt-1 text-[12px] text-[#98a2b3]">
        当前节点：{node.name}
      </div>
      <div className="mt-3 text-[12px] text-[#b0b7c3]">
        当前节点类型的编辑器将在后续阶段接入
      </div>
    </div>
  </div>
);

const editorRegistry: Partial<
  Record<DevelopmentTaskType, DevelopmentEditorDefinition>
> = {
  SQL: {
    type: 'SQL',
    label: 'SQL',
    icon: Code2,
    iconClassName: 'text-[#f79009]',
    capabilities: {
      ...commonCapabilities,
      run: true,
      publish: true,
      format: true,
    },
    Editor: SqlEditor,
    Toolbar: SqlToolbar,
    panels: {
      'run-config': SqlRunConfig,
    },
    RunResult: SqlRunResult,
  },
  SHELL: {
    type: 'SHELL',
    label: 'Shell',
    icon: TerminalSquare,
    iconClassName: 'text-[#6172f3]',
    capabilities: commonCapabilities,
    Editor: ShellEditor,
    panels: {
      'run-config': ShellRunConfig,
    },
    RunResult: ShellRunResult,
  },
};

const fallbackLabels: Partial<Record<DevelopmentTaskType, string>> = {
  HTTP: 'HTTP',
  PYTHON: 'Python',
};

export const getEditorDefinition = (
  type: DevelopmentTaskType,
): DevelopmentEditorDefinition => {
  const definition = editorRegistry[type];
  if (definition) return definition;

  return {
    type,
    label: fallbackLabels[type] || type,
    icon: Code2,
    iconClassName: 'text-[#667085]',
    capabilities: commonCapabilities,
    Editor: UnsupportedEditor,
  };
};
