import {
  createDevelopmentDirectory,
  createDevelopmentNode,
  deleteDevelopmentDirectory,
  deleteDevelopmentNode,
  listDevelopmentDirectories,
  listDevelopmentNodes,
  renameDevelopmentDirectory,
  renameDevelopmentNode,
  type DevelopmentDirectory,
  type DevelopmentId,
  type DevelopmentNodeType,
  type DevelopmentResourceNode,
} from '@/services/data-development';
import { message } from 'antd';
import type { Key, PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY } from '../constants';
import type {
  DevelopmentTreeAction,
  DevelopmentTreeNode,
  DevelopmentTreeNodeKey,
} from '../types';
import {
  buildDevelopmentTreeData,
  clampDevelopmentTreeWidth,
  copyDevelopmentText,
  developmentDirectoryKey,
  developmentIdFromTreeKey,
  developmentNodeKey,
  developmentNodeTypeForAction,
  filterDevelopmentTreeData,
  parseDevelopmentTreeWidth,
} from '../utils';

const initialTreeWidth = () => {
  if (typeof window === 'undefined') return parseDevelopmentTreeWidth(null);
  return parseDevelopmentTreeWidth(
    window.localStorage.getItem(DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY),
  );
};

export const useDataDevelopmentPage = () => {
  const requestSequenceRef = useRef(0);
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [nodes, setNodes] = useState<DevelopmentResourceNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [selectedNodeKey, setSelectedNodeKey] =
    useState<DevelopmentTreeNodeKey>();
  const [treeWidth, setTreeWidth] = useState(initialTreeWidth);
  const [treeCollapsed, setTreeCollapsed] = useState(false);

  const [createNodeOpen, setCreateNodeOpen] = useState(false);
  const [createNodeType, setCreateNodeType] =
    useState<DevelopmentNodeType>('SQL');
  const [nodeSaving, setNodeSaving] = useState(false);
  const [createDirectoryOpen, setCreateDirectoryOpen] = useState(false);
  const [directorySaving, setDirectorySaving] = useState(false);
  const [renameTarget, setRenameTarget] = useState<DevelopmentTreeNode>();
  const [renameSaving, setRenameSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DevelopmentTreeNode>();
  const [deleteSaving, setDeleteSaving] = useState(false);

  const loadTree = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setTreeLoading(true);

    try {
      const [nextDirectories, nextNodes] = await Promise.all([
        listDevelopmentDirectories(),
        listDevelopmentNodes(),
      ]);
      if (requestSequence !== requestSequenceRef.current) return;
      setDirectories(nextDirectories || []);
      setNodes(nextNodes || []);
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return;
      message.error(
        error instanceof Error ? error.message : '查询数据开发树失败',
      );
      setDirectories([]);
      setNodes([]);
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setTreeLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadTree();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadTree]);

  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );
  const selectedResourceNodeId = useMemo(
    () => developmentIdFromTreeKey(selectedNodeKey, 'node:'),
    [selectedNodeKey],
  );
  const directoryIdForSelection = useMemo(() => {
    const selectedDirectoryId = developmentIdFromTreeKey(
      selectedNodeKey,
      'directory:',
    );
    if (selectedDirectoryId) return selectedDirectoryId;
    if (!selectedResourceNodeId) return undefined;
    return nodeMap.get(selectedResourceNodeId)?.directoryId || undefined;
  }, [nodeMap, selectedNodeKey, selectedResourceNodeId]);

  const fullTreeData = useMemo(
    () => buildDevelopmentTreeData(directories, nodes),
    [directories, nodes],
  );
  const treeData = useMemo(
    () => filterDevelopmentTreeData(fullTreeData, treeKeyword),
    [fullTreeData, treeKeyword],
  );

  const handleResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (treeCollapsed) return;
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = treeWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setTreeWidth(
          clampDevelopmentTreeWidth(
            startWidth + moveEvent.clientX - startX,
          ),
        );
      };
      const finish = (upEvent: PointerEvent) => {
        const width = clampDevelopmentTreeWidth(
          startWidth + upEvent.clientX - startX,
        );
        setTreeWidth(width);
        window.localStorage.setItem(
          DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY,
          String(width),
        );
        document.body.style.cursor = previousCursor;
        document.body.style.userSelect = previousUserSelect;
        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('pointerup', finish);
        window.removeEventListener('pointercancel', finish);
      };

      window.addEventListener('pointermove', handlePointerMove);
      window.addEventListener('pointerup', finish);
      window.addEventListener('pointercancel', finish);
    },
    [treeCollapsed, treeWidth],
  );

  const openCreateNode = useCallback((type: DevelopmentNodeType) => {
    setCreateNodeType(type);
    setCreateNodeOpen(true);
  }, []);

  const closeCreateNode = useCallback(() => {
    if (!nodeSaving) setCreateNodeOpen(false);
  }, [nodeSaving]);

  const openCreateDirectory = useCallback(() => {
    setCreateDirectoryOpen(true);
  }, []);

  const closeCreateDirectory = useCallback(() => {
    if (!directorySaving) setCreateDirectoryOpen(false);
  }, [directorySaving]);

  const submitDirectory = useCallback(
    async (parentId: DevelopmentId | undefined, name: string) => {
      setDirectorySaving(true);
      try {
        const created = await createDevelopmentDirectory({ parentId, name });
        setCreateDirectoryOpen(false);
        setTreeKeyword('');
        await loadTree();
        setSelectedNodeKey(developmentDirectoryKey(created.id));
        message.success('目录创建成功');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '新建目录失败');
      } finally {
        setDirectorySaving(false);
      }
    },
    [loadTree],
  );

  const submitNode = useCallback(
    async (
      type: DevelopmentNodeType,
      directoryId: DevelopmentId | undefined,
      name: string,
    ) => {
      setNodeSaving(true);
      try {
        const created = await createDevelopmentNode({
          name,
          type,
          directoryId,
        });
        setCreateNodeOpen(false);
        setTreeKeyword('');
        await loadTree();
        setSelectedNodeKey(developmentNodeKey(created.id));
        message.success('节点创建成功');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '新建节点失败');
      } finally {
        setNodeSaving(false);
      }
    },
    [loadTree],
  );

  const copyResourceText = useCallback(
    async (value: string, successText: string) => {
      try {
        await copyDevelopmentText(value);
        message.success(successText);
      } catch {
        message.error('复制失败，请检查浏览器剪贴板权限');
      }
    },
    [],
  );

  const handleResourceAction = useCallback(
    (action: DevelopmentTreeAction, resource: DevelopmentTreeNode) => {
      setSelectedNodeKey(resource.key);
      if (action === 'create-directory') {
        setCreateDirectoryOpen(true);
        return;
      }

      const type = developmentNodeTypeForAction(action);
      if (type) {
        openCreateNode(type);
        return;
      }
      if (action === 'copy-name') {
        void copyResourceText(resource.title, '名称已复制');
        return;
      }
      if (action === 'copy-path') {
        void copyResourceText(resource.resourcePath, '路径已复制');
        return;
      }
      if (action === 'rename') {
        setRenameTarget(resource);
        return;
      }
      if (action === 'delete') setDeleteTarget(resource);
    },
    [copyResourceText, openCreateNode],
  );

  const submitRename = useCallback(
    async (name: string) => {
      if (!renameTarget) return;
      setRenameSaving(true);
      try {
        if (renameTarget.nodeType === 'directory') {
          await renameDevelopmentDirectory(renameTarget.resourceId, name);
        } else {
          await renameDevelopmentNode(renameTarget.resourceId, name);
        }
        setRenameTarget(undefined);
        setTreeKeyword('');
        await loadTree();
        message.success('重命名成功');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '重命名失败');
      } finally {
        setRenameSaving(false);
      }
    },
    [loadTree, renameTarget],
  );

  const closeRename = useCallback(() => {
    if (!renameSaving) setRenameTarget(undefined);
  }, [renameSaving]);

  const submitDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleteSaving(true);
    try {
      if (deleteTarget.nodeType === 'directory') {
        await deleteDevelopmentDirectory(deleteTarget.resourceId);
      } else {
        await deleteDevelopmentNode(deleteTarget.resourceId);
      }
      setDeleteTarget(undefined);
      setSelectedNodeKey(undefined);
      setTreeKeyword('');
      await loadTree();
      message.success('删除成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    } finally {
      setDeleteSaving(false);
    }
  }, [deleteTarget, loadTree]);

  const closeDelete = useCallback(() => {
    if (!deleteSaving) setDeleteTarget(undefined);
  }, [deleteSaving]);

  const selectTreeNodes = useCallback((keys: Key[]) => {
    const key = keys[0];
    setSelectedNodeKey(
      key ? (String(key) as DevelopmentTreeNodeKey) : undefined,
    );
  }, []);

  const focusNode = useCallback((nodeId?: DevelopmentId) => {
    setSelectedNodeKey(nodeId ? developmentNodeKey(nodeId) : undefined);
  }, []);

  return {
    directories,
    nodes,
    treeData,
    treeLoading,
    treeKeyword,
    selectedNodeKey,
    selectedResourceNodeId,
    directoryIdForSelection,
    treeWidth,
    treeCollapsed,
    createNodeOpen,
    createNodeType,
    nodeSaving,
    createDirectoryOpen,
    directorySaving,
    renameTarget,
    renameSaving,
    deleteTarget,
    deleteSaving,
    setTreeKeyword,
    setTreeCollapsed,
    openCreateNode,
    closeCreateNode,
    openCreateDirectory,
    closeCreateDirectory,
    handleResizeStart,
    handleResourceAction,
    selectTreeNodes,
    focusNode,
    submitDirectory,
    submitNode,
    submitRename,
    closeRename,
    submitDelete,
    closeDelete,
    loadTree,
  };
};
