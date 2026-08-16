import { API_SUCCESS_CODE } from '@/services/http/response';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { Button, Input, Modal, Select, Spin, Tooltip, message } from 'antd';
import { Braces, Database, Network, Save } from 'lucide-react';
import type { DragEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  addEdge,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  type ReactFlowInstance,
  useEdgesState,
  useNodesState,
} from 'reactflow';
import 'reactflow/dist/style.css';

import { canConnectNodes, isDevelopmentTaskNodeType } from '../../node-model';
import {
  createDevelopmentNode,
  deleteDevelopmentNode,
  getDevelopmentGraph,
  saveDevelopmentGraph,
} from '../../service';
import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
  DevelopmentResourceNode,
} from '../../types';
import DevelopmentDagNode, { type DevelopmentDagNodeData } from './DevelopmentDagNode';
import { defaultDagPosition, wouldCreateCycle } from './dag-model';

const NODE_DRAG_TYPE = 'application/yak-development-node-type';
type DagFlowNode = Node<DevelopmentDagNodeData>;

interface DevelopmentDagCanvasProps {
  resources: DevelopmentResourceNode[];
  directories: DevelopmentDirectory[];
  selectedNodeId?: DevelopmentId;
  onNodeOpen: (nodeId: DevelopmentId) => void;
  onResourcesChanged?: () => void | Promise<void>;
}

interface PendingCreate {
  type: 'SQL' | 'DATASET' | 'DATA_SERVICE';
  position: { x: number; y: number };
}

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const palette = [
  { title: '数据处理', items: [{ type: 'SQL' as const, label: 'SQL', icon: <Braces size={15} /> }] },
  {
    title: '数据输出',
    items: [
      { type: 'DATASET' as const, label: '数据集', icon: <Database size={15} /> },
      { type: 'DATA_SERVICE' as const, label: '数据服务', icon: <Network size={15} /> },
    ],
  },
];

const resourceInProject = (
  resource: DevelopmentResourceNode,
  projectId?: DevelopmentId,
) => {
  const resourceProjectId = resource.projectId ? String(resource.projectId) : undefined;
  return projectId ? resourceProjectId === projectId : !resourceProjectId;
};

const typeLabel = (type: DevelopmentNodeType) => {
  if (type === 'DATASET') return '数据集';
  if (type === 'DATA_SERVICE') return '数据服务';
  return type;
};

