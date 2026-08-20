import { Button, Input, Typography } from 'antd';
import { Plus, Trash2, Upload } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';

import {
  updateEditorSessionConfig,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';
import ResourcePicker, { type ResourcePickerValue } from '../../components/ResourcePicker';
import type { ResourceId } from '@/pages/resource-management/types';
import { FileSuffixIcon } from '@/pages/resource-management/components/FileSuffixIcon';

/**
 * Java 任务配置（与后端 JavaTaskConfig record 对应）。
 * 通过 configJson 承载，content 字段留空。
 *
 * resources[].id 保存为 string 以避免 JavaScript 大数精度丢失
 * （雪花 ID 19 位，超过 Number.MAX_SAFE_INTEGER 16 位）。
 */
interface ResourceEntry {
  id: string;
  name: string;
  version?: number;
}

interface JavaTaskConfigJson {
  /** New multi-resource format */
  resources?: ResourceEntry[];
  /** Legacy single-resource format (backward compat) */
  resourceId?: string;
  resourceName?: string;
  resourceVersion?: number;
  checksum?: string;
  mainClass?: string;
  jvmArgs?: string;
  programArgs?: string;
  envVars?: Record<string, string>;
  timeoutSeconds?: number;
}

const parseConfigJson = (configJson: string): JavaTaskConfigJson => {
  try {
    return JSON.parse(configJson) as JavaTaskConfigJson;
  } catch {
    return {};
  }
};

const buildConfigJson = (config: JavaTaskConfigJson): string => {
  const cleaned: Record<string, unknown> = {};
  if (config.resources && config.resources.length > 0) {
    cleaned.resources = config.resources.map((r) => {
      const entry: Record<string, unknown> = { resourceId: r.id, name: r.name };
      if (r.version != null) entry.resourceVersion = r.version;
      return entry;
    });
  }
  if (config.mainClass) cleaned.mainClass = config.mainClass;
  if (config.jvmArgs) cleaned.jvmArgs = config.jvmArgs;
  if (config.programArgs) cleaned.programArgs = config.programArgs;
  if (config.envVars && Object.keys(config.envVars).length > 0) cleaned.envVars = config.envVars;
  if (config.timeoutSeconds != null) cleaned.timeoutSeconds = config.timeoutSeconds;
  return JSON.stringify(cleaned);
};

const extractSuffix = (name?: string): string | undefined => {
  if (!name) return undefined;
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : undefined;
};

/** Normalise legacy single-resource config into the resources array. */
const normaliseResources = (config: JavaTaskConfigJson): ResourceEntry[] => {
  if (config.resources && config.resources.length > 0) return config.resources;
  if (config.resourceId && config.resourceId !== '' && config.resourceId !== '0') {
    return [{ id: config.resourceId, name: config.resourceName || config.resourceId, version: config.resourceVersion }];
  }
  return [];
};

export const JavaEditor = ({ node }: DevelopmentEditorContext) => {
  const session = useEditorSession(node.id, node.type);
  const config = useMemo(() => parseConfigJson(session.configJson || '{}'), [session.configJson]);
  const resources = useMemo(() => normaliseResources(config), [config]);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [mainClass, setMainClass] = useState(config.mainClass || '');
  const [jvmArgs, setJvmArgs] = useState(config.jvmArgs || '');
  const [programArgs, setProgramArgs] = useState(config.programArgs || '');

  const updateConfig = useCallback(
    (partial: Partial<JavaTaskConfigJson>) => {
      const next = { ...config, ...partial };
      const json = buildConfigJson(next);
      updateEditorSessionConfig(node.id, json);
    },
    [config, node.id],
  );

  const handleResourceSelected = (value: ResourcePickerValue) => {
    const exists = resources.some((r) => r.id === String(value.id));
    if (exists) {
      setPickerOpen(false);
      return;
    }
    const newResources = [
      ...resources,
      { id: String(value.id), name: value.name, version: value.version },
    ];
    updateConfig({ resources: newResources });
    setPickerOpen(false);
  };

  const handleRemoveResource = (index: number) => {
    const newResources = resources.filter((_, i) => i !== index);
    updateConfig({ resources: newResources });
  };

  const handleMainClassBlur = () => {
    if (mainClass !== (config.mainClass || '')) {
      updateConfig({ mainClass: mainClass || undefined });
    }
  };

  const handleJvmArgsBlur = () => {
    if (jvmArgs !== (config.jvmArgs || '')) {
      updateConfig({ jvmArgs: jvmArgs || undefined });
    }
  };

  const handleProgramArgsBlur = () => {
    if (programArgs !== (config.programArgs || '')) {
      updateConfig({ programArgs: programArgs || undefined });
    }
  };

  const hasResources = resources.length > 0;
  const isMultiJar = resources.length > 1;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-auto bg-white p-6">
      {/* 资源引用区域 */}
      <div className="mb-6">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          引用 JAR 文件
        </Typography.Text>
        <Typography.Paragraph className="mb-3 text-[12px] text-[#98a2b3]">
          从资源管理中选择已上传的 JAR 文件。支持多个 JAR，多个时将使用 classpath 模式运行。
        </Typography.Paragraph>

        {/* JAR 列表 */}
        {hasResources && (
          <div className="mb-3 space-y-2">
            {resources.map((res, index) => (
              <div
                key={res.id + '-' + index}
                className="flex items-center gap-3 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-4 py-3"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#eff8ff]">
                  <FileSuffixIcon suffix={extractSuffix(res.name)} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13px] font-medium text-[#344054]">
                    {res.name}
                  </div>
                  <div className="text-[11px] text-[#98a2b3]">
                    {res.version ? `v${res.version}` : '版本将在发布时锁定'}
                  </div>
                </div>
                <Button
                  size="small"
                  danger
                  icon={<Trash2 size={14} />}
                  onClick={() => handleRemoveResource(index)}
                />
              </div>
            ))}
          </div>
        )}

        {/* 添加 JAR 按钮 */}
        <Button
          type="dashed"
          icon={<Plus size={14} />}
          className="w-full"
          onClick={() => setPickerOpen(true)}
        >
          添加 JAR 文件
        </Button>
      </div>

      {/* 主类配置 */}
      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          主类（Main Class）
          {isMultiJar && <span className="ml-1 text-[rgba(254,44,85,1)]">*</span>}
        </Typography.Text>
        <Input
          value={mainClass}
          placeholder={
            isMultiJar
              ? 'com.example.Main（多 JAR 模式必填）'
              : 'com.example.Main（可选，不填时使用 java -jar 默认入口）'
          }
          onChange={(e) => setMainClass(e.target.value)}
          onBlur={handleMainClassBlur}
        />
      </div>

      {/* JVM 参数 */}
      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          JVM 参数
        </Typography.Text>
        <Input
          value={jvmArgs}
          placeholder="-Xmx512m -Dfile.encoding=UTF-8"
          onChange={(e) => setJvmArgs(e.target.value)}
          onBlur={handleJvmArgsBlur}
        />
      </div>

      {/* 程序参数 */}
      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          程序参数
        </Typography.Text>
        <Input
          value={programArgs}
          placeholder="--env production --config /path/to/config.yaml"
          onChange={(e) => setProgramArgs(e.target.value)}
          onBlur={handleProgramArgsBlur}
        />
      </div>

      <ResourcePicker
        open={pickerOpen}
        acceptSuffixes={['.jar']}
        onCancel={() => setPickerOpen(false)}
        onConfirm={handleResourceSelected}
      />
    </div>
  );
};

