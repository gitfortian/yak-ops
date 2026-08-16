import { Network } from 'lucide-react';

import type { DevelopmentResourceNode } from '../../types';

interface DataServiceNodeEditorProps {
  node: DevelopmentResourceNode;
}

export default function DataServiceNodeEditor({ node }: DataServiceNodeEditorProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-12 shrink-0 items-center border-b border-[#e4e7ec] px-4">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f5f5f6] text-[#475467]">
            <Network size={15} />
          </span>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-semibold text-[#161823]">{node.name}</div>
            <div className="text-[10px] text-[#98a2b3]">数据服务节点 · 独立资源</div>
          </div>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 items-center justify-center px-6">
        <div className="max-w-[560px] text-center">
          <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-[#f5f5f6] text-[#667085]">
            <Network size={19} />
          </div>
          <div className="text-[13px] font-semibold text-[#344054]">数据服务已经是独立节点</div>
          <div className="mt-2 text-[11px] leading-6 text-[#98a2b3]">
            数据开发只负责数据服务资源本身，不在这里维护节点连线或工作流关系。
            当前发布能力仍保留在原有 SQL 发布入口，后续再单独迁移到这个节点。
          </div>
        </div>
      </div>
    </div>
  );
}
