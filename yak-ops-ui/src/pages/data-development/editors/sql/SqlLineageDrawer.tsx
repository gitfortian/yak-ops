import { Alert, Drawer, Switch, message } from 'antd';
import { X } from 'lucide-react';
import ReactFlow, {
  Background,
  type Edge,
  type Node,
  Position,
  type OnNodesChange,
  type OnEdgesChange,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { parseSqlLineage, validateSql } from '../../service';
import type {
  SqlLineageColumnEdge,
  SqlLineageResult,
  SqlLineageTableEdge,
  SqlLineageTableNode,
  SqlValidationError,
  SqlValidationResult,
} from '../../types';

interface SqlLineageDrawerProps {
  open: boolean;
  sql: string;
  /** Optional data-source ID for column metadata lookup (enables SELECT * expansion and column validation). */
  datasourceId?: number | null;
  onClose: () => void;
}

const SOURCE_NODE_STYLE =
  'rounded-[6px] border border-[#b9d6fb] bg-[#eef5ff] px-4 py-2.5 text-center shadow-sm';
const TARGET_NODE_STYLE =
  'rounded-[6px] border border-[#a3e4c1] bg-[#edf8f2] px-4 py-2.5 text-center shadow-sm';

const TableNodeComponent = ({ data }: { data: { label: string; nodeType: string } }) => (
  <div className={data.nodeType === 'SOURCE' ? SOURCE_NODE_STYLE : TARGET_NODE_STYLE}>
    <div className="text-[11px] text-[#98a2b3]">{data.nodeType === 'SOURCE' ? '源表' : '目标表'}</div>
    <div className="mt-0.5 text-[13px] font-semibold text-[#344054]">{data.label}</div>
  </div>
);

const nodeTypes = { tableNode: TableNodeComponent };

const noop: OnNodesChange = () => {};
const noopEdges: OnEdgesChange = () => {};

const SqlLineageDrawer = ({ open, sql, datasourceId, onClose }: SqlLineageDrawerProps) => {
  const [results, setResults] = useState<SqlLineageResult[]>([]);
  const [validationResults, setValidationResults] = useState<SqlValidationResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [showColumnLineage, setShowColumnLineage] = useState(false);

  const fetchLineage = useCallback(async () => {
    if (!sql.trim()) {
      message.warning('当前编辑器没有 SQL 内容');
      return;
    }
    setLoading(true);
    try {
      // Step 1: Validate SQL first
      const validateRes = await validateSql(sql, datasourceId);
      const valResults = validateRes.data || [];
      setValidationResults(valResults);

      // Check if all statements are valid
      const allValid = valResults.length > 0 && valResults.every((r) => r.valid);

      if (!allValid) {
        // Has validation errors — still proceed with lineage for valid statements
        // but the errors will be shown prominently
      }

      // Step 2: Parse lineage (even if validation has warnings, still try)
      const res = await parseSqlLineage(sql, datasourceId);
      setResults(res.data || []);
    } catch {
      message.error('血缘解析请求失败');
      setResults([]);
      setValidationResults([]);
    } finally {
      setLoading(false);
    }
  }, [sql, datasourceId]);

  useEffect(() => {
    if (open) fetchLineage();
  }, [open, fetchLineage]);

  // Merge validation errors across all statements
  const allErrors = useMemo(() => {
    const errors: SqlValidationError[] = [];
    for (const r of validationResults) {
      errors.push(...r.errors);
    }
    return errors;
  }, [validationResults]);

  const hasValidationErrors = allErrors.some((e) => e.severity === 'ERROR');

  // Merge all per-statement results into a unified view for display
  const merged = useMemo(() => {
    if (results.length === 0) return null;
    const tables: SqlLineageTableNode[] = [];
    const tableEdges: SqlLineageTableEdge[] = [];
    const columnEdges: SqlLineageColumnEdge[] = [];
    const warnings: string[] = [];
    const seenTables = new Set<string>();
    const seenTableEdges = new Set<string>();

    for (const r of results) {
      for (const t of r.tables) {
        const key = `${t.name}:${t.type}`;
        if (!seenTables.has(key)) {
          seenTables.add(key);
          tables.push(t);
        }
      }
      for (const e of r.tableEdges) {
        const key = `${e.source}->${e.target}`;
        if (!seenTableEdges.has(key)) {
          seenTableEdges.add(key);
          tableEdges.push(e);
        }
      }
      columnEdges.push(...r.columnEdges);
      if (r.warnings) warnings.push(...r.warnings);
    }
    return { tables, tableEdges, columnEdges, warnings };
  }, [results]);

  // Build flow nodes/edges directly — no internal state management needed
  // since we don't need interactive drag/select/connect.
  const flowData = useMemo(() => {
    if (!merged) return { nodes: [] as Node[], edges: [] as Edge[] };
    return buildFlowData(merged.tables, merged.tableEdges, merged.columnEdges, showColumnLineage);
  }, [merged, showColumnLineage]);

  const titleElement = (
    <div className="flex items-center justify-between">
      <span>SQL 血缘分析</span>
      <button
        type="button"
        onClick={onClose}
        className="flex h-7 w-7 items-center justify-center rounded-[4px] text-[#667085] hover:bg-[#f5f5f6] hover:text-[#344054]"
      >
        <X size={16} />
      </button>
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={titleElement}
      width="min(800px, 80vw)"
      closable={false}
      styles={{ body: { padding: 0 } }}
      destroyOnClose
    >
      <div className="flex h-full flex-col">
        {/* Toolbar */}
        <div className="flex shrink-0 items-center justify-between border-b border-[#eaecf0] px-4 py-2">
          <div className="flex items-center gap-3">
            <span className="text-[12px] text-[#667085]">列级血缘</span>
            <Switch
              size="small"
              checked={showColumnLineage}
              onChange={setShowColumnLineage}
              disabled={!merged || merged.columnEdges.length === 0}
            />
          </div>
          {merged && (
            <div className="flex items-center gap-3 text-[11px] text-[#98a2b3]">
              <span>{merged.tables.length} 表</span>
              <span>{merged.tableEdges.length} 关系</span>
              {merged.columnEdges.length > 0 && (
                <span>{merged.columnEdges.length} 列映射</span>
              )}
            </div>
          )}
        </div>

        {/* Validation Errors */}
        {allErrors.length > 0 && (
          <div className="px-4 pt-3">
            <Alert
              type={hasValidationErrors ? 'error' : 'warning'}
              showIcon
              message={
                hasValidationErrors
                  ? 'SQL 校验未通过，血缘结果可能不完整'
                  : 'SQL 校验存在警告'
              }
              description={
                <ul className="m-0 mt-1 list-none pl-0 text-[12px]">
                  {allErrors.map((e, i) => (
                    <li key={i} className="flex items-start gap-1.5 py-0.5">
                        <span className={`shrink-0 rounded px-1 py-0.5 text-[10px] font-medium ${
                          e.severity === 'ERROR'
                            ? 'bg-red-50 text-red-600'
                            : 'bg-amber-50 text-amber-600'
                        }`}>
                          {e.type.replace(/_/g, ' ')}
                        </span>
                        <span>
                          {e.message}
                          {e.line != null && e.column != null && (
                            <span className="ml-1 text-[10px] text-[#98a2b3]">
                              (行 {e.line}, 列 {e.column})
                            </span>
                          )}
                        </span>
                      </li>
                  ))}
                </ul>
              }
            />
          </div>
        )}

        {/* Warnings */}
        {merged?.warnings && merged.warnings.length > 0 && (
          <div className="px-4 pt-3">
            <Alert
              type="warning"
              showIcon
              message={
                <ul className="m-0 list-none pl-0 text-[12px]">
                  {merged.warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              }
            />
          </div>
        )}

        {/* Empty state */}
        {!loading && merged && merged.tables.length === 0 && (
          <div className="flex flex-1 items-center justify-center text-center text-[13px] text-[#98a2b3]">
            <div>
              <div className="text-[15px] font-medium text-[#667085]">未检测到血缘关系</div>
              <div className="mt-1 text-[12px]">
                当前 SQL 可能是纯查询语句或无法识别表级血缘
              </div>
            </div>
          </div>
        )}

        {/* Flow canvas */}
        {!loading && merged && merged.tables.length > 0 && (
          <div className="min-h-0 flex-1">
            <ReactFlow
              nodes={flowData.nodes}
              edges={flowData.edges}
              onNodesChange={noop}
              onEdgesChange={noopEdges}
              nodeTypes={nodeTypes}
              fitView
              fitViewOptions={{ padding: 0.3 }}
              minZoom={0.3}
              maxZoom={1.5}
              nodesDraggable
              nodesConnectable={false}
              elementsSelectable={false}
              proOptions={{ hideAttribution: true }}
            >
              <Background color="#e5e7eb" gap={16} size={1} />
            </ReactFlow>
          </div>
        )}

        {/* Loading */}
        {loading && (
          <div className="flex flex-1 items-center justify-center text-[12px] text-[#667085]">
            正在解析血缘关系…
          </div>
        )}
      </div>
    </Drawer>
  );
};

// ---- Helpers ----

function buildFlowData(
  tableNodes: SqlLineageTableNode[],
  tableEdges: SqlLineageTableEdge[],
  columnEdges: SqlLineageColumnEdge[],
  showColumnLineage: boolean,
): { nodes: Node[]; edges: Edge[] } {
  const sources = tableNodes.filter((t) => t.type === 'SOURCE');
  const targets = tableNodes.filter((t) => t.type === 'TARGET');

  const hasTargets = targets.length > 0;

  // Layout: sources on left, targets on right
  const nodeGapY = 100;
  const sourceX = hasTargets ? 80 : 250;
  const targetX = hasTargets ? 500 : 250;

  const nodes: Node[] = [];

  sources.forEach((t, i) => {
    nodes.push({
      id: t.name,
      type: 'tableNode',
      position: { x: sourceX, y: 60 + i * nodeGapY },
      data: { label: t.name, nodeType: 'SOURCE' },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    });
  });

  targets.forEach((t, i) => {
    nodes.push({
      id: t.name,
      type: 'tableNode',
      position: { x: targetX, y: 60 + i * nodeGapY },
      data: { label: t.name, nodeType: 'TARGET' },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    });
  });

  const edges: Edge[] = [];

  // Table-level edges
  const addedEdgeKeys = new Set<string>();

  tableEdges.forEach((e) => {
    const key = `${e.source}->${e.target}`;
    if (addedEdgeKeys.has(key)) return;
    addedEdgeKeys.add(key);

    // Check if there are column edges for this table pair
    const relatedColumns = columnEdges.filter(
      (ce) => ce.sourceTable === e.source && ce.targetTable === e.target,
    );

    edges.push({
      id: `table-${key}`,
      source: e.source,
      target: e.target,
      animated: relatedColumns.length > 0,
      style: { stroke: '#667085', strokeWidth: 1.5 },
      label: showColumnLineage && relatedColumns.length > 0
        ? `${relatedColumns.length} 列`
        : undefined,
      labelStyle: { fontSize: 10, fill: '#98a2b3' },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.8 },
    });
  });

  // Column-level edges (optional)
  if (showColumnLineage && columnEdges.length > 0) {
    columnEdges.forEach((ce, i) => {
      const key = `col-${ce.sourceTable}.${ce.sourceColumn}->${ce.targetTable}.${ce.targetColumn}`;
      if (addedEdgeKeys.has(key)) return;
      addedEdgeKeys.add(key);

      edges.push({
        id: key,
        source: ce.sourceTable,
        target: ce.targetTable,
        style: { stroke: '#3972cf', strokeWidth: 1, strokeDasharray: '4 3' },
        label: `${ce.sourceColumn} → ${ce.targetColumn}`,
        labelStyle: { fontSize: 9, fill: '#3972cf' },
        labelBgStyle: { fill: '#fff', fillOpacity: 0.9 },
      });
    });
  }

  return { nodes, edges };
}

export default SqlLineageDrawer;