export const JavaRunConfig = ({ node }: DevelopmentEditorContext) => (
  <div className="text-[12px] leading-6 text-[#667085]">
    <div className="font-medium text-[#344054]">Java 运行配置</div>
    <div className="mt-2">当前节点：{node.name}</div>
    <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
      <div>默认使用 <code className="rounded bg-[#f5f5f6] px-1 font-mono text-[10px]">JAVA_HOME</code> 环境变量指定的 JDK，未设置时依赖系统 PATH 中的 java 命令。</div>
      <div className="mt-2">环境变量和超时时间可通过 configJson 配置，后续将在本面板提供可视化编辑。</div>
    </div>
  </div>
);

export const JavaRunResult = ({ result }: DevelopmentEditorRunResultContext) => {
  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#475467]">Java 运行结果</div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            点击运行按钮执行当前 Java 任务
          </div>
        </div>
      </div>
    );
  }

  if (result.status === 'RUNNING') {
    return (
      <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
        正在执行 Java 任务…
      </div>
    );
  }

  if (result.status !== 'SUCCESS') {
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div className="max-w-[680px]">
          <div className="text-[13px] font-medium text-[#b42318]">
            {result.status === 'CANCELLED'
              ? 'Java 执行已取消'
              : result.status === 'TIMEOUT'
                ? 'Java 执行超时'
                : 'Java 执行失败'}
          </div>
          <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
            {result.message || '未返回更多错误信息'}
          </div>
          {result.output?.stderr ? (
            <pre className="mt-3 max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 text-left font-mono text-[11px] text-[#344054]">
              {String(result.output.stderr)}
            </pre>
          ) : null}
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
  const exitCode = output.exitCode != null ? String(output.exitCode) : '—';

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <span className="text-[12px] font-medium text-[#344054]">Java 执行完成</span>
        <span className="text-[10px] text-[#98a2b3]">退出码：{exitCode} · 耗时 {result.durationMs} ms</span>
      </div>
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
          <div className="text-[12px] text-[#98a2b3]">任务执行完毕，无输出</div>
        )}
      </div>
    </div>
  );
};
