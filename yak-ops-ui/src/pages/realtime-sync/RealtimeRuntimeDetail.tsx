import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Progress,
  Space,
  Spin,
  Tabs,
  Timeline,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { realtimeApi } from './api';
import type {
  RealtimeEvent,
  RealtimeJob,
  RealtimeObservability,
  RealtimeRuntimeLog,
  RuntimeCapabilities,
} from './types';

const ACTIVE_STATES = new Set(['STARTING', 'RUNNING', 'STOPPING', 'UNKNOWN']);

const formatNumber = (value?: number) =>
  value === undefined || value === null
    ? '-'
    : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(value);

const formatRate = (value?: number) =>
  value === undefined || value === null ? '-' : `${formatNumber(value)}/s`;

const formatBytes = (value?: number) => {
  if (value === undefined || value === null) return '-';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let current = value / 1024;
  let index = 0;
  while (current >= 1024 && index < units.length - 1) {
    current /= 1024;
    index += 1;
  }
  return `${current.toFixed(current >= 100 ? 0 : current >= 10 ? 1 : 2)} ${units[index]}`;
};

const formatByteRate = (value?: number) =>
  value === undefined || value === null ? '-' : `${formatBytes(value)}/s`;

const formatDuration = (value?: number) => {
  if (value === undefined || value === null) return '-';
  const seconds = Math.max(0, Math.floor(value / 1000));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  if (days > 0) return `${days}天 ${hours}小时`;
  if (hours > 0) return `${hours}小时 ${minutes}分`;
  if (minutes > 0) return `${minutes}分 ${rest}秒`;
  return `${rest}秒`;
};

const formatTime = (value?: number) =>
  value === undefined || value === null ? '-' : new Date(value).toLocaleString('zh-CN');

const pressurePercent = (value?: number) =>
  value === undefined || value === null ? 0 : Math.max(0, Math.min(100, Math.round(value / 10)));

function MetricCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card size="small" className="h-full">
      <div className="text-[12px] text-[#667085]">{label}</div>
      <div className="mt-1 text-[22px] font-semibold leading-8 text-[#101828]">{value}</div>
      {hint && <div className="mt-1 text-[11px] text-[#98a2b3]">{hint}</div>}
    </Card>
  );
}

function CodeBlock({ children, empty }: { children?: string; empty: string }) {
  return (
    <pre className="max-h-[520px] min-h-[180px] overflow-auto rounded-lg bg-[#101828] p-4 text-[12px] leading-5 text-[#d0d5dd]">
      {children || empty}
    </pre>
  );
}

interface Props {
  job: RealtimeJob;
  events: RealtimeEvent[];
  capabilities: RuntimeCapabilities;
}

