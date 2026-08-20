import { history } from '@umijs/max';
import { Button, Empty, Tooltip } from 'antd';
import { ArrowUpRight, GitBranch, LoaderCircle, RefreshCw } from 'lucide-react';
import { useMemo } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MarkerType,
  type Edge,
  type Node,
} from 'reactflow';
import 'reactflow/dist/style.css';

import LineageNode, {
  type LineageNodeData,
} from '@/pages/data-analysis/lineage/LineageNode';
import { buildLineageView } from '@/pages/data-analysis/lineage/graph-layout';
import type {
  LineageAssetType,
  LineageGraph,
} from '@/pages/data-analysis/lineage/types';

import type {
  DevelopmentId,
  DevelopmentSqlLineagePreview,
} from '../../../types';

interface SqlLineagePreviewPanelProps {
  nodeId: DevelopmentId;
  preview?: DevelopmentSqlLineagePreview;
  loading: boolean;
  onRefresh: () => void;
}

const nodeTypes = { lineage: LineageNode };
const visibleTypes = new Set<LineageAssetType>(['TABLE', 'SQL_TASK']);

const statusLabel: Record<DevelopmentSqlLineagePreview['status'], string> = {
  SUCCESS: '解析成功',
  PARTIAL: '部分解析',
  UNRESOLVED: '字段待解析',
  FAILED: '解析失败',
};

const statusClassName: Record<DevelopmentSqlLineagePreview['status'], string> = {
  SUCCESS: 'bg-[#ecfdf3] text-[#027a48]',
  PARTIAL: 'bg-[#fffaeb] text-[#b54708]',
  UNRESOLVED: 'bg-[#f2f4f7] text-[#667085]',
  FAILED: 'bg-[#fef3f2] text-[#b42318]',
};

const Metric = ({ label, value }: { label: string; value: number }) => (
  <span className="inline-flex items-center gap-1 text-[11px] text-[#8a8f99]">
    <span>{label}</span>
    <span className="font-medium tabular-nums text-[#475467]">{value}</span>
  </span>
);

const SqlLineagePreviewPanel = ({
  nodeId,
  preview,
  loading,
  onRefresh,
}: SqlLineagePreviewPanelProps) => {
  const graph: LineageGraph | undefined = preview?.graph;
  const view = useMemo(
    () => (graph ? buildLineageView(graph, 'BOTH', visibleTypes) : undefined),
    [graph],
  );

  const flowNodes = useMemo<Array<Node<LineageNodeData>>>(() => (
    view?.nodes.map(({ asset, position }) => ({
      id: asset.id,
      type: 'lineage',
      position,
      draggable: false,
      selectable: false,
      data: { asset, root: asset.id === graph?.root.id },
    })) || []
  ), [graph?.root.id, view?.nodes]);

  const flowEdges = useMemo<Edge[]>(() => (
    view?.relations.map((relation) => ({
      id: relation.id,
      source: relation.sourceAssetId,
      target: relation.targetAssetId,
      type: 'smoothstep',
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 14,
        height: 14,
        color: '#b9bec6',
      },
      style: {
        stroke: '#c9cdd3',
        strokeWidth: 1.15,
      },
    })) || []
  ), [view?.relations]);

  const productionAssetKey = `sql-task:data-development:${nodeId}`;
  const graphKey = graph
    ? graph.nodes.map((node) => node.id).join('|') || graph.root.id
    : 'empty';

  if (loading && !preview) {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd] text-[12px] text-[#667085]">
        <LoaderCircle size={16} className="mr-2 animate-spin" />
        正在解析当前 SQL 血缘
      </div>
    );
  }

  if (!preview) {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd]">
        <Empty
          image={(
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-[9px] bg-[#eef0f3] text-[#667085]">
              <GitBranch size={20} />
            </div>
          )}
          description={(
            <div>
              <div className="text-[13px] font-medium text-[#475467]">解析当前 SQL 查看血缘</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">预览只解析当前编辑内容，不保存、不发布、也不写入正式血缘。</div>
            </div>
          )}
        >
          <Button size="small" icon={<RefreshCw size={13} />} onClick={onRefresh}>
            解析血缘
          </Button>
        </Empty>
      </div>
    );
  }

  if (preview.status === 'FAILED') {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd] px-8">
        <Empty
          description={(
            <div className="max-w-[620px]">
              <div className="text-[13px] font-medium text-[#b42318]">SQL 血缘解析失败</div>
              <div className="mt-1 break-words text-[11px] leading-5 text-[#8a8f99]">
                {preview.parseError || '当前 SQL 暂时无法解析，请检查语法后重试。'}
              </div>
            </div>
          )}
        >
          <Button size="small" icon={<RefreshCw size={13} />} onClick={onRefresh}>
            再次解析
          </Button>
        </Empty>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-white">
      <div className="flex h-9 shrink-0 items-center gap-3 border-b border-[#eef0f2] px-3">
        <span
          className={[
            'shrink-0 rounded-[4px] px-1.5 py-0.5 text-[10px] font-medium',
            statusClassName[preview.status],
          ].join(' ')}
        >
          {statusLabel[preview.status]}
        </span>
        <Metric label="输入表" value={preview.inputTableCount} />
        <Metric label="输出表" value={preview.outputTableCount} />
        <Metric label="字段映射" value={preview.columnMappingCount} />
        <Metric label="未解析" value={preview.unresolvedColumnReferenceCount} />
        {preview.columnParseError ? (
          <span
            className="min-w-0 flex-1 truncate text-[11px] text-[#b54708]"
            title={preview.columnParseError}
          >
            字段级解析降级：{preview.columnParseError}
          </span>
        ) : (
          <span className="min-w-0 flex-1" />
        )}
        <Tooltip title="重新解析当前编辑器 SQL">
          <Button
            type="text"
            size="small"
            loading={loading}
            icon={<RefreshCw size={13} />}
            onClick={onRefresh}
          >
            再次解析
          </Button>
        </Tooltip>
        <Tooltip title="打开该 SQL 节点已发布并持久化的正式血缘">
          <Button
            type="text"
            size="small"
            icon={<ArrowUpRight size={13} />}
            onClick={() => history.push(
              `/data-analysis/lineage?assetKey=${encodeURIComponent(productionAssetKey)}`,
            )}
          >
            生产血缘
          </Button>
        </Tooltip>
      </div>

      <div className="relative min-h-0 flex-1 bg-[#fbfcfd]">
        <ReactFlow
          key={graphKey}
          nodes={flowNodes}
          edges={flowEdges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.28, minZoom: 0.62, maxZoom: 1.05 }}
          minZoom={0.35}
          maxZoom={1.35}
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={18} size={1} color="#e4e7eb" />
          <Controls showInteractive={false} position="bottom-right" />
        </ReactFlow>
      </div>
    </div>
  );
};

export default SqlLineagePreviewPanel;
