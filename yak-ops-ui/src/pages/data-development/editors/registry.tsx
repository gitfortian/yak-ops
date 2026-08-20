import { Braces, Code2, DatabaseZap, Network, TerminalSquare } from 'lucide-react';

import PythonIcon from '../icon/PythonIcon';

import type { DevelopmentNodeType, DevelopmentTaskType } from '../types';
import SqlLineagePanel from '../components/workbench/SqlLineagePanel';
import { PythonEditor, PythonRunConfig, PythonRunResult } from './python/PythonEditor';
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
      <div className="mt-3 text-[15px] font-semibold text-[#344054]">{node.type} 编辑器区域</div>
      <div className="mt-1 text-[12px] text-[#98a2b3]">当前节点：{node.name}</div>
    </div>
  </div>
);

const editorRegistry: Partial<Record<DevelopmentTaskType, DevelopmentEditorDefinition>> = {
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
      lineage: true,
    },
    Editor: SqlEditor,
    Toolbar: SqlToolbar,
    panels: {
      'run-config': SqlRunConfig,
      lineage: SqlLineagePanel,
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
  PYTHON: {
    type: 'PYTHON',
    label: 'Python',
    icon: PythonIcon,
    iconClassName: '',
    capabilities: {
      ...commonCapabilities,
      run: true,
      publish: true,
    },
    Editor: PythonEditor,
    panels: {
      'run-config': PythonRunConfig,
    },
    RunResult: PythonRunResult,
  },
};

const fallbackLabels: Partial<Record<DevelopmentTaskType, string>> = {
  HTTP: 'HTTP',
};

export const getEditorDefinition = (type: DevelopmentTaskType): DevelopmentEditorDefinition => {
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

export const getEditorAppearance = (type: DevelopmentNodeType) => {
  if (type === 'DATA_SERVICE') {
    return { label: 'Data Service', icon: Network, iconClassName: 'text-[#7f56d9]' };
  }
  if (type === 'DATASET') {
    return { label: 'Dataset', icon: DatabaseZap, iconClassName: 'text-[#12b76a]' };
  }
  if (type === 'HTTP') {
    return { label: 'HTTP', icon: Braces, iconClassName: 'text-[#2e90fa]' };
  }
  if (type === 'PYTHON') {
    return { label: 'Python', icon: PythonIcon, iconClassName: '' };
  }
  const definition = getEditorDefinition(type);
  return { label: definition.label, icon: definition.icon, iconClassName: definition.iconClassName };
};
