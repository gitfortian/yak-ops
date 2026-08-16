import { API_SUCCESS_CODE } from '@/services/http/response';
import { Button, Modal, message } from 'antd';
import { TriangleAlert } from 'lucide-react';
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
import { isDevelopmentTaskNode } from '../../node-model';
import {
  getDevelopmentTaskDraft,
  publishDevelopmentTask,
  runDevelopmentTask,
  saveDevelopmentTaskDraft,
} from '../../service';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
  DevelopmentResourceNode,
  DevelopmentTaskDraft,
  DevelopmentTaskRunResult,
} from '../../types';
import DataServiceNodeEditor from '../data-service/DataServiceNodeEditor';
import EditorHost from './EditorHost';
import EditorTabs, { type EditorTabAction } from './EditorTabs';
import EditorToolbar from './EditorToolbar';
import RightPanel from './RightPanel';
import RunResultPanel from './RunResultPanel';

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

interface DataServiceWorkbenchEditorProps {
  node: DevelopmentResourceNode;
  active: boolean;
  onSaved?: () => void | Promise<void>;
  onOpenSourceNode: (nodeId: DevelopmentId) => void;
  onDirtyChange: (dirty: boolean) => void;
}

/**
 * Keep the authoring node identity stable while the directory tree refreshes. A save in another tab
 * may recreate the node list; that must not force a hidden Data Service editor to reload and discard
 * its unsaved local form state.
 */
