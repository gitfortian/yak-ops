import type { DevelopmentEditorContext } from '../../editors/types';
import type { DevelopmentSqlLineageColumnMapping } from '../../types';

const demoMappings: DevelopmentSqlLineageColumnMapping[] = [
  {
    sourceTable: 'ods_order',
    sourceColumn: 'total_amount',
    targetColumn: 'total_amount',
    mappingKind: 'AGGREGATION',
    expression: 'SUM(b.total_amount)',
    outputOrdinal: 1,
    sourceOrdinal: 1,
  },
];

const formatMappingKind = (kind: DevelopmentSqlLineageColumnMapping['mappingKind']) => {
  if (kind === 'AGGREGATION') return '聚合计算';
  if (kind === 'TRANSFORMATION') return '转换';
  return '直接映射';
};

const SqlLineagePanel = ({ node }: DevelopmentEditorContext) => {
  return (
    <div className="space-y-4 text-[12px] text-[#344054]">
      <div>
        <div className="font-medium">字段血缘增强预览</div>
        <div className="mt-1 text-[#98a2b3]">
          {node.name} 的字段映射展示将结合 SQL 解析结果和数据源元数据。
        </div>
      </div>
      <div className="space-y-2">
        {demoMappings.map((item) => (
          <div key={`${item.sourceTable}-${item.sourceColumn}`} className="rounded-[6px] border border-[#e5e7eb] p-3">
            <div className="flex justify-between">
              <span>{item.sourceTable}.{item.sourceColumn}</span>
              <span className="text-[#667085]">{formatMappingKind(item.mappingKind)}</span>
            </div>
            <div className="my-2 text-[#98a2b3]">↓</div>
            <div className="font-medium">目标字段：{item.targetColumn}</div>
            {item.expression ? <div className="mt-2 rounded bg-[#f8fafc] px-2 py-1 font-mono text-[11px]">{item.expression}</div> : null}
          </div>
        ))}
      </div>
    </div>
  );
};

export default SqlLineagePanel;
