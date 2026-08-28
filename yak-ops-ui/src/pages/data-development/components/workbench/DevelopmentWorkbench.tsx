import { message } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { getEditorDefinition } from '../../editors/registry';
import {
  getEditorSession,
  markEditorSessionSaved,
} from '../../editors/session/editorSessionStore';
import {
  hydrateDevelopmentTaskDraft,
  prepareDevelopmentTaskDefinition,
  restoreDevelopmentTaskOriginal,
} from '../../editors/taskPersistence';
import {
  executionDetailToRunResult,
  executionSubmissionToRunResult,
  isDevelopmentExecutionActive,
  isDevelopmentExecutionRetryable,
} from '../../executions/execution-state';
import { isDevelopmentTaskNode } from '../../node-model';
import {
  cancelDevelopmentTaskExecution,
  getActiveDevelopmentTaskExecution,
  getDevelopmentTaskDraft,
  getDevelopmentTaskExecution,
  previewDevelopmentSqlLineage,
  publishDevelopmentTask,
  retryDevelopmentTaskExecution,
  runDevelopmentTask,
  saveDevelopmentTaskDraft,
} from '../../service';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentResourceNode,
  DevelopmentSqlLineagePreview,
  DevelopmentTaskDraft,
  DevelopmentTaskRunResult,
} from '../../types';
import EditorHost from './EditorHost';
import EditorTabs, { type EditorTabAction } from './EditorTabs';
import EditorToolbar from './EditorToolbar';
import RightPanel from './RightPanel';
import RunResultPanel, {
  type WorkbenchBottomPanelView,
} from './RunResultPanel';
import {
  DataServiceWorkbenchEditor,
  DatasetWorkbenchEditor,
} from './StandaloneResourceEditors';
import UnsavedChangesModal from './UnsavedChangesModal';
import { responseData } from './workbenchResponse';
import { closeTabs, tabActionTargets } from './workbenchTabs';

interface DevelopmentWorkbenchProps {
  nodes: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeFocus: (nodeId?: DevelopmentId) => void;
  onNodesChanged?: () => void | Promise<void>;
}

interface PendingCloseRequest {
  nodeIds: DevelopmentId[];
  dirtyNodeIds: DevelopmentId[];
}

