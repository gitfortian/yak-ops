import {
  getTaskCatalogAsset,
  type TaskCatalogAsset,
} from '@/services/taskCatalog';
import type { WorkflowCanvasTaskOption } from './types';

export const TASK_ASSET_ID_PREFIX = 'task-asset:';

export const taskCatalogOption = (asset: TaskCatalogAsset): WorkflowCanvasTaskOption => ({
  id: `${TASK_ASSET_ID_PREFIX}${asset.id}`,
  label: asset.name,
  typeLabel: '数据开发',
  taskType: asset.taskType,
  taskAssetId: asset.id,
  taskRevisionId: asset.currentRevision.taskRevisionId,
  taskRevisionNo: asset.currentRevision.revisionNo,
  meta: `已发布 v${asset.currentRevision.revisionNo}`,
});

export const taskAssetIdFromTaskId = (taskId: string) =>
  taskId.startsWith(TASK_ASSET_ID_PREFIX)
    ? taskId.slice(TASK_ASSET_ID_PREFIX.length).trim()
    : undefined;

export const resolveWorkflowTaskOption = async (
  taskId: string,
  knownOptions: WorkflowCanvasTaskOption[],
): Promise<WorkflowCanvasTaskOption | undefined> => {
  const known = knownOptions.find((option) => option.id === taskId);
  if (known) return known;
  const assetId = taskAssetIdFromTaskId(taskId);
  if (!assetId) return undefined;
  return taskCatalogOption(await getTaskCatalogAsset(assetId));
};
