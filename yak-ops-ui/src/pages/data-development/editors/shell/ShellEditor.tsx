import { TerminalSquare } from 'lucide-react';

import type { DevelopmentEditorContext } from '../types';

export const ShellEditor = ({ node }: DevelopmentEditorContext) => (
  <div className="flex h-full min-h-0 items-center justify-center overflow-auto bg-white">
    <div className="text-center">
      <div className="inline-flex h-10 w-10 items-center justify-center rounded-lg bg-[#f5f5f6] text-[#6172f3]">
        <TerminalSquare size={18} strokeWidth={1.8} />
      </div>
      <div className="mt-3 text-[15px] font-semibold text-[#344054]">
        Shell 编辑器区域
      </div>
      <div className="mt-1 text-[12px] text-[#98a2b3]">
        当前节点：{node.name}
      </div>
      <div className="mt-3 text-[12px] text-[#b0b7c3]">
        Shell 编辑器内容将在下一阶段接入
      </div>
    </div>
  </div>
);

export const ShellRunConfig = ({ node }: DevelopmentEditorContext) => (
  <div className="text-[12px] leading-6 text-[#667085]">
    <div className="font-medium text-[#344054]">Shell 运行配置</div>
    <div className="mt-2">当前节点：{node.name}</div>
    <div>执行环境、参数和环境变量将在后续阶段接入。</div>
  </div>
);

export const ShellRunResult = ({ node }: DevelopmentEditorContext) => (
  <div className="text-center">
    <div className="text-[13px] font-medium text-[#475467]">
      Shell 运行结果区域
    </div>
    <div className="mt-1 text-[11px] text-[#98a2b3]">
      {node.name} 的 stdout、stderr 和执行日志将在后续阶段接入
    </div>
  </div>
);
