import { FunctionSquare, KeyRound, Table2 } from 'lucide-react';
import { Handle, type NodeProps, Position } from 'reactflow';

export interface ColumnLineageField {
  name: string;
  transformed: boolean;
}

export interface ColumnLineageNodeData {
  tableName: string;
  role: 'source' | 'target';
  fields: ColumnLineageField[];
  activeFields: Set<string>;
  onFieldClick: (tableName: string, fieldName: string) => void;
  onFieldHover: (tableName?: string, fieldName?: string) => void;
}

export const columnHandleId = (fieldName: string) => `column:${fieldName}`;

export default function ColumnLineageNode({ data }: NodeProps<ColumnLineageNodeData>) {
  return (
    <div className="w-[250px] overflow-hidden rounded-[10px] border border-[#dfe3e8] bg-white shadow-[0_8px_24px_rgba(16,24,40,.08)]">
      <div className="flex h-11 items-center gap-2 border-b border-[#e8eaed] bg-[#f7f8fa] px-3">
        <Table2 size={16} className="text-[#475467]" />
        <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-[#1d2939]" title={data.tableName}>
          {data.tableName}
        </span>
        <span className="rounded bg-white px-1.5 py-0.5 text-[9px] text-[#667085] ring-1 ring-[#e4e7ec]">
          {data.role === 'source' ? '源表' : '目标表'}
        </span>
      </div>
      <div className="py-1">
        {data.fields.map((field, index) => {
          const active = data.activeFields.has(field.name);
          return (
            <button
              key={field.name}
              type="button"
              className={[
                'relative flex h-9 w-full items-center gap-2 border-0 px-3 text-left transition-colors',
                active ? 'bg-[#fff1f4]' : 'bg-white hover:bg-[#f7f8fa]',
              ].join(' ')}
              onClick={() => data.onFieldClick(data.tableName, field.name)}
              onMouseEnter={() => data.onFieldHover(data.tableName, field.name)}
              onMouseLeave={() => data.onFieldHover()}
            >
              {index === 0 ? <KeyRound size={13} className="text-[#e0a400]" /> : <span className="w-[13px]" />}
              <span className="min-w-0 flex-1 truncate text-[12px] text-[#344054]">{field.name}</span>
              {field.transformed ? <FunctionSquare size={13} className="text-[#7f56d9]" /> : null}
              {data.role === 'target' ? (
                <Handle
                  type="target"
                  position={Position.Left}
                  id={columnHandleId(field.name)}
                  className="!h-2 !w-2 !border-[#12b76a] !bg-white"
                />
              ) : (
                <Handle
                  type="source"
                  position={Position.Right}
                  id={columnHandleId(field.name)}
                  className="!h-2 !w-2 !border-[#f04473] !bg-white"
                />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