const DataServiceWorkbenchEditor = ({
  node,
  active,
  onSaved,
  onOpenSourceNode,
  onDirtyChange,
}: DataServiceWorkbenchEditorProps) => {
  const stableNode = useMemo(
    () => node,
    [node.id, node.name],
  );

  return (
    <div
      className={[
        'min-h-0 flex-1 overflow-hidden',
        active ? 'flex' : 'hidden',
      ].join(' ')}
    >
      <DataServiceNodeEditor
        node={stableNode}
        onSaved={onSaved}
        onOpenSourceNode={onOpenSourceNode}
        onDirtyChange={onDirtyChange}
      />
    </div>
  );
};

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

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
  const [runResults, setRunResults] = useState<
    Partial<Record<DevelopmentId, DevelopmentTaskRunResult>>
  >({});
  const [runningNodeIds, setRunningNodeIds] = useState<DevelopmentId[]>([]);
  const [pendingClose, setPendingClose] = useState<PendingCloseRequest>();
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [closeSaving, setCloseSaving] = useState(false);
  const [versionsRefreshKey, setVersionsRefreshKey] = useState(0);
  const [dataServiceDirtyNodeIds, setDataServiceDirtyNodeIds] = useState<DevelopmentId[]>([]);

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
    setDataServiceDirtyNodeIds((current) => current.filter((nodeId) => nodeMap.has(nodeId)));
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

  const activeResource = activeNodeId ? nodeMap.get(activeNodeId) : undefined;
  const activeTaskNode = activeResource && isDevelopmentTaskNode(activeResource)
    ? activeResource
    : undefined;
  const activeDirectory = activeResource?.directoryId
    ? directoryMap.get(activeResource.directoryId)
    : undefined;
  const activeRunning = activeTaskNode
    ? runningNodeIds.includes(activeTaskNode.id)
    : false;
  const openDataServiceNodes = useMemo(
    () => openNodeIds
      .map((nodeId) => nodeMap.get(nodeId))
      .filter((node): node is DevelopmentResourceNode => Boolean(node && node.type === 'DATA_SERVICE')),
    [nodeMap, openNodeIds],
  );

  const focusNode = (nodeId: DevelopmentId) => {
    const target = nodeMap.get(nodeId);
    if (!target) return;
    setOpenNodeIds((current) => current.includes(nodeId) ? current : [...current, nodeId]);
    setActiveNodeId(nodeId);
    if (target.type === 'DATA_SERVICE') setRunPanelOpen(false);
    onNodeFocus(nodeId);
  };

  const updateDataServiceDirty = (nodeId: DevelopmentId, dirty: boolean) => {
    setDataServiceDirtyNodeIds((current) => {
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
    if (!activeTaskNode || runningNodeIds.includes(activeTaskNode.id)) return;
    const node = activeTaskNode;
    const startedAt = Date.now();
    setRunPanelOpen(true);
    setRunningNodeIds((current) => [...current, node.id]);
    setRunResults((current) => ({
      ...current,
      [node.id]: {
        status: 'RUNNING',
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
      const result = responseData(
        await runDevelopmentTask(node.id, runDefinition),
        '运行任务失败',
      );
      setRunResults((current) => ({ ...current, [node.id]: result }));
      if (result.status === 'FAILED' || result.status === 'TIMEOUT') {
        message.error(result.message || 'SQL 执行失败');
      }
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
    } finally {
      setRunningNodeIds((current) => current.filter((nodeId) => nodeId !== node.id));
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

    const closeSet = new Set(nodeIds);
    const currentIndex = activeNodeId ? openNodeIds.indexOf(activeNodeId) : -1;
    const next = openNodeIds.filter((id) => !closeSet.has(id));
    setOpenNodeIds(next);
    setDataServiceDirtyNodeIds((current) => current.filter((id) => !closeSet.has(id)));

    if (!activeNodeId || !closeSet.has(activeNodeId)) return;

    const nextActiveId =
      next[Math.min(Math.max(currentIndex, 0), next.length - 1)] ||
      next[next.length - 1];
    setActiveNodeId(nextActiveId);
    onNodeFocus(nextActiveId);
    if (!nextActiveId || nodeMap.get(nextActiveId)?.type === 'DATA_SERVICE') {
      setRunPanelOpen(false);
    }
  };

  const requestCloseNodes = (nodeIds: DevelopmentId[]) => {
    const targetNodeIds = openNodeIds.filter((nodeId) => nodeIds.includes(nodeId));
    if (!targetNodeIds.length) return;

    const dirtyDataServiceNodes = targetNodeIds
      .filter((nodeId) => dataServiceDirtyNodeIds.includes(nodeId))
      .map((nodeId) => nodeMap.get(nodeId))
      .filter((node): node is DevelopmentResourceNode => Boolean(node));
    if (dirtyDataServiceNodes.length) {
      const firstName = dirtyDataServiceNodes[0]?.name || 'Data Service';
      message.warning(
        dirtyDataServiceNodes.length === 1
          ? `「${firstName}」有未保存修改，请先保存草稿后再关闭`
          : `有 ${dirtyDataServiceNodes.length} 个 Data Service 编辑器尚未保存，请先保存草稿`,
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
    if (!activeNodeId) return;
    const activeIndex = openNodeIds.indexOf(activeNodeId);
    if (activeIndex < 0) return;

    if (action === 'close-current') {
      requestCloseNodes([activeNodeId]);
      return;
    }
    if (action === 'close-all') {
      requestCloseNodes(openNodeIds);
      return;
    }
    if (action === 'close-others') {
      requestCloseNodes(openNodeIds.filter((nodeId) => nodeId !== activeNodeId));
      return;
    }
    if (action === 'close-left') {
      requestCloseNodes(openNodeIds.slice(0, activeIndex));
      return;
    }
    if (action === 'close-right') {
      requestCloseNodes(openNodeIds.slice(activeIndex + 1));
    }
  };

  const pendingDirtyNodes = pendingClose?.dirtyNodeIds
    .map((nodeId) => nodeMap.get(nodeId))
    .filter((node): node is DevelopmentNode => Boolean(node && isDevelopmentTaskNode(node)));
  const pendingDirtyCount = pendingDirtyNodes?.length || 0;
  const pendingDirtyName = pendingDirtyNodes?.[0]?.name || '当前编辑器';

  if (!openNodeIds.length || !activeResource) {
    return (
      <main className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-white">
        <div className="text-center">
          <div className="text-[14px] font-medium text-[#667085]">
            选择左侧开发节点
          </div>
          <div className="mt-1 text-[12px] text-[#98a2b3]">
            SQL、Shell 和 Data Service 会在同一个开发工作台中打开
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
        dirtyNodeIds={dataServiceDirtyNodeIds}
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
              running={activeRunning}
              saving={saving}
              publishing={publishing}
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
                result={runResults[activeTaskNode.id]}
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
            onDirtyChange={(dirty) => updateDataServiceDirty(dataServiceNode.id, dirty)}
          />
        ))}
      </div>

      <Modal
        open={Boolean(pendingClose)}
        title={null}
        footer={null}
        width={520}
        centered
        maskClosable={false}
        closable={!closeSaving}
        onCancel={() => {
          if (!closeSaving) setPendingClose(undefined);
        }}
      >
        <div className="flex gap-4 px-1 py-2">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center text-[#f79009]">
            <TriangleAlert size={36} strokeWidth={1.7} />
          </div>

          <div className="min-w-0 flex-1 pt-1">
            <div className="text-[16px] font-medium leading-6 text-[#1f2937]">
              {pendingDirtyCount <= 1 ? (
                <>
                  是否要保存对 <span className="font-semibold">{pendingDirtyName}</span>{' '}
                  的更改？
                </>
              ) : (
                <>是否要保存 {pendingDirtyCount} 个已修改编辑器的更改？</>
              )}
            </div>

            <div className="mt-4 text-[13px] leading-5 text-[#475467]">
              如果不保存，{pendingDirtyCount <= 1 ? '你的更改' : '这些更改'}将丢失。
            </div>

            {pendingDirtyCount > 1 ? (
              <div
                className="mt-2 max-w-[360px] truncate text-[12px] text-[#98a2b3]"
                title={pendingDirtyNodes?.map((node) => node.name).join('、')}
              >
                {pendingDirtyNodes?.map((node) => node.name).join('、')}
              </div>
            ) : null}

            <div className="mt-6 flex justify-end gap-2">
              <Button
                type="primary"
                loading={closeSaving}
                onClick={() => void resolvePendingClose(true)}
              >
                {pendingDirtyCount > 1 ? '保存全部' : '保存'}
              </Button>
              <Button
                disabled={closeSaving}
                onClick={() => void resolvePendingClose(false)}
              >
                {pendingDirtyCount > 1 ? '全部不保存' : '不保存'}
              </Button>
              <Button
                disabled={closeSaving}
                onClick={() => setPendingClose(undefined)}
              >
                取消
              </Button>
            </div>
          </div>
        </div>
      </Modal>
    </main>
  );
};

export default DevelopmentWorkbench;
