import { Button, Typography } from 'antd';
import { Snail, Trash2, Upload } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';

import ResourcePicker, { type ResourcePickerValue } from '../../components/ResourcePicker';
import type { ResourceId } from '@/pages/resource-management/types';
import { FileSuffixIcon } from '@/pages/resource-management/components/FileSuffixIcon';
import { useEditorMode } from '../session/editorModeStore';
import {
  updateEditorSessionConfig,
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

/**
 * Python 任务 configJson 配置（与后端 PythonTaskConfig / parseResourceId 对应）。
 *
 * resourceId 保存为 string 以避免 JavaScript 大数精度丢失。
 * 内联 content 与资源引用的互斥在保存/发布时由 prepareDevelopmentTaskDefinition 按当前模式清理。
 */
interface PythonTaskConfigJson {
  resourceId?: string;
  resourceName?: string;
  resourceVersion?: number;
  checksum?: string;
  pythonExecutable?: string;
  scriptArgs?: string[];
  envVars?: Record<string, string>;
  timeoutSeconds?: number;
}

const parseConfigJson = (configJson: string): PythonTaskConfigJson => {
  try {
    return JSON.parse(configJson) as PythonTaskConfigJson;
  } catch {
    return {};
  }
};

const buildConfigJson = (config: PythonTaskConfigJson): string => {
  const cleaned: Record<string, unknown> = {};
  if (config.resourceId != null && config.resourceId !== '') cleaned.resourceId = config.resourceId;
  if (config.resourceName) cleaned.resourceName = config.resourceName;
  if (config.resourceVersion != null) cleaned.resourceVersion = config.resourceVersion;
  if (config.checksum) cleaned.checksum = config.checksum;
  if (config.pythonExecutable) cleaned.pythonExecutable = config.pythonExecutable;
  if (config.scriptArgs && config.scriptArgs.length > 0) cleaned.scriptArgs = config.scriptArgs;
  if (config.envVars && Object.keys(config.envVars).length > 0) cleaned.envVars = config.envVars;
  if (config.timeoutSeconds != null) cleaned.timeoutSeconds = config.timeoutSeconds;
  return JSON.stringify(cleaned);
};

type PythonEditMode = 'inline' | 'resource';

const extractSuffix = (name?: string): string | undefined => {
  if (!name) return undefined;
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : undefined;
};

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
  const config = useMemo(() => parseConfigJson(session.configJson || '{}'), [session.configJson]);

  const hasResource = config.resourceId != null && config.resourceId !== '' && config.resourceId !== '0';
  const [editMode, setEditMode] = useState<PythonEditMode>(() => hasResource ? 'resource' : 'inline');
  useEditorMode(node.id, editMode);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [position, setPosition] = useState<PythonEditorPosition>(() => ({
    lineNumber: session.viewState?.lineNumber || 1,
    column: session.viewState?.column || 1,
    selectionLength: 0,
  }));

  const updateConfig = useCallback(
    (partial: Partial<PythonTaskConfigJson>) => {
      const next = { ...config, ...partial };
      const json = buildConfigJson(next);
      updateEditorSessionConfig(node.id, json);
    },
    [config, node.id],
  );

  const handleResourceSelected = (value: ResourcePickerValue) => {
    updateConfig({
      resourceId: String(value.id),
      resourceName: value.name,
      resourceVersion: undefined,
      checksum: undefined,
    });
    setPickerOpen(false);
  };

  const handleClearResource = () => {
    updateConfig({
      resourceId: '',
      resourceName: undefined,
      resourceVersion: undefined,
      checksum: undefined,
    });
  };

  const handleSwitchToInline = () => setEditMode('inline');

  const handleSwitchToResource = () => setEditMode('resource');

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      {/* 模式切换标签 */}
      <div className="flex shrink-0 items-center gap-1 border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <button
          type="button"
          className={`rounded px-3 py-1 text-[12px] font-medium transition-colors ${
            editMode === 'inline'
              ? 'bg-white text-[#344054] shadow-sm'
              : 'text-[#667085] hover:text-[#344054]'
          }`}
          onClick={handleSwitchToInline}
        >
          内联脚本
        </button>
        <button
          type="button"
          className={`rounded px-3 py-1 text-[12px] font-medium transition-colors ${
            editMode === 'resource'
              ? 'bg-white text-[#344054] shadow-sm'
              : 'text-[#667085] hover:text-[#344054]'
          }`}
          onClick={handleSwitchToResource}
        >
          引用资源文件
        </button>
      </div>

      {/* 编辑器内容区 */}
      {editMode === 'inline' ? (
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
      ) : (
        <div className="flex-1 overflow-auto bg-white p-6">
          {/* 资源引用区域 */}
          <div className="mb-6">
            <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
              <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
              引用 Python 文件
            </Typography.Text>
            <Typography.Paragraph className="mb-3 text-[12px] text-[#98a2b3]">
              从资源管理中选择已上传的 Python 脚本文件。任务执行时将下载该文件到本地临时目录。
            </Typography.Paragraph>

            {hasResource ? (
              <div className="flex items-center gap-3 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-4 py-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#eff8ff]">
                  <FileSuffixIcon suffix={extractSuffix(config.resourceName)} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13px] font-medium text-[#344054]">
                    {config.resourceName || config.resourceId}
                  </div>
                  <div className="text-[11px] text-[#98a2b3]">
                    {config.checksum ? `SHA-256: ${config.checksum.substring(0, 16)}...` : '版本将在发布时锁定'}
                  </div>
                </div>
                <Button
                  size="small"
                  icon={<Upload size={14} />}
                  onClick={() => setPickerOpen(true)}
                />
                <Button
                  size="small"
                  danger
                  icon={<Trash2 size={14} />}
                  onClick={handleClearResource}
                />
              </div>
            ) : (
              <div
                className="flex cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-4 py-8 transition-colors hover:border-[#1570ef] hover:bg-[#eff8ff]"
                onClick={() => setPickerOpen(true)}
              >
                <Upload size={24} className="text-[#98a2b3]" />
                <div className="mt-2 text-[13px] text-[#475467]">点击选择 Python 文件</div>
                <div className="mt-1 text-[11px] text-[#98a2b3]">从资源管理中选择已上传的文件</div>
              </div>
            )}
          </div>


          <ResourcePicker
            open={pickerOpen}
            acceptSuffixes={['.py']}
            selectedId={config.resourceId as ResourceId | undefined}
            onCancel={() => setPickerOpen(false)}
            onConfirm={handleResourceSelected}
          />
        </div>
      )}

      {/* 底部状态栏 */}
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
          {editMode === 'inline' && position.selectionLength > 0 ? (
            <span>已选择 {position.selectionLength} 字符</span>
          ) : null}
          {editMode === 'inline' ? (
            <span>
              Ln {position.lineNumber}, Col {position.column}
            </span>
          ) : (
            <span>引用模式</span>
          )}
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