const DevelopmentWorkbench = ({
  nodes,
  directories,
  selectedNodeId,
  onNodeFocus,
  onNodesChanged,
}: DevelopmentWorkbenchProps) => {
  const [openNodeIds, setOpenNodeIds] = useState<DevelopmentId[]>([]);
  const [activeNodeId, setActiveNodeId] = useState<DevelopmentId>();
  const [runPanelOpen, setRunPanelOpen] = useState(false);
  const [bottomPanelView, setBottomPanelView] =
    useState<WorkbenchBottomPanelView>('result');
  const [runResults, setRunResults] = useState<
    Partial<Record<DevelopmentId, DevelopmentTaskRunResult>>
  >({});
  const [executionIds, setExecutionIds] = useState<
    Partial<Record<DevelopmentId, DevelopmentId>>
  >({});
  const [executionActionNodeIds, setExecutionActionNodeIds] = useState<DevelopmentId[]>([]);
  const [lineagePreviews, setLineagePreviews] = useState<
    Partial<Record<DevelopmentId, DevelopmentSqlLineagePreview>>
  >({});
  const [lineageLoadingNodeIds, setLineageLoadingNodeIds] = useState<DevelopmentId[]>([]);
  const [pendingClose, setPendingClose] = useState<PendingCloseRequest>();
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [closeSaving, setCloseSaving] = useState(false);
  const [versionsRefreshKey, setVersionsRefreshKey] = useState(0);
  const [resourceDirtyNodeIds, setResourceDirtyNodeIds] = useState<DevelopmentId[]>([]);

  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );
  const directoryMap = useMemo(
    () => new Map(directories.map((directory) => [directory.id, directory])),
    [directories],
  );

  useEffect(() => {
    if (!selectedNodeId || !nodeMap.has(selectedNodeId)) return;
    setOpenNodeIds((current) =>
      current.includes(selectedNodeId) ? current : [...current, selectedNodeId],
    );
    setActiveNodeId(selectedNodeId);
  }, [nodeMap, selectedNodeId]);

  useEffect(() => {
    setOpenNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
    setActiveNodeId((current) =>
      current && nodeMap.has(current) ? current : undefined,
    );
    setResourceDirtyNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
    setLineageLoadingNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
    setExecutionActionNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
    setExecutionIds((current) => Object.fromEntries(
      Object.entries(current).filter(([nodeId]) => nodeMap.has(nodeId)),
    ));
  }, [nodeMap]);

  useEffect(() => {
    if (!activeNodeId) return;
    const node = nodeMap.get(activeNodeId);
    if (!node || !isDevelopmentTaskNode(node)) return;

    let active = true;
    getDevelopmentTaskDraft(node.id)
      .then((response) => {
        if (!active) return;
        const draft = responseData(response, '加载任务草稿失败');
        hydrateDevelopmentTaskDraft(node, draft);
      })
      .catch((error) => {
        if (!active) return;
        message.error(error instanceof Error ? error.message : '加载任务草稿失败');
      });

    return () => {
      active = false;
    };
  }, [activeNodeId, nodeMap]);

  useEffect(() => {
    if (!activeNodeId || executionIds[activeNodeId]) return;
    const node = nodeMap.get(activeNodeId);
    if (!node || !isDevelopmentTaskNode(node)) return;

    let active = true;
    getActiveDevelopmentTaskExecution(node.id)
      .then((response) => {
        if (!active) return;
        const execution = responseData(response, '读取当前运行实例失败');
        if (!execution) return;
        setRunResults((current) => ({
          ...current,
          [node.id]: executionDetailToRunResult(execution),
        }));
        if (isDevelopmentExecutionActive(execution.status)) {
          setExecutionIds((current) => ({ ...current, [node.id]: execution.id }));
          setRunPanelOpen(true);
          setBottomPanelView('result');
        }
      })
      .catch(() => {
        // Reattach is best-effort; normal draft/editor loading must remain usable.
      });

    return () => {
      active = false;
    };
  }, [activeNodeId, executionIds, nodeMap]);

  useEffect(() => {
    const tracked = Object.entries(executionIds).filter(
      (entry): entry is [DevelopmentId, DevelopmentId] => Boolean(entry[1]),
    );
    if (!tracked.length) return;

    let disposed = false;
    const refresh = async () => {
      await Promise.all(tracked.map(async ([nodeId, executionId]) => {
        try {
          const detail = responseData(
            await getDevelopmentTaskExecution(executionId),
            '刷新运行状态失败',
          );
          if (disposed) return;
          setRunResults((current) => ({
            ...current,
            [nodeId]: executionDetailToRunResult(detail),
          }));
          if (!isDevelopmentExecutionActive(detail.status)) {
            setExecutionIds((current) => {
              if (current[nodeId] !== executionId) return current;
              const next = { ...current };
              delete next[nodeId];
              return next;
            });
            if (detail.status === 'FAILED' || detail.status === 'TIMEOUT') {
              message.error(detail.errorMessage || '任务执行失败');
            }
          }
        } catch {
          // The durable execution page remains the source of truth; retry on the next tick.
        }
      }));
    };

    void refresh();
    const timer = window.setInterval(() => void refresh(), 1200);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [executionIds]);

  const activeResource = activeNodeId ? nodeMap.get(activeNodeId) : undefined;
  const activeTaskNode = activeResource && isDevelopmentTaskNode(activeResource)
    ? activeResource
    : undefined;
  const activeDirectory = activeResource?.directoryId
    ? directoryMap.get(activeResource.directoryId)
    : undefined;
  const activeRunResult = activeTaskNode ? runResults[activeTaskNode.id] : undefined;
  const activeRunning = isDevelopmentExecutionActive(activeRunResult?.status);
  const activeExecutionActionLoading = activeTaskNode
    ? executionActionNodeIds.includes(activeTaskNode.id)
    : false;
  const activeLineageLoading = activeTaskNode
    ? lineageLoadingNodeIds.includes(activeTaskNode.id)
    : false;
  const openDataServiceNodes = useMemo(
    () => openNodeIds
      .map((nodeId) => nodeMap.get(nodeId))
      .filter((node): node is DevelopmentResourceNode => Boolean(node && node.type === 'DATA_SERVICE')),
    [nodeMap, openNodeIds],
  );
  const openDatasetNodes = useMemo(
    () => openNodeIds
      .map((nodeId) => nodeMap.get(nodeId))
      .filter((node): node is DevelopmentResourceNode => Boolean(node && node.type === 'DATASET')),
    [nodeMap, openNodeIds],
  );

  const focusNode = (nodeId: DevelopmentId) => {
    const target = nodeMap.get(nodeId);
    if (!target) return;
    setOpenNodeIds((current) => current.includes(nodeId) ? current : [...current, nodeId]);
    setActiveNodeId(nodeId);
    if (!isDevelopmentTaskNode(target)) setRunPanelOpen(false);
    else if (target.type !== 'SQL') setBottomPanelView('result');
    onNodeFocus(nodeId);
  };

  const updateResourceDirty = (nodeId: DevelopmentId, dirty: boolean) => {
    setResourceDirtyNodeIds((current) => {
      if (dirty) return current.includes(nodeId) ? current : [...current, nodeId];
      return current.filter((id) => id !== nodeId);
    });
  };

  const persistDraft = async (nodeId: DevelopmentId): Promise<DevelopmentTaskDraft> => {
    const resource = nodeMap.get(nodeId);
    if (!resource || !isDevelopmentTaskNode(resource)) {
      throw new Error(`当前节点不是可执行任务：${nodeId}`);
    }

    const definition = prepareDevelopmentTaskDefinition(resource);
    const session = getEditorSession(nodeId);
    const draft = responseData(
      await saveDevelopmentTaskDraft(nodeId, {
        ...definition,
        baseRevision: session?.draftRevision || 0,
      }),
      '保存草稿失败',
    );

    markEditorSessionSaved(nodeId, draft.draftRevision);
    await onNodesChanged?.();
    return draft;
  };

  const saveActiveDraft = async () => {
    if (!activeTaskNode) return;
    setSaving(true);
    try {
      const draft = await persistDraft(activeTaskNode.id);
      message.success(`草稿已保存 · Draft #${draft.draftRevision}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存草稿失败');
    } finally {
      setSaving(false);
    }
  };

  const runActiveTask = async (contentOverride?: string) => {
    if (!activeTaskNode || activeRunning) return;
    const node = activeTaskNode;
    const startedAt = Date.now();
    setRunPanelOpen(true);
    setBottomPanelView('result');
    setRunResults((current) => ({
      ...current,
      [node.id]: {
        status: 'PENDING',
        message: '',
        durationMs: 0,
        output: {},
      },
    }));

    try {
      const definition = prepareDevelopmentTaskDefinition(node);
      const runDefinition =
        contentOverride === undefined
          ? definition
          : { ...definition, content: contentOverride };
      const submission = responseData(
        await runDevelopmentTask(node.id, runDefinition),
        '提交任务失败',
      );
      setRunResults((current) => ({
        ...current,
        [node.id]: executionSubmissionToRunResult(submission),
      }));
      if (isDevelopmentExecutionActive(submission.status)) {
        setExecutionIds((current) => ({ ...current, [node.id]: submission.id }));
        return;
      }

      const detail = responseData(
        await getDevelopmentTaskExecution(submission.id),
        '读取运行结果失败',
      );
      setRunResults((current) => ({
        ...current,
        [node.id]: executionDetailToRunResult(detail),
      }));
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '运行任务失败';
      setRunResults((current) => ({
        ...current,
        [node.id]: {
          status: 'FAILED',
          message: errorMessage,
          durationMs: Math.max(0, Date.now() - startedAt),
          output: {},
        },
      }));
      message.error(errorMessage);
    }
  };

  const cancelActiveExecution = async () => {
    if (!activeTaskNode || !activeRunResult?.executionId || !activeRunning) return;
    const nodeId = activeTaskNode.id;
    setExecutionActionNodeIds((current) =>
      current.includes(nodeId) ? current : [...current, nodeId],
    );
    try {
      const detail = responseData(
        await cancelDevelopmentTaskExecution(activeRunResult.executionId),
        '取消任务失败',
      );
      setRunResults((current) => ({
        ...current,
        [nodeId]: executionDetailToRunResult(detail),
      }));
      if (!isDevelopmentExecutionActive(detail.status)) {
        setExecutionIds((current) => {
          const next = { ...current };
          delete next[nodeId];
          return next;
        });
      }
      message.success(detail.status === 'CANCELLED' ? '任务已取消' : '已提交取消请求');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消任务失败');
    } finally {
      setExecutionActionNodeIds((current) => current.filter((id) => id !== nodeId));
    }
  };

  const retryActiveExecution = async () => {
    if (
      !activeTaskNode
      || !activeRunResult?.executionId
      || !isDevelopmentExecutionRetryable(activeRunResult.status)
    ) return;
    const nodeId = activeTaskNode.id;
    setExecutionActionNodeIds((current) =>
      current.includes(nodeId) ? current : [...current, nodeId],
    );
    try {
      const submission = responseData(
        await retryDevelopmentTaskExecution(activeRunResult.executionId),
        '重试任务失败',
      );
      setRunResults((current) => ({
        ...current,
        [nodeId]: executionSubmissionToRunResult(submission),
      }));
      if (isDevelopmentExecutionActive(submission.status)) {
        setExecutionIds((current) => ({ ...current, [nodeId]: submission.id }));
      }
      message.success('任务已重新提交');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重试任务失败');
    } finally {
      setExecutionActionNodeIds((current) => current.filter((id) => id !== nodeId));
    }
  };

  const previewActiveLineage = async () => {
    if (!activeTaskNode || activeTaskNode.type !== 'SQL' || activeLineageLoading) return;
    const node = activeTaskNode;
    setRunPanelOpen(true);
    setBottomPanelView('lineage');
    setLineageLoadingNodeIds((current) =>
      current.includes(node.id) ? current : [...current, node.id],
    );

    try {
      const definition = prepareDevelopmentTaskDefinition(node);
      const preview = responseData(
        await previewDevelopmentSqlLineage(node.id, definition),
        '血缘解析失败',
      );
      setLineagePreviews((current) => ({ ...current, [node.id]: preview }));
      if (preview.status === 'FAILED') {
        message.error(preview.parseError || '当前 SQL 血缘解析失败');
      } else if (preview.status === 'PARTIAL' || preview.status === 'UNRESOLVED') {
        message.warning('血缘解析完成，部分字段暂未能解析');
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '血缘解析失败');
    } finally {
      setLineageLoadingNodeIds((current) => current.filter((nodeId) => nodeId !== node.id));
    }
  };

  const publishActiveTask = async () => {
    if (!activeTaskNode) return;
    setPublishing(true);
    try {
      prepareDevelopmentTaskDefinition(activeTaskNode);
      let session = getEditorSession(activeTaskNode.id);
      if (!session || session.dirty || !session.draftRevision) {
        await persistDraft(activeTaskNode.id);
        session = getEditorSession(activeTaskNode.id);
      }
      if (!session?.draftRevision) {
        throw new Error('发布前请先保存草稿');
      }

      const published = responseData(
        await publishDevelopmentTask(activeTaskNode.id, session.draftRevision),
        '发布任务失败',
      );
      setVersionsRefreshKey((current) => current + 1);
      message.success(`已发布 v${published.revisionNo}`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布任务失败');
    } finally {
      setPublishing(false);
    }
  };

  const closeNodes = (nodeIds: DevelopmentId[]) => {
    if (!nodeIds.length) return;

    const { nextOpenNodeIds, nextActiveNodeId } = closeTabs(
      openNodeIds,
      activeNodeId,
      nodeIds,
    );
    const closeSet = new Set(nodeIds);
    setOpenNodeIds(nextOpenNodeIds);
    setResourceDirtyNodeIds((current) => current.filter((id) => !closeSet.has(id)));

    if (!activeNodeId || nextActiveNodeId === activeNodeId) return;

    setActiveNodeId(nextActiveNodeId);
    onNodeFocus(nextActiveNodeId);
    const nextNode = nextActiveNodeId ? nodeMap.get(nextActiveNodeId) : undefined;
    if (!nextNode || !isDevelopmentTaskNode(nextNode)) {
      setRunPanelOpen(false);
    } else if (nextNode.type !== 'SQL') {
      setBottomPanelView('result');
    }
  };

  const requestCloseNodes = (nodeIds: DevelopmentId[]) => {
    const targetNodeIds = openNodeIds.filter((nodeId) => nodeIds.includes(nodeId));
    if (!targetNodeIds.length) return;

    const dirtyResourceNodes = targetNodeIds
      .filter((nodeId) => resourceDirtyNodeIds.includes(nodeId))
      .map((nodeId) => nodeMap.get(nodeId))
      .filter((node): node is DevelopmentResourceNode => Boolean(node));
    if (dirtyResourceNodes.length) {
      const firstName = dirtyResourceNodes[0]?.name || '资源';
      message.warning(
        dirtyResourceNodes.length === 1
          ? `「${firstName}」有未保存修改，请先保存后再关闭`
          : `有 ${dirtyResourceNodes.length} 个资源编辑器尚未保存，请先保存后再关闭`,
      );
      return;
    }

    const dirtyNodeIds = targetNodeIds.filter((nodeId) => {
      const node = nodeMap.get(nodeId);
      return Boolean(node && isDevelopmentTaskNode(node) && getEditorSession(nodeId)?.dirty);
    });

    if (!dirtyNodeIds.length) {
      closeNodes(targetNodeIds);
      return;
    }

    setPendingClose({
      nodeIds: targetNodeIds,
      dirtyNodeIds,
    });
  };

  const resolvePendingClose = async (save: boolean) => {
    if (!pendingClose) return;

    if (save) {
      setCloseSaving(true);
      try {
        for (const nodeId of pendingClose.dirtyNodeIds) {
          if (getEditorSession(nodeId)?.dirty) {
            await persistDraft(nodeId);
          }
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '保存草稿失败');
        setCloseSaving(false);
        return;
      }
      setCloseSaving(false);
    } else {
      pendingClose.dirtyNodeIds.forEach((nodeId) => {
        const node = nodeMap.get(nodeId);
        if (node && isDevelopmentTaskNode(node)) restoreDevelopmentTaskOriginal(node);
      });
    }

    const nodeIds = pendingClose.nodeIds;
    setPendingClose(undefined);
    closeNodes(nodeIds);
  };

  const handleTabAction = (action: EditorTabAction) => {
    requestCloseNodes(tabActionTargets(action, openNodeIds, activeNodeId));
  };

  const pendingDirtyNames = pendingClose?.dirtyNodeIds
    .map((nodeId) => nodeMap.get(nodeId))
    .filter((node): node is DevelopmentResourceNode => Boolean(node && isDevelopmentTaskNode(node)))
    .map((node) => node.name) || [];

  if (!openNodeIds.length || !activeResource) {
    return (
      <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
        <div className="text-center">
          <div className="text-[14px] font-medium text-[#667085]">
            选择左侧开发节点
          </div>
          <div className="mt-1 text-[12px] text-[#98a2b3]">
            SQL、Shell、Dataset 和 Data Service 会在同一个开发工作台中打开
          </div>
        </div>
      </main>
    );
  }

  const definition = activeTaskNode ? getEditorDefinition(activeTaskNode.type) : undefined;

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <EditorTabs
        nodeMap={nodeMap}
        openNodeIds={openNodeIds}
        activeNodeId={activeNodeId}
        dirtyNodeIds={resourceDirtyNodeIds}
        onFocus={focusNode}
        onClose={(nodeId) => requestCloseNodes([nodeId])}
        onAction={handleTabAction}
      />

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
        {activeTaskNode && definition ? (
          <>
            <EditorToolbar
              node={activeTaskNode}
              directory={activeDirectory}
              definition={definition}
              onRun={() => void runActiveTask()}
              onSave={() => void saveActiveDraft()}
              onPublish={() => void publishActiveTask()}
              onLineage={activeTaskNode.type === 'SQL'
                ? () => void previewActiveLineage()
                : undefined}
              running={activeRunning}
              saving={saving}
              publishing={publishing}
              lineageLoading={activeLineageLoading}
            />

            <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
              <div className="flex min-h-0 flex-1 overflow-hidden">
                <EditorHost
                  node={activeTaskNode}
                  directory={activeDirectory}
                  definition={definition}
                  onRunContent={(content) => void runActiveTask(content)}
                  running={activeRunning}
                />
                <RightPanel
                  node={activeTaskNode}
                  directory={activeDirectory}
                  definition={definition}
                  versionsRefreshKey={versionsRefreshKey}
                />
              </div>

              <RunResultPanel
                open={runPanelOpen}
                node={activeTaskNode}
                directory={activeDirectory}
                definition={definition}
                result={activeRunResult}
                view={bottomPanelView}
                onViewChange={setBottomPanelView}
                lineagePreview={lineagePreviews[activeTaskNode.id]}
                lineageLoading={activeLineageLoading}
                onRefreshLineage={activeTaskNode.type === 'SQL'
                  ? () => void previewActiveLineage()
                  : undefined}
                onCancel={activeRunning ? () => void cancelActiveExecution() : undefined}
                onRetry={isDevelopmentExecutionRetryable(activeRunResult?.status)
                  ? () => void retryActiveExecution()
                  : undefined}
                actionLoading={activeExecutionActionLoading}
                onClose={() => setRunPanelOpen(false)}
              />
            </div>
          </>
        ) : null}

        {openDataServiceNodes.map((dataServiceNode) => (
          <DataServiceWorkbenchEditor
            key={dataServiceNode.id}
            node={dataServiceNode}
            active={activeNodeId === dataServiceNode.id}
            onSaved={onNodesChanged}
            onOpenSourceNode={focusNode}
            onDirtyChange={(dirty) => updateResourceDirty(dataServiceNode.id, dirty)}
          />
        ))}

        {openDatasetNodes.map((datasetNode) => (
          <DatasetWorkbenchEditor
            key={datasetNode.id}
            node={datasetNode}
            active={activeNodeId === datasetNode.id}
            onSaved={onNodesChanged}
            onDirtyChange={(dirty) => updateResourceDirty(datasetNode.id, dirty)}
          />
        ))}
      </div>

      <UnsavedChangesModal
        open={Boolean(pendingClose)}
        saving={closeSaving}
        dirtyNames={pendingDirtyNames}
        onSave={() => resolvePendingClose(true)}
        onDiscard={() => resolvePendingClose(false)}
        onCancel={() => setPendingClose(undefined)}
      />
    </main>
  );
};

export default DevelopmentWorkbench;
