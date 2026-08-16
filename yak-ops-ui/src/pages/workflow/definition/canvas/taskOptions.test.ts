import type { TaskCatalogAsset } from '@/services/taskCatalog';
import {
  isWorkflowEligibleTaskCatalogAsset,
  taskCatalogOption,
} from './taskOptions';

const asset = (taskType: string): TaskCatalogAsset => ({
  id: '12',
  source: 'DATA_DEVELOPMENT',
  sourceRef: '10001',
  projectId: '7',
  name: '测试资产',
  taskType,
  status: 'ONLINE',
  currentRevision: {
    taskAssetId: '12',
    taskRevisionId: '101',
    revisionNo: 1,
  },
});

describe('workflow task catalog options', () => {
  test('allows executable data development tasks', () => {
    expect(isWorkflowEligibleTaskCatalogAsset(asset('SQL'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('PYTHON'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('SHELL'))).toBe(true);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('HTTP'))).toBe(true);
  });

  test('rejects output resources from workflow orchestration', () => {
    expect(isWorkflowEligibleTaskCatalogAsset(asset('DATASET'))).toBe(false);
    expect(isWorkflowEligibleTaskCatalogAsset(asset('DATA_SERVICE'))).toBe(false);
    expect(() => taskCatalogOption(asset('DATASET'))).toThrow('不能进入工作流编排');
    expect(() => taskCatalogOption(asset('DATA_SERVICE'))).toThrow('不能进入工作流编排');
  });
});