export default function RealtimeRuntimeDetail({ job, events, capabilities }: Props) {
  const [observability, setObservability] = useState<RealtimeObservability>();
  const [observabilityLoading, setObservabilityLoading] = useState(false);
  const [submissionLog, setSubmissionLog] = useState('');
  const [runtimeLog, setRuntimeLog] = useState<RealtimeRuntimeLog>();
  const [submissionLoading, setSubmissionLoading] = useState(false);
  const [runtimeLoading, setRuntimeLoading] = useState(false);

  const engineJobId = job.latestDeployment?.engineJobId;
  const hasDeployment = Boolean(job.latestDeployment);
  const canObserve = Boolean(engineJobId);

  const refreshObservability = useCallback(
    async (showError = true) => {
      if (!engineJobId) return;
      setObservabilityLoading(true);
      try {
        const result = await realtimeApi.observability(job.id);
        setObservability(result.data);
      } catch (error: any) {
        if (showError) message.error(error?.message || 'Flink 观测数据不可用');
      } finally {
        setObservabilityLoading(false);
      }
    },
    [engineJobId, job.id],
  );

  useEffect(() => {
    setObservability(undefined);
    setSubmissionLog('');
    setRuntimeLog(undefined);
    if (engineJobId) void refreshObservability(false);
  }, [engineJobId, job.id, refreshObservability]);

  useEffect(() => {
    if (!engineJobId || !ACTIVE_STATES.has(job.observedState)) return undefined;
    const timer = window.setInterval(() => void refreshObservability(false), 5000);
    return () => window.clearInterval(timer);
  }, [engineJobId, job.observedState, refreshObservability]);

  const loadSubmissionLog = async () => {
    setSubmissionLoading(true);
    try {
      const result = await realtimeApi.submissionLog(job.id);
      setSubmissionLog(result.data.logs || '');
    } catch (error: any) {
      message.error(error?.message || '提交日志不可用');
    } finally {
      setSubmissionLoading(false);
    }
  };

  const loadRuntimeLog = async () => {
    setRuntimeLoading(true);
    try {
      const result = await realtimeApi.runtimeLog(job.id);
      setRuntimeLog(result.data);
    } catch (error: any) {
      message.error(error?.message || '运行诊断不可用');
    } finally {
      setRuntimeLoading(false);
    }
  };

  const flinkWebUrl = useMemo(() => {
    if (observability?.flinkWebUrl) return observability.flinkWebUrl;
    if (!engineJobId || !capabilities.restUrl) return undefined;
    return `${capabilities.restUrl.replace(/\/+$/, '')}/#/job/${engineJobId}/overview`;
  }, [capabilities.restUrl, engineJobId, observability?.flinkWebUrl]);

  const checkpoint = observability?.checkpoints;
  const latestCheckpoint = checkpoint?.latestCompleted;
  const metrics = observability?.metrics;

  const overview = !canObserve ? (
    <Empty description="当前任务还没有可观测的 Flink JobId" />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between gap-3">
          <div className="text-[12px] text-[#98a2b3]">
            {observability?.sampledAt
              ? `采样时间：${formatTime(observability.sampledAt)}`
              : '正在读取 Flink 状态'}
          </div>
          <Space>
            {flinkWebUrl && (
              <Button size="small" href={flinkWebUrl} target="_blank">
                打开 Flink Web UI
              </Button>
            )}
            <Button
              size="small"
              icon={<ReloadOutlined />}
              loading={observabilityLoading}
              onClick={() => void refreshObservability()}
            >
              刷新
            </Button>
          </Space>
        </div>

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard
            label="Flink 状态"
            value={observability?.flinkState || job.observedState || '-'}
            hint={observability?.flinkJobName || '等待 Flink 返回运行名称'}
          />
          <MetricCard label="运行时长" value={formatDuration(observability?.durationMs)} />
          <MetricCard
            label="读取速率"
            value={formatRate(metrics?.recordsReadPerSecond)}
            hint={`累计 ${formatNumber(metrics?.recordsRead)} 条`}
          />
          <MetricCard
            label="写入速率"
            value={formatRate(metrics?.recordsWrittenPerSecond)}
            hint={`累计 ${formatNumber(metrics?.recordsWritten)} 条`}
          />
        </div>

        <Descriptions bordered size="small" column={2}>
          <Descriptions.Item label="定义版本">
            v{job.definitionVersion} / 已发布 v{job.publishedVersion || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="运行意图">
            {job.desiredState} / {job.observedState}
          </Descriptions.Item>
          <Descriptions.Item label="Flink JobId">{engineJobId || '-'}</Descriptions.Item>
          <Descriptions.Item label="Flink CDC Revision">
            {job.latestDeployment?.runtimeRevision || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="启动时间">{formatTime(observability?.startTime)}</Descriptions.Item>
          <Descriptions.Item label="最近 Checkpoint">
            {latestCheckpoint?.id
              ? `#${latestCheckpoint.id} · ${formatDuration(latestCheckpoint.durationMs)}`
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="部署摘要" span={2}>
            {job.latestDeployment?.specSummary || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="最近错误" span={2}>
            {job.lastError || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Space>
    </Spin>
  );

  const logs = (
    <Tabs
      items={[
        {
          key: 'submission',
          label: '提交日志',
          children: hasDeployment ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert
                type="info"
                showIcon
                message="这里展示 Flink CDC CLI 提交过程；即使提交失败、超时或 JobId 尚未恢复，也会按本次部署保留日志。密码、Token 等敏感字段会在后端再次脱敏。"
              />
              <Button loading={submissionLoading} onClick={() => void loadSubmissionLog()}>
                读取最近提交日志
              </Button>
              <CodeBlock empty="尚未读取提交日志">{submissionLog}</CodeBlock>
            </Space>
          ) : (
            <Empty description="任务尚无部署记录，暂无提交日志" />
          ),
        },
        {
          key: 'runtime',
          label: '运行诊断',
          children: canObserve ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert
                type="info"
                showIcon
                message="运行诊断来自 Flink Job Exception History，用于定位作业失败和 Task 异常；完整 JobManager/TaskManager 日志请进入 Flink Web UI。"
              />
              <Button loading={runtimeLoading} onClick={() => void loadRuntimeLog()}>
                刷新运行诊断
              </Button>
              {runtimeLog?.rootException && (
                <Alert
                  type="error"
                  showIcon
                  message={`最近异常 · ${formatTime(runtimeLog.timestamp)}`}
                  description={
                    <pre className="m-0 whitespace-pre-wrap text-[12px]">{runtimeLog.rootException}</pre>
                  }
                />
              )}
              {runtimeLog?.truncated && (
                <Alert type="warning" showIcon message="Flink 异常历史已截断，仅展示最近部分记录" />
              )}
              {runtimeLog?.exceptions?.length ? (
                <Timeline
                  items={runtimeLog.exceptions.map((item, index) => ({
                    color: 'red',
                    children: (
                      <div key={`${item.timestamp || 0}-${index}`}>
                        <div className="font-medium text-[#344054]">
                          {item.exceptionName || 'Runtime Exception'}
                        </div>
                        <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                          {formatTime(item.timestamp)} · {item.taskName || '-'} ·{' '}
                          {item.taskManagerId || '-'}
                        </div>
                        {item.stacktrace && (
                          <pre className="mt-2 max-h-[220px] overflow-auto whitespace-pre-wrap rounded bg-[#f8f9fb] p-3 text-[11px] text-[#475467]">
                            {item.stacktrace}
                          </pre>
                        )}
                      </div>
                    ),
                  }))}
                />
              ) : (
                <Empty description={runtimeLog ? 'Flink 当前没有异常历史' : '尚未读取运行诊断'} />
              )}
            </Space>
          ) : (
            <Empty description="Flink JobId 尚未确认；可以先查看提交日志，待状态对账恢复 JobId 后再读取运行诊断" />
          ),
        },
      ]}
    />
  );

  const checkpoints = !canObserve ? (
    <Empty description="当前任务还没有 Flink JobId，暂无 Checkpoint 数据" />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between">
          <Typography.Text type="secondary">Checkpoint 汇总由 Flink REST 归一化展示</Typography.Text>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={observabilityLoading}
            onClick={() => void refreshObservability()}
          >
            刷新
          </Button>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard label="成功" value={formatNumber(checkpoint?.completed)} />
          <MetricCard label="失败" value={formatNumber(checkpoint?.failed)} />
          <MetricCard label="进行中" value={formatNumber(checkpoint?.inProgress)} />
          <MetricCard label="总计" value={formatNumber(checkpoint?.total)} />
        </div>
        {latestCheckpoint ? (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="最近成功 ID">#{latestCheckpoint.id}</Descriptions.Item>
            <Descriptions.Item label="完成时间">
              {formatTime(latestCheckpoint.latestAckTimestamp)}
            </Descriptions.Item>
            <Descriptions.Item label="耗时">
              {formatDuration(latestCheckpoint.durationMs)}
            </Descriptions.Item>
            <Descriptions.Item label="状态大小">
              {formatBytes(latestCheckpoint.stateSizeBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="Checkpointed Size">
              {formatBytes(latestCheckpoint.checkpointedSizeBytes)}
            </Descriptions.Item>
            <Descriptions.Item label="确认 Subtasks">
              {latestCheckpoint.acknowledgedSubtasks ?? '-'} / {latestCheckpoint.totalSubtasks ?? '-'}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Empty description="还没有成功的 Checkpoint" />
        )}
        {checkpoint?.latestFailed?.failureMessage && (
          <Alert
            type="error"
            showIcon
            message="最近 Checkpoint 失败"
            description={checkpoint.latestFailed.failureMessage}
          />
        )}
      </Space>
    </Spin>
  );

  const metricPanel = !canObserve ? (
    <Empty description="当前任务还没有 Flink JobId，暂无 Metrics 数据" />
  ) : (
    <Spin spinning={observabilityLoading && !observability}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <div className="flex items-center justify-between">
          <Typography.Text type="secondary">
            Source/Sink 吞吐来自 Flink vertex 聚合指标；无法可靠识别时显示为 “-”
          </Typography.Text>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={observabilityLoading}
            onClick={() => void refreshObservability()}
          >
            刷新
          </Button>
        </div>
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <MetricCard
            label="Source Records"
            value={formatNumber(metrics?.recordsRead)}
            hint={formatRate(metrics?.recordsReadPerSecond)}
          />
          <MetricCard
            label="Sink Records"
            value={formatNumber(metrics?.recordsWritten)}
            hint={formatRate(metrics?.recordsWrittenPerSecond)}
          />
          <MetricCard
            label="Source Bytes"
            value={formatBytes(metrics?.bytesRead)}
            hint={formatByteRate(metrics?.bytesReadPerSecond)}
          />
          <MetricCard
            label="Sink Bytes"
            value={formatBytes(metrics?.bytesWritten)}
            hint={formatByteRate(metrics?.bytesWrittenPerSecond)}
          />
        </div>
        <Card size="small" title="运行压力">
          <Space direction="vertical" size={14} style={{ width: '100%' }}>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>最大 Busy</span>
                <span>{metrics?.maxBusyMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress percent={pressurePercent(metrics?.maxBusyMsPerSecond)} showInfo={false} />
            </div>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>最大 Backpressure</span>
                <span>{metrics?.maxBackpressuredMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress
                percent={pressurePercent(metrics?.maxBackpressuredMsPerSecond)}
                showInfo={false}
                status={
                  pressurePercent(metrics?.maxBackpressuredMsPerSecond) >= 70
                    ? 'exception'
                    : 'normal'
                }
              />
            </div>
            <div>
              <div className="mb-1 flex justify-between text-[12px] text-[#667085]">
                <span>最大 Idle</span>
                <span>{metrics?.maxIdleMsPerSecond?.toFixed(0) ?? '-'} ms/s</span>
              </div>
              <Progress percent={pressurePercent(metrics?.maxIdleMsPerSecond)} showInfo={false} />
            </div>
            <Typography.Text type="secondary" className="text-[11px]">
              当前采集到 {metrics?.vertexCount || 0} 个 Flink Vertex。
            </Typography.Text>
          </Space>
        </Card>
      </Space>
    </Spin>
  );

  return (
    <Tabs
      items={[
        { key: 'overview', label: '运行概览', children: overview },
        {
          key: 'events',
          label: '状态事件',
          children: events.length ? (
            <Timeline
              items={events.map((event) => ({
                color:
                  event.toState === 'FAILED' || event.toState === 'CONFLICT' ? 'red' : 'blue',
                children: (
                  <div>
                    <Typography.Text strong>{event.eventType}</Typography.Text>{' '}
                    <Typography.Text type="secondary">{event.createTime}</Typography.Text>
                    <div>
                      {event.fromState || '-'} → {event.toState || '-'} · {event.message}
                    </div>
                  </div>
                ),
              }))}
            />
          ) : (
            <Empty description="暂无状态事件" />
          ),
        },
        { key: 'logs', label: '日志', children: logs },
        { key: 'checkpoints', label: 'Checkpoint', children: checkpoints },
        { key: 'metrics', label: 'Metrics', children: metricPanel },
      ]}
    />
  );
}
