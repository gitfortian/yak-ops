import { API_SUCCESS_CODE } from '@/services/http/response';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { BRAND_THEME } from '@/styles/brand';
import { ConfigProvider, Modal, message } from 'antd';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import CreateDirectoryModal from './components/CreateDirectoryModal';
import CreateTaskModal from './components/CreateTaskModal';
import DevelopmentEditorWorkspace from './components/DevelopmentEditorWorkspace';
import DevelopmentTreePane, {
  type DevelopmentNodeCreateType,
  type DevelopmentTreeAction,
  type DevelopmentTreeNode,
} from './components/DevelopmentTreePane';
import RenameResourceModal from './components/RenameResourceModal';
import {
  createDevelopmentDirectory,
  createDevelopmentNode,
  deleteDevelopmentDirectory,
  deleteDevelopmentNode,
  listDevelopmentDirectories,
  listDevelopmentNodes,
  renameDevelopmentDirectory,
  renameDevelopmentNode,
} from './service';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNode,
  DevelopmentTaskType,
} from './types';

type TreeNodeKey = `directory:${string}` | `node:${string}`;

const DEFAULT_LEFT_WIDTH = 300;
const MIN_LEFT_WIDTH = 220;
const MAX_LEFT_WIDTH = 440;
const LEFT_WIDTH_STORAGE_KEY = 'yak-data-development.left-width';

const directoryKey = (directoryId: DevelopmentId): TreeNodeKey =>
  `directory:${directoryId}`;
const nodeKey = (nodeId: DevelopmentId): TreeNodeKey => `node:${nodeId}`;

const idFromKey = (key: string | undefined, prefix: string) => {
  if (!key?.startsWith(prefix)) return undefined;
  const value = key.substring(prefix.length).trim();
  return value || undefined;
};

const clampLeftWidth = (value: number) =>
  Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, value));

const initialLeftWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_LEFT_WIDTH;
  const stored = Number(window.localStorage.getItem(LEFT_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampLeftWidth(stored)
    : DEFAULT_LEFT_WIDTH;
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

export default function DataDevelopmentPage() {
  const { currentProject } = useSecurityProject();
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [nodes, setNodes] = useState<DevelopmentNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [selectedNodeKey, setSelectedNodeKey] = useState<TreeNodeKey>();
  const [leftWidth, setLeftWidth] = useState(initialLeftWidth);
  const [leftCollapsed, setLeftCollapsed] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [createType, setCreateType] = useState<DevelopmentNodeCreateType>('SQL');
  const [nodeSaving, setNodeSaving] = useState(false);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [directorySaving, setDirectorySaving] = useState(false);
  const [renameTarget, setRenameTarget] = useState<DevelopmentTreeNode>();
  const [renameSaving, setRenameSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DevelopmentTreeNode>();
  const [deleteSaving, setDeleteSaving] = useState(false);

  const loadTree = useCallback(async () => {
    setTreeLoading(true);
    try {
      const [directoryResponse, nodeResponse] = await Promise.all([
        listDevelopmentDirectories(),
        listDevelopmentNodes(),
      ]);
      setDirectories(responseData(directoryResponse, '查询数据开发目录失败') || []);
      setNodes(responseData(nodeResponse, '查询数据开发节点失败') || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '查询数据开发树失败');
      setDirectories([]);
      setNodes([]);
    } finally {
      setTreeLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTree();
  }, [loadTree]);

  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );
  const directoryPathMap = useMemo(
    () => new Map(directories.map((directory) => [directory.id, directory.path])),
    [directories],
  );
  const selectedResourceNodeId = useMemo(
    () => idFromKey(selectedNodeKey, 'node:'),
    [selectedNodeKey],
  );

  const directoryIdForSelection = useMemo(() => {
    const selectedDirectoryId = idFromKey(selectedNodeKey, 'directory:');
    if (selectedDirectoryId) return selectedDirectoryId;

    if (!selectedResourceNodeId) return undefined;
    const selectedResourceNode = nodeMap.get(selectedResourceNodeId);
    return selectedResourceNode?.directoryId || undefined;
  }, [nodeMap, selectedNodeKey, selectedResourceNodeId]);

  const fullTreeData = useMemo<DevelopmentTreeNode[]>(() => {
    const resourceNodes = (directoryId?: DevelopmentId): DevelopmentTreeNode[] =>
      nodes
        .filter((node) => (node.directoryId || undefined) === directoryId)
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((node) => {
          const parentPath = directoryId ? directoryPathMap.get(directoryId) || '' : '';
          return {
            key: nodeKey(node.id),
            title: node.name,
            nodeType: 'node',
            resourceId: node.id,
            resourcePath: `${parentPath}/${node.name}`,
            taskType: node.type,
            searchText: `${node.name} ${node.type} ${node.id}`,
            isLeaf: true,
          };
        });

    const directoryNodes = (parentId?: DevelopmentId): DevelopmentTreeNode[] =>
      directories
        .filter((directory) => (directory.parentId || undefined) === parentId)
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((directory) => ({
          key: directoryKey(directory.id),
          title: directory.name,
          nodeType: 'directory',
          resourceId: directory.id,
          resourcePath: directory.path,
          searchText: `${directory.name} ${directory.path || ''}`,
          children: [
            ...directoryNodes(directory.id),
            ...resourceNodes(directory.id),
          ],
        }));

    return [...directoryNodes(), ...resourceNodes()];
  }, [directories, directoryPathMap, nodes]);

  const treeData = useMemo<DevelopmentTreeNode[]>(() => {
    const normalized = treeKeyword.trim().toLowerCase();
    if (!normalized) return fullTreeData;

    const filterNodes = (
      values: DevelopmentTreeNode[],
    ): DevelopmentTreeNode[] =>
      values.flatMap((node) => {
        const children = node.children ? filterNodes(node.children) : [];
        const text = `${node.title} ${node.searchText || ''}`.toLowerCase();
        if (text.includes(normalized)) {
          return [{ ...node, children: node.children }];
        }
        if (children.length) {
          return [{ ...node, children }];
        }
        return [];
      });

    return filterNodes(fullTreeData);
  }, [fullTreeData, treeKeyword]);

  const handleResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (leftCollapsed) return;
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = leftWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setLeftWidth(clampLeftWidth(startWidth + moveEvent.clientX - startX));
      };
      const finish = (upEvent: PointerEvent) => {
        const width = clampLeftWidth(startWidth + upEvent.clientX - startX);
        setLeftWidth(width);
        window.localStorage.setItem(LEFT_WIDTH_STORAGE_KEY, String(width));
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
    [leftCollapsed, leftWidth],
  );

  const submitDirectory = async (
    parentId: DevelopmentId | undefined,
    name: string,
  ) => {
    setDirectorySaving(true);
    try {
      const created = responseData(
        await createDevelopmentDirectory({ parentId, name }),
        '新建目录失败',
      );
      setDirectoryOpen(false);
      setTreeKeyword('');
      await loadTree();
      setSelectedNodeKey(directoryKey(created.id));
      message.success('目录创建成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建目录失败');
    } finally {
      setDirectorySaving(false);
    }
  };

  const submitNode = async (
    type: DevelopmentTaskType,
    projectId: DevelopmentId | undefined,
    directoryId: DevelopmentId | undefined,
    name: string,
  ) => {
    setNodeSaving(true);
    try {
      const created = responseData(
        await createDevelopmentNode({
          name,
          type,
          projectId,
          directoryId,
        }),
        '新建节点失败',
      );
      setCreateOpen(false);
      setTreeKeyword('');
      await loadTree();
      setSelectedNodeKey(nodeKey(created.id));
      message.success('节点创建成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '新建节点失败');
    } finally {
      setNodeSaving(false);
    }
  };

  const copyText = async (value: string, successText: string) => {
    try {
      await navigator.clipboard.writeText(value);
      message.success(successText);
    } catch {
      message.error('复制失败，请检查浏览器剪贴板权限');
    }
  };

  const handleResourceAction = (
    action: DevelopmentTreeAction,
    resource: DevelopmentTreeNode,
  ) => {
    setSelectedNodeKey(resource.key as TreeNodeKey);
    if (action === 'create-directory') {
      setDirectoryOpen(true);
      return;
    }
    if (action === 'create-sql' || action === 'create-shell') {
      setCreateType(action === 'create-sql' ? 'SQL' : 'SHELL');
      setCreateOpen(true);
      return;
    }
    if (action === 'copy-name') {
      void copyText(resource.title, '名称已复制');
      return;
    }
    if (action === 'copy-path') {
      void copyText(resource.resourcePath, '路径已复制');
      return;
    }
    if (action === 'rename') {
      setRenameTarget(resource);
      return;
    }
    if (action === 'delete') {
      setDeleteTarget(resource);
    }
  };

  const submitRename = async (name: string) => {
    if (!renameTarget) return;
    setRenameSaving(true);
    try {
      if (renameTarget.nodeType === 'directory') {
        responseData(
          await renameDevelopmentDirectory(renameTarget.resourceId, name),
          '目录重命名失败',
        );
      } else {
        responseData(
          await renameDevelopmentNode(renameTarget.resourceId, name),
          '节点重命名失败',
        );
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
  };

  const submitDelete = async () => {
    if (!deleteTarget) return;
    setDeleteSaving(true);
    try {
      if (deleteTarget.nodeType === 'directory') {
        responseData(
          await deleteDevelopmentDirectory(deleteTarget.resourceId),
          '目录删除失败',
        );
      } else {
        responseData(
          await deleteDevelopmentNode(deleteTarget.resourceId),
          '节点删除失败',
        );
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
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-[#f5f5f6]">
        <div className="flex min-h-0 flex-1 overflow-hidden border border-[#e4e7ec] bg-white shadow-[0_1px_2px_rgba(16,24,40,0.03)]">
          <DevelopmentTreePane
            treeData={treeData}
            treeLoading={treeLoading}
            selectedNodeKey={selectedNodeKey}
            searchValue={treeKeyword}
            leftWidth={leftWidth}
            collapsed={leftCollapsed}
            onCreateDirectory={() => setDirectoryOpen(true)}
            onCreateNode={(type) => {
              setCreateType(type);
              setCreateOpen(true);
            }}
            onResourceAction={handleResourceAction}
            onSearchChange={setTreeKeyword}
            onResizeStart={handleResizeStart}
            onCollapsedChange={setLeftCollapsed}
            onSelect={(keys) => {
              const key = keys[0];
              setSelectedNodeKey(key ? (String(key) as TreeNodeKey) : undefined);
            }}
          />

          <DevelopmentEditorWorkspace
            nodes={nodes}
            directories={directories}
            selectedNodeId={selectedResourceNodeId}
            onNodeFocus={(nodeId) =>
              setSelectedNodeKey(nodeId ? nodeKey(nodeId) : undefined)
            }
          />
        </div>

        <CreateTaskModal
          open={createOpen}
          type={createType}
          directories={directories}
          loading={nodeSaving}
          defaultProjectId={
            currentProject?.id ? String(currentProject.id) : undefined
          }
          defaultDirectoryId={directoryIdForSelection}
          onCancel={() => {
            if (!nodeSaving) setCreateOpen(false);
          }}
          onNext={(type, projectId, directoryId, name) => {
            void submitNode(type, projectId, directoryId, name);
          }}
        />

        <CreateDirectoryModal
          open={directoryOpen}
          directories={directories}
          defaultParentId={directoryIdForSelection}
          loading={directorySaving}
          onCancel={() => {
            if (!directorySaving) setDirectoryOpen(false);
          }}
          onSubmit={(parentId, name) => void submitDirectory(parentId, name)}
        />

        <RenameResourceModal
          open={Boolean(renameTarget)}
          resourceLabel={renameTarget?.nodeType === 'directory' ? '目录' : '节点'}
          initialName={renameTarget?.title || ''}
          loading={renameSaving}
          onCancel={() => {
            if (!renameSaving) setRenameTarget(undefined);
          }}
          onSubmit={(name) => void submitRename(name)}
        />

        <Modal
          open={Boolean(deleteTarget)}
          title={`删除${deleteTarget?.nodeType === 'directory' ? '目录' : '节点'}`}
          okText="删除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
          confirmLoading={deleteSaving}
          maskClosable={!deleteSaving}
          closable={!deleteSaving}
          onCancel={() => {
            if (!deleteSaving) setDeleteTarget(undefined);
          }}
          onOk={() => void submitDelete()}
        >
          <div className="pt-2 text-[13px] leading-6 text-[#475467]">
            确认删除“{deleteTarget?.title}”吗？
            {deleteTarget?.nodeType === 'directory' ? (
              <div className="mt-1 text-[#98a2b3]">
                仅空目录可以删除；存在子目录或节点时后端会拒绝本次操作。
              </div>
            ) : null}
          </div>
        </Modal>
      </div>
    </ConfigProvider>
  );
}