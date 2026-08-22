import {
  CheckCircleOutlined,
  CloudServerOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SafetyOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Alert, Button, Card, ConfigProvider, Descriptions, Space, Steps, Tag, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { BRAND_THEME } from '@/styles/brand';
import { realtimeApi } from './api';
import type { RealtimeJob, RuntimeCapabilities } from './types';

type ExecutionAction = 'validate' | 'publish' | 'start' | 'stop' | 'reconcile';

const stateLabel: Record<string, string> = {
  STOPPED: '已停止',
  STARTING: '启动中',
  RUNNING: '运行中',
  STOPPING: '停止中',
  FAILED: '失败',
  UNKNOWN: '未知',
  CONFLICT: '冲突',
};

export default function RealtimeExecutionPanel({
  job,
  onEdit,
  onBack,
}: {
  job: RealtimeJob;
  onEdit: () => void;
  onBack: () => void;
}) {
  const [current, setCurrent] = useState(job);
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities>({});
  const [validated, setValidated] = useState(false);
  const [acting, setActing] = useState<ExecutionAction>();

  useEffect(() => {
    setCurrent(job);
  }, [job]);

  useEffect(() => {
    let cancelled = false;
    realtimeApi
      .capabilities(current.runtimeEnvironmentId)
      .then((response) => {
        if (!cancelled) setCapabilities(response.data || {});
      })
      .catch(() => {
        if (!cancelled) setCapabilities({});
      });
    return () => {
      cancelled = true;
    };
  }, [current.runtimeEnvironmentId]);

  const refreshJob = async () => {
    const response = await realtimeApi.detail(current.id);
    setCurrent(response.data);
    return response.data;
  };

  const waitForStartResult = async () => {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const refreshed = await refreshJob();
      if (['RUNNING', 'FAILED', 'UNKNOWN', 'CONFLICT'].includes(refreshed.observedState)) {
        return refreshed;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 1500));
    }
    return refreshJob();
  };

  const run = async (action: ExecutionAction) => {
    setActing(action);
    try {
      await realtimeApi.action(current.id, action);
      if (action === 'validate') {
        setValidated(true);
        message.success('Flink CDC 运行校验通过');
        await refreshJob();
      } else if (action === 'publish') {
        setValidated(true);
        const refreshed = await refreshJob();
        message.success(
          refreshed.releaseState === 'PUBLISHED' ? '当前定义版本已发布' : '发布请求已完成',
        );
      } else if (action === 'start') {
        const refreshed = await waitForStartResult();
        if (refreshed.observedState === 'RUNNING') {
          message.success('实时同步任务已启动');
        } else if (refreshed.observedState === 'STARTING') {
          message.warning('Flink 任务仍在启动，可返回列表继续观察状态');
        } else {
          message.warning(`Flink 启动结果：${stateLabel[refreshed.observedState] || refreshed.observedState}`);
        }
      } else if (action === 'stop') {
        await refreshJob();
        message.success('已提交停止请求');
      } else {
        await refreshJob();
        message.success('状态对账已完成');
      }
    } catch (error: any) {
      message.error(error?.message || '执行操作失败');
      try {
        await refreshJob();
      } catch {
        // Keep the last known state when the follow-up read also fails.
      }
    } finally {
      setActing(undefined);
    }
  };

  const hasPublishedVersion = current.publishedVersion != null;
  const currentDraftPublished =
    current.releaseState === 'PUBLISHED' && current.publishedVersion === current.definitionVersion;
  const hasUnpublishedChanges = hasPublishedVersion && !currentDraftPublished;
  const running = current.desiredState === 'RUNNING';
  const startable =
    hasPublishedVersion &&
    current.desiredState === 'STOPPED' &&
    ['STOPPED', 'FAILED'].includes(current.observedState);
  const runtimeDisabled = capabilities.deployEnabled === false;
  const blockStartForDisplayedRuntime = currentDraftPublished && runtimeDisabled;
  const runtimeDisabledReason = capabilities.deployDisabledReason || '当前运行环境不可提交任务';

  const steps = useMemo(
    () => [
      {
        title: '保存草稿',
        description: `定义 v${current.definitionVersion}`,
        status: 'finish' as const,
      },
      {
        title: '运行校验',
        description: 'Flink CDC / Connector',
        status: validated || currentDraftPublished || running ? ('finish' as const) : ('process' as const),
      },
      {
        title: '发布版本',
        description: currentDraftPublished
          ? `已发布 v${current.publishedVersion}`
          : hasPublishedVersion
            ? `已发布 v${current.publishedVersion} · 当前草稿未发布`
            : '锁定当前定义版本',
        status: currentDraftPublished || running
          ? ('finish' as const)
          : validated
            ? ('process' as const)
            : hasPublishedVersion
              ? ('finish' as const)
              : ('wait' as const),
      },
      {
        title: '启动任务',
        description: running
          ? stateLabel[current.observedState] || current.observedState
          : hasUnpublishedChanges
            ? `将启动已发布 v${current.publishedVersion}`
            : '提交到 Flink CDC',
        status: current.observedState === 'RUNNING'
          ? ('finish' as const)
          : hasPublishedVersion
            ? ('process' as const)
            : ('wait' as const),
      },
    ],
    [current, currentDraftPublished, hasPublishedVersion, hasUnpublishedChanges, running, validated],
  );

  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] px-6 py-6 text-[#161823]">
        <div className="mx-auto w-full max-w-[1100px]">
          <header className="mb-5 flex flex-wrap items-start justify-between gap-4 rounded-xl bg-white px-7 py-6">
            <div>
              <div className="flex items-center gap-2 text-[12px] font-medium text-[var(--yak-brand-color)]">
                <CheckCircleOutlined />
                配置已保存 · 统一执行链路
              </div>
              <h1 className="mb-0 mt-1 text-[20px] font-semibold text-[#101828]">{current.name}</h1>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                任务 ID：{current.id} · 当前草稿 v{current.definitionVersion}
                {hasPublishedVersion ? ` · 已发布 v${current.publishedVersion}` : ''}
              </div>
            </div>
            <Space>
              <Button disabled={Boolean(acting)} onClick={onEdit}>
                继续编辑
              </Button>
              <Button disabled={Boolean(acting)} onClick={onBack}>
                返回任务列表
              </Button>
            </Space>
          </header>

          <Card className="mb-5">
            <Steps responsive items={steps} />
          </Card>

          {hasUnpublishedChanges && (
            <Alert
              className="mb-5"
              type="info"
              showIcon
              message={`当前草稿 v${current.definitionVersion} 尚未发布`}
              description={`点击“启动任务”会运行已发布版本 v${current.publishedVersion}；当前草稿及其运行环境配置不会影响这次启动。`}
            />
          )}

          {runtimeDisabled && currentDraftPublished && (
            <Alert
              className="mb-5"
              type="warning"
              showIcon
              message="当前已发布版本绑定的运行环境暂不可提交任务"
              description={runtimeDisabledReason}
            />
          )}

          <div className="grid grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)] gap-5 max-lg:grid-cols-1">
            <Card title="下一步操作">
              <div className="space-y-4">
                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <SafetyOutlined /> 1. 校验运行环境
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        校验当前草稿绑定的运行环境和 Connector；启动时后端会再次按实际 Published Version 独立校验。
                      </div>
                    </div>
                    <Button
                      loading={acting === 'validate'}
                      disabled={Boolean(acting) || !current.spec || runtimeDisabled}
                      onClick={() => void run('validate')}
                    >
                      运行校验
                    </Button>
                  </div>
                </div>

                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <CloudServerOutlined /> 2. 发布当前版本
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        发布会再次运行完整校验，并创建不可变的 DefinitionVersion。
                      </div>
                    </div>
                    <Button
                      type="primary"
                      loading={acting === 'publish'}
                      disabled={Boolean(acting) || currentDraftPublished || running || !current.spec || runtimeDisabled}
                      onClick={() => void run('publish')}
                    >
                      {currentDraftPublished ? '已发布' : '发布当前版本'}
                    </Button>
                  </div>
                </div>

                <div className="rounded-xl border border-[#eaecf0] p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-[14px] font-semibold text-[#101828]">
                        <PlayCircleOutlined /> 3. 启动实时任务
                      </div>
                      <div className="mt-1 text-[12px] leading-5 text-[#667085]">
                        启动只读取不可变的 Published DefinitionVersion，由 PipelineYamlCompiler 生成临时 Flink CDC YAML 后通过 LOCAL / SSH 提交。
                      </div>
                    </div>
                    {running ? (
                      <Button
                        danger
                        icon={<StopOutlined />}
                        loading={acting === 'stop'}
                        disabled={Boolean(acting)}
                        onClick={() => void run('stop')}
                      >
                        停止任务
                      </Button>
                    ) : (
                      <Button
                        type="primary"
                        danger
                        icon={<PlayCircleOutlined />}
                        loading={acting === 'start'}
                        disabled={Boolean(acting) || !startable || blockStartForDisplayedRuntime}
                        onClick={() => void run('start')}
                      >
                        {hasUnpublishedChanges ? `启动已发布 v${current.publishedVersion}` : '启动任务'}
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            </Card>

            <div className="space-y-5">
              <Card title="当前执行状态">
                <Descriptions size="small" column={1}>
                  <Descriptions.Item label="当前草稿">
                    v{current.definitionVersion} · {current.releaseState}
                  </Descriptions.Item>
                  <Descriptions.Item label="已发布版本">
                    {hasPublishedVersion ? `v${current.publishedVersion}` : '未发布'}
                  </Descriptions.Item>
                  <Descriptions.Item label="运行状态">
                    <Tag>{stateLabel[current.observedState] || current.observedState}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="当前草稿运行环境">
                    {capabilities.runtimeEnvironmentName || `环境 #${current.runtimeEnvironmentId}`}
                  </Descriptions.Item>
                  <Descriptions.Item label="提交方式">
                    {capabilities.submissionMode || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Flink / CDC">
                    {capabilities.flinkVersion || '-'} / {capabilities.flinkCdcVersion || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Engine JobId">
                    {current.latestDeployment?.engineJobId || '-'}
                  </Descriptions.Item>
                </Descriptions>
                <Button
                  className="mt-3"
                  size="small"
                  icon={<ReloadOutlined />}
                  disabled={Boolean(acting)}
                  onClick={() => void refreshJob()}
                >
                  刷新状态
                </Button>
              </Card>

              <Alert
                type="info"
                showIcon
                message="Wizard 与 YAML 共用这一条执行链路"
                description="编辑方式不会写入任务运行定义。发布形成不可变 DefinitionVersion；启动只读取该版本快照，数据库不保存原生 Flink CDC YAML。"
              />
            </div>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