export default function DevelopmentDagCanvas({
  resources,
  directories,
  selectedNodeId,
  onNodeOpen,
  onResourcesChanged,
}: DevelopmentDagCanvasProps) {
  const { currentProject } = useSecurityProject();
  const projectId = currentProject?.id ? String(currentProject.id) : undefined;
  const wrapperRef = useRef<HTMLDivElement>(null);
  const draftPositionRef = useRef(new Map<DevelopmentId, { x: number; y: number }>());
  const savedPositionRef = useRef(new Map<DevelopmentId, { x: number; y: number }>());
  const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance>();
  const [flowNodes, setFlowNodes, onNodesChange] = useNodesState<DevelopmentDagNodeData>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [pendingCreate, setPendingCreate] = useState<PendingCreate>();
  const [createName, setCreateName] = useState('');
  const [createDirectoryId, setCreateDirectoryId] = useState<DevelopmentId>();
  const [creating, setCreating] = useState(false);

  const scopedResources = useMemo(
    () => resources.filter((resource) => resourceInProject(resource, projectId)),
    [projectId, resources],
  );
  const resourceMap = useMemo(
    () => new Map(scopedResources.map((resource) => [resource.id, resource])),
    [scopedResources],
  );

  const removeResource = useCallback((nodeId: DevelopmentId) => {
    const resource = resourceMap.get(nodeId);
    if (!resource) return;
    Modal.confirm({
      title: `删除${typeLabel(resource.type)}节点`,
      content: `确认删除“${resource.name}”吗？相关 DAG 连线会一并从画布移除。`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        responseData(await deleteDevelopmentNode(nodeId), '删除节点失败');
        setFlowNodes((current) => current.filter((node) => node.id !== nodeId));
        setEdges((current) => current.filter(
          (edge) => edge.source !== nodeId && edge.target !== nodeId,
        ));
        setDirty(true);
        await onResourcesChanged?.();
        message.success('节点已删除');
      },
    });
  }, [onResourcesChanged, resourceMap, setEdges, setFlowNodes]);

  const toFlowNode = useCallback((
    resource: DevelopmentResourceNode,
    index: number,
    existing?: DagFlowNode,
  ): DagFlowNode => ({
    id: resource.id,
    type: 'development',
    position:
      existing?.position
      || draftPositionRef.current.get(resource.id)
      || savedPositionRef.current.get(resource.id)
      || defaultDagPosition(index),
    selected: selectedNodeId === resource.id,
    deletable: false,
    data: { resource, onDelete: removeResource },
  }), [removeResource, selectedNodeId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getDevelopmentGraph(projectId)
      .then((response) => {
        if (!active) return;
        const graph = responseData(response, '加载数据开发 DAG 失败');
        savedPositionRef.current = new Map(
          graph.nodes.map((item) => [item.nodeId, { x: item.x, y: item.y }]),
        );
        setEdges(graph.edges.map((edge) => ({
          id: `edge-${edge.sourceNodeId}-${edge.targetNodeId}`,
          source: edge.sourceNodeId,
          target: edge.targetNodeId,
          type: 'smoothstep',
        })));
        setFlowNodes((current) => current.map((node) => {
          const savedPosition = savedPositionRef.current.get(node.id);
          return savedPosition ? { ...node, position: savedPosition } : node;
        }));
        setDirty(false);
      })
      .catch((error) => {
        if (!active) return;
        savedPositionRef.current = new Map();
        setEdges([]);
        message.error(error instanceof Error ? error.message : '加载数据开发 DAG 失败');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [projectId, setEdges, setFlowNodes]);

  useEffect(() => {
    setFlowNodes((current) => {
      const currentMap = new Map(current.map((node) => [node.id, node]));
      return scopedResources.map((resource, index) =>
        toFlowNode(resource, index, currentMap.get(resource.id)),
      );
    });
    const ids = new Set(scopedResources.map((resource) => resource.id));
    setEdges((current) => current.filter(
      (edge) => ids.has(edge.source) && ids.has(edge.target),
    ));
  }, [scopedResources, setEdges, setFlowNodes, toFlowNode]);

  useEffect(() => {
    if (!selectedNodeId || !reactFlowInstance || !resourceMap.has(selectedNodeId)) return;
    const node = flowNodes.find((item) => item.id === selectedNodeId);
    if (!node) return;
    reactFlowInstance.setCenter(node.position.x + 120, node.position.y + 45, {
      zoom: Math.min(reactFlowInstance.getZoom(), 1),
      duration: 220,
    });
  }, [flowNodes, reactFlowInstance, resourceMap, selectedNodeId]);

  const handleNodesChange = useCallback((changes: NodeChange[]) => {
    if (changes.some((change) => change.type === 'position')) setDirty(true);
    onNodesChange(changes);
  }, [onNodesChange]);

  const handleEdgesChange = useCallback((changes: EdgeChange[]) => {
    if (changes.some((change) => change.type === 'remove')) setDirty(true);
    onEdgesChange(changes);
  }, [onEdgesChange]);

  const handleConnect = useCallback((connection: Connection) => {
    if (!connection.source || !connection.target) return;
    const source = resourceMap.get(connection.source);
    const target = resourceMap.get(connection.target);
    if (!source || !target) return;
    if (!canConnectNodes(source.type, target.type)) {
      message.warning(`暂不支持 ${typeLabel(source.type)} → ${typeLabel(target.type)} 的连接`);
      return;
    }
    if (edges.some(
      (edge) => edge.source === connection.source && edge.target === connection.target,
    )) {
      message.info('这条依赖关系已经存在');
      return;
    }
    if (wouldCreateCycle(edges, connection.source, connection.target)) {
      message.warning('数据开发 DAG 不能形成循环依赖');
      return;
    }
    setEdges((current) => addEdge({
      ...connection,
      id: `edge-${connection.source}-${connection.target}`,
      type: 'smoothstep',
    }, current));
    setDirty(true);
  }, [edges, resourceMap, setEdges]);

  const saveGraph = async () => {
    setSaving(true);
    try {
      const saved = responseData(
        await saveDevelopmentGraph({
          projectId,
          nodes: flowNodes.map((node) => ({
            nodeId: node.id,
            x: node.position.x,
            y: node.position.y,
          })),
          edges: edges.map((edge) => ({
            sourceNodeId: edge.source,
            targetNodeId: edge.target,
          })),
        }),
        '保存数据开发 DAG 失败',
      );
      savedPositionRef.current = new Map(
        saved.nodes.map((item) => [item.nodeId, { x: item.x, y: item.y }]),
      );
      setDirty(false);
      message.success('DAG 已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存数据开发 DAG 失败');
    } finally {
      setSaving(false);
    }
  };

  const onDragStart = (
    event: DragEvent<HTMLDivElement>,
    type: PendingCreate['type'],
  ) => {
    event.dataTransfer.setData(NODE_DRAG_TYPE, type);
    event.dataTransfer.effectAllowed = 'copy';
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (!reactFlowInstance || !wrapperRef.current) return;
    const type = event.dataTransfer.getData(NODE_DRAG_TYPE) as PendingCreate['type'];
    if (!['SQL', 'DATASET', 'DATA_SERVICE'].includes(type)) return;
    const bounds = wrapperRef.current.getBoundingClientRect();
    const position = reactFlowInstance.project({
      x: event.clientX - bounds.left,
      y: event.clientY - bounds.top,
    });
    setPendingCreate({ type, position });
    setCreateName('');
    setCreateDirectoryId(undefined);
  };

  const createNode = async () => {
    if (!pendingCreate || !createName.trim() || creating) return;
    setCreating(true);
    try {
      const created = responseData(
        await createDevelopmentNode({
          name: createName.trim(),
          type: pendingCreate.type,
          projectId,
          directoryId: createDirectoryId,
        }),
        '创建 DAG 节点失败',
      );
      draftPositionRef.current.set(created.id, pendingCreate.position);
      setPendingCreate(undefined);
      setCreateName('');
      setDirty(true);
      await onResourcesChanged?.();
      message.success(`${typeLabel(created.type)}节点已创建`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建 DAG 节点失败');
    } finally {
      setCreating(false);
    }
  };

  const nodeTypes = useMemo(() => ({ development: DevelopmentDagNode }), []);

  return (
    <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
      <aside className="w-[190px] shrink-0 border-r border-[#e4e7ec] bg-[#fafafa] px-3 py-3">
        <div className="mb-3 text-[12px] font-semibold text-[#344054]">节点</div>
        {palette.map((section) => (
          <div key={section.title} className="mb-4">
            <div className="mb-1.5 px-1 text-[10px] font-medium uppercase tracking-wide text-[#98a2b3]">
              {section.title}
            </div>
            <div className="space-y-1.5">
              {section.items.map((item) => (
                <div
                  key={item.type}
                  draggable
                  onDragStart={(event) => onDragStart(event, item.type)}
                  className="flex cursor-grab items-center gap-2 rounded-lg border border-[#e4e7ec] bg-white px-2.5 py-2 text-[12px] text-[#344054] shadow-[0_1px_1px_rgba(16,24,40,.02)] transition hover:border-[#d0d5dd] hover:shadow-sm active:cursor-grabbing"
                >
                  <span className="text-[#667085]">{item.icon}</span>
                  <span>{item.label}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
        <div className="mt-5 rounded-lg bg-white px-2.5 py-2 text-[10px] leading-5 text-[#98a2b3]">
          拖到画布创建节点。双击处理节点进入编辑器。
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#e4e7ec] px-3">
          <div className="flex items-center gap-2 text-[12px] text-[#667085]">
            <span className="font-medium text-[#344054]">DAG 画布</span>
            <span>·</span>
            <span>{flowNodes.length} 个节点</span>
            <span>·</span>
            <span>{edges.length} 条依赖</span>
            {dirty ? <span className="text-[#f79009]">未保存</span> : null}
          </div>
          <Tooltip title={dirty ? '保存节点位置和依赖关系' : '当前 DAG 已保存'}>
            <Button
              size="small"
              type="primary"
              icon={<Save size={13} />}
              loading={saving}
              disabled={!dirty}
              onClick={() => void saveGraph()}
            >
              保存 DAG
            </Button>
          </Tooltip>
        </div>

        <div
          ref={wrapperRef}
          className="relative min-h-0 flex-1"
          onDragOver={(event) => {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
          }}
          onDrop={onDrop}
        >
          <Spin spinning={loading} wrapperClassName="block h-full [&_.ant-spin-container]:h-full">
            <ReactFlow
              nodes={flowNodes}
              edges={edges}
              nodeTypes={nodeTypes}
              onInit={setReactFlowInstance}
              onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange}
              onConnect={handleConnect}
              onNodeDoubleClick={(_, node) => {
                if (isDevelopmentTaskNodeType(node.data.resource.type)) {
                  onNodeOpen(node.id);
                }
              }}
              fitView
              fitViewOptions={{ padding: 0.2, maxZoom: 1 }}
              minZoom={0.35}
              maxZoom={1.5}
              deleteKeyCode={['Backspace', 'Delete']}
              defaultEdgeOptions={{ type: 'smoothstep' }}
              className="bg-[#fcfcfd]"
            >
              <Background gap={20} size={1} color="#eceef1" />
              <Controls showInteractive={false} />
              <MiniMap pannable zoomable nodeStrokeWidth={1} />
            </ReactFlow>
          </Spin>
        </div>
      </section>

      <Modal
        open={Boolean(pendingCreate)}
        title={`新建${pendingCreate ? typeLabel(pendingCreate.type) : ''}节点`}
        okText="创建"
        cancelText="取消"
        confirmLoading={creating}
        okButtonProps={{ disabled: !createName.trim() }}
        maskClosable={!creating}
        closable={!creating}
        onCancel={() => {
          if (!creating) setPendingCreate(undefined);
        }}
        onOk={() => void createNode()}
      >
        <div className="space-y-3 pt-2">
          <div>
            <div className="mb-1.5 text-[12px] text-[#475467]">名称</div>
            <Input
              autoFocus
              maxLength={128}
              value={createName}
              placeholder={pendingCreate?.type === 'DATASET' ? '例如：用户分析数据集' : pendingCreate?.type === 'DATA_SERVICE' ? '例如：用户查询 API' : '例如：用户查询 SQL'}
              onChange={(event) => setCreateName(event.target.value)}
              onPressEnter={() => void createNode()}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] text-[#475467]">开发目录</div>
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              className="w-full"
              placeholder="根目录"
              value={createDirectoryId}
              options={directories.map((directory) => ({ value: directory.id, label: directory.path }))}
              onChange={(value) => setCreateDirectoryId(value)}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
