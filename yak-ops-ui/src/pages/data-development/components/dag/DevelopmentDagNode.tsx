import { Button, Tooltip } from 'antd';
import {
  Braces,
  Database,
  Network,
  SquareTerminal,
  Trash2,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Handle, Position, type NodeProps } from 'reactflow';

import { getNodeCategory } from '../../node-model';
import type { DevelopmentResourceNode } from '../../types';

export interface DevelopmentDagNodeData {
  resource: DevelopmentResourceNode;
  onDelete: (nodeId: string) => void;
}

const nodeIcon = (type: DevelopmentResourceNode['type']): ReactNode => {
  if (type === 'DATASET') return <Database size={17} strokeWidth={1.8} />;
  if (type === 'DATA_SERVICE') return <Network size={17} strokeWidth={1.8} />;
  if (type === 'SHELL') return <SquareTerminal size={17} strokeWidth={1.8} />;
  return <Braces size={17} strokeWidth={1.8} />;
};

const nodeTypeLabel = (type: DevelopmentResourceNode['type']) => {
  if (type === 'DATASET') return '数据集';
  if (type === 'DATA_SERVICE') return '数据服务';
  if (type === 'SHELL') return 'Shell';
  if (type === 'PYTHON') return 'Python';
  if (type === 'HTTP') return 'HTTP';
  return 'SQL';
};

const statusLabel = (resource: DevelopmentResourceNode) => {
  if (resource.type === 'DATASET') return '未配置';
  if (resource.type === 'DATA_SERVICE') return '未发布';
  return resource.configured ? '已配置' : '未配置';
};

const canReceive = (type: DevelopmentResourceNode['type']) =>
  type === 'SQL' || type === 'DATASET' || type === 'DATA_SERVICE';

const canEmit = (type: DevelopmentResourceNode['type']) =>
  type === 'SQL' || type === 'DATASET';

export default function DevelopmentDagNode({
  data,
  selected,
}: NodeProps<DevelopmentDagNodeData>) {
  const { resource } = data;
  const category = getNodeCategory(resource.type);

  return (
    <div className="group relative w-[242px]">
      {canReceive(resource.type) ? (
        <Handle
          type="target"
          position={Position.Left}
          className="!h-2.5 !w-2.5 !border-2 !border-white !bg-[#98a2b3]"
        />
      ) : null}

      <div
        className={[
          'rounded-xl border bg-white px-3.5 py-3 shadow-[0_1px_2px_rgba(16,24,40,.05)]',
          'transition-[border-color,box-shadow] duration-150',
          selected
            ? 'border-[#fe2c55] shadow-[0_0_0_2px_rgba(254,44,85,.06)]'
            : 'border-[#e4e7ec] group-hover:border-[#d0d5dd] group-hover:shadow-[0_5px_16px_rgba(16,24,40,.08)]',
        ].join(' ')}
      >
        <div className="flex items-start gap-2.5">
          <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#f5f5f6] text-[#475467]">
            {nodeIcon(resource.type)}
          </div>

          <div className="min-w-0 flex-1">
            <div className="truncate text-[13px] font-semibold leading-5 text-[#161823]">
              {resource.name}
            </div>
            <div className="mt-0.5 flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
              <span>{nodeTypeLabel(resource.type)}</span>
              <span>·</span>
              <span>{category === 'OUTPUT' ? '数据输出' : '数据处理'}</span>
            </div>
          </div>

          <Tooltip title="删除节点">
            <Button
              type="text"
              size="small"
              aria-label={`删除 ${resource.name}`}
              icon={<Trash2 size={13} strokeWidth={1.8} />}
              onClick={(event) => {
                event.stopPropagation();
                data.onDelete(resource.id);
              }}
              className="nodrag !-mr-1 !-mt-1 !hidden !h-7 !w-7 !items-center !justify-center !p-0 !text-[#98a2b3] group-hover:!flex hover:!bg-[#f5f5f6] hover:!text-[#475467]"
            />
          </Tooltip>
        </div>

        <div className="mt-3 flex items-center justify-between border-t border-[#f0f1f3] pt-2 text-[10px]">
          <span className="text-[#98a2b3]">{resource.type}</span>
          <span className="text-[#667085]">{statusLabel(resource)}</span>
        </div>
      </div>

      {canEmit(resource.type) ? (
        <Handle
          type="source"
          position={Position.Right}
          className="!h-2.5 !w-2.5 !border-2 !border-white !bg-[#667085]"
        />
      ) : null}
    </div>
  );
}
