import type { LucideIcon } from 'lucide-react';
import type { ComponentType } from 'react';

import type {
  DevelopmentDirectory,
  DevelopmentNode,
  DevelopmentTaskType,
} from '../types';

export type DevelopmentEditorPanelKey =
  | 'properties'
  | 'run-config'
  | 'schedule-config'
  | 'versions';

export interface DevelopmentEditorContext {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
}

export interface DevelopmentEditorToolbarContext
  extends DevelopmentEditorContext {
  onRun: () => void;
  onSave: () => void;
  onPublish: () => void;
  saving: boolean;
  publishing: boolean;
}

export interface DevelopmentEditorCapabilities {
  run: boolean;
  stop: boolean;
  save: boolean;
  refresh: boolean;
  publish: boolean;
  share: boolean;
  format?: boolean;
  properties: boolean;
  runConfig: boolean;
  scheduleConfig: boolean;
  versions: boolean;
}

export interface DevelopmentEditorDefinition {
  type: DevelopmentTaskType;
  label: string;
  icon: LucideIcon;
  iconClassName: string;
  capabilities: DevelopmentEditorCapabilities;
  Editor: ComponentType<DevelopmentEditorContext>;
  Toolbar?: ComponentType<DevelopmentEditorToolbarContext>;
  panels?: Partial<
    Record<DevelopmentEditorPanelKey, ComponentType<DevelopmentEditorContext>>
  >;
  RunResult?: ComponentType<DevelopmentEditorContext>;
}
