import { Snail } from 'lucide-react';
import { useState } from 'react';

import {
  updateEditorSessionContent,
  updateEditorSessionViewState,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';
import PythonMonacoEditor, {
  type PythonEditorPosition,
} from './PythonMonacoEditor';

const defaultPosition: PythonEditorPosition = {
  lineNumber: 1,
  column: 1,
  selectionLength: 0,
};

export const PythonEditor = ({
  node,
  onRunContent,
  running,
}: DevelopmentEditorContext) => {
  const session = useEditorSession(node.id, node.type);
  const [position, setPosition] = useState<PythonEditorPosition>(() => ({
    lineNumber: session.viewState?.lineNumber || 1,
    column: session.viewState?.column || 1,
    selectionLength: 0,
  }));

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      <div className="min-h-0 flex-1">
        <PythonMonacoEditor
          id={String(node.id)}
          value={session.content}
          initialViewState={session.viewState}
          onChange={(value) => updateEditorSessionContent(node.id, value)}
          onRunScript={onRunContent}
          running={running}
          onPositionChange={setPosition}
          onViewStateChange={(viewState) =>
            updateEditorSessionViewState(node.id, viewState)
          }
        />
      </div>

      <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
        <div className="flex min-w-0 items-center gap-3">
          <span className="font-medium text-[#667085]">Python</span>
          <span className="truncate">{node.name}</span>
          {session.dirty ? (
            <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
              未保存
            </span>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {position.selectionLength > 0 ? (
            <span>已选择 {position.selectionLength} 字符</span>
          ) : null}
          <span>
            Ln {position.lineNumber}, Col {position.column}
          </span>
        </div>
      </div>
    </div>
  );
};

export const PythonRunConfig = ({ node }: DevelopmentEditorContext) => (
  <div className="text-[12px] leading-6 text-[#667085]">
    <div className="font-medium text-[#344054]">Python 运行配置</div>
    <div className="mt-2">当前节点：{node.name}</div>
    <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
      <div>默认使用 <code className="rounded bg-[#f5f5f6] px-1 py-0.5 font-mono text-[10px]">PYTHON_HOME</code> 环境变量指定的解释器，未设置时依赖系统 PATH 中的 python 命令。</div>
      <div className="mt-2">脚本参数、环境变量和超时时间可通过 configJson 配置，后续将在本面板提供可视化编辑。</div>
    </div>
  </div>
);

export const PythonRunResult = ({ result }: DevelopmentEditorRunResultContext) => {
  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#475467]">Python 运行结果</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            点击顶部运行按钮或按 Ctrl+Shift+Enter 执行当前 Python 脚本
          </div>
        </div>
      </div>
    );
  }

  if (result.status === 'RUNNING') {
    return (
      <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
        <Snail size={16} className="mr-2 animate-spin" />
        正在执行 Python 脚本…
      </div>
    );
  }

  if (result.status !== 'SUCCESS') {
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div className="max-w-[680px]">
          <div className="text-[13px] font-medium text-[#b42318]">
            {result.status === 'CANCELLED'
              ? 'Python 执行已取消'
              : result.status === 'TIMEOUT'
                ? 'Python 执行超时'
                : 'Python 执行失败'}
          </div>
          <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
            {result.message || '未返回更多错误信息'}
          </div>
          {result.output?.stderr && (
            <pre className="mt-3 max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 text-left font-mono text-[11px] text-[#344054]">
              {String(result.output.stderr)}
            </pre>
          )}
          <div className="mt-2 text-[10px] text-[#98a2b3]">
            耗时 {result.durationMs} ms
          </div>
        </div>
      </div>
    );
  }

  const output = result.output || {};
  const stdout = output.stdout ? String(output.stdout) : '';
  const stderr = output.stderr ? String(output.stderr) : '';
  const exitCode = output.exitCode;
  const pythonExecutable = output.pythonExecutable ? String(output.pythonExecutable) : '';

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <span className="text-[12px] font-medium text-[#344054]">Python 执行完成</span>
        <span className="text-[10px] text-[#98a2b3]">退出码：{exitCode ?? '—'} · 耗时 {result.durationMs} ms</span>
      </div>
      {pythonExecutable && (
        <div className="flex shrink-0 items-center border-b border-[#eef0f2] bg-[#f5f6f8] px-3 py-1">
          <span className="text-[10px] text-[#98a2b3]">解释器：{pythonExecutable}</span>
        </div>
      )}
      <div className="min-h-0 flex-1 overflow-auto p-3">
        {stdout && (
          <div className="mb-3">
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stdout</div>
            <pre className="max-h-[320px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
              {stdout}
            </pre>
          </div>
        )}
        {stderr && (
          <div>
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stderr</div>
            <pre className="max-h-[160px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#fef3f2] p-3 font-mono text-[11px] leading-5 text-[#b42318]">
              {stderr}
            </pre>
          </div>
        )}
        {!stdout && !stderr && (
          <div className="text-[12px] text-[#98a2b3]">脚本执行完毕，无输出</div>
        )}
      </div>
    </div>
  );
};
