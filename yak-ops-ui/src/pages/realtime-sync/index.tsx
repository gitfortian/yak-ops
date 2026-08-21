import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  message,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { realtimeApi } from './api';
import JobEditor from './JobEditor';
import type { DataSourceOption, RealtimeEvent, RealtimeJob, RuntimeCapabilities } from './types';

const stateColor = (state: string) =>
  ({
    RUNNING: 'green',
    FAILED: 'red',
    CONFLICT: 'red',
    UNKNOWN: 'orange',
    STARTING: 'blue',
    STOPPING: 'blue',
    PUBLISHED: 'geekblue',
    DRAFT: 'default',
  })[state] || 'default';

export default function RealtimeSync() {
  const [jobs, setJobs] = useState<RealtimeJob[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities>({});
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [editor, setEditor] = useState<{ open: boolean; job?: RealtimeJob }>({ open: false });
  const [detail, setDetail] = useState<RealtimeJob>();
  const [events, setEvents] = useState<RealtimeEvent[]>([]);
  const [logs, setLogs] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, caps, sources] = await Promise.allSettled([
        realtimeApi.page(pageNo, pageSize, keyword || undefined),
        realtimeApi.capabilities(),
        realtimeApi.dataSources(),
      ]);
      if (page.status === 'rejected') throw page.reason;
      setJobs(page.value.data.records || []);
      setTotal(page.value.data.total || 0);
      setCapabilities(caps.status === 'fulfilled' ? caps.value.data || {} : {});
      setDataSources(sources.status === 'fulfilled' ? sources.value.data || [] : []);
      if (caps.status === 'rejected') message.warning('Runtime 当前不可用，任务定义仍可查看');
    } catch (error: any) {
      message.error(error?.message || '加载实时同步控制面失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, pageSize]);

  useEffect(() => {
    void load();
  }, [load]);

  const action = async (job: RealtimeJob, name: 'publish' | 'validate' | 'start' | 'stop' | 'restart') => {
    try {
      await realtimeApi.action(job.id, name);
      message.success(name === 'validate' ? 'Runtime 校验通过' : '操作成功');
      await load();
    } catch (error: any) {
      message.error(error?.message || '操作失败');
    }
  };

  const openDetail = async (job: RealtimeJob) => {
    try {
      const [jobResult, eventResult] = await Promise.all([realtimeApi.detail(job.id), realtimeApi.events(job.id)]);
      setDetail(jobResult.data);
      setEvents(eventResult.data || []);
      setLogs('');
    } catch (error: any) {
      message.error(error?.message || '加载运行详情失败');
    }
  };

  const columns: ColumnsType<RealtimeJob> = [
    {
      title: '任务',
      dataIndex: 'name',
      width: 210,
      render: (value, job) => (
        <Space direction="vertical" size={0}>
          <Button type="link" style={{ padding: 0 }} onClick={() => openDetail(job)}>
            {value}
          </Button>
          <Typography.Text type="secondary" ellipsis style={{ maxWidth: 200 }}>
            {job.description || '-'}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '版本', width: 90, render: (_, job) => `v${job.definitionVersion}` },
    {
      title: '发布',
      dataIndex: 'releaseState',
      width: 110,
      render: (value) => <Tag color={stateColor(value)}>{value}</Tag>,
    },
    { title: '期望', dataIndex: 'desiredState', width: 110, render: (value) => <Tag>{value}</Tag> },
    {
      title: '运行',
      dataIndex: 'observedState',
      width: 120,
      render: (value) => <Tag color={stateColor(value)}>{value}</Tag>,
    },
    { title: 'Runtime', width: 150, render: (_, job) => job.latestDeployment?.runtimeRevision || '-' },
    { title: '更新时间', dataIndex: 'updateTime', width: 190 },
    {
      title: '操作',
      fixed: 'right',
      width: 360,
      render: (_, job) => (
        <Space wrap>
          <Button size="small" disabled={job.desiredState === 'RUNNING'} onClick={() => setEditor({ open: true, job })}>
            编辑
          </Button>
          <Button size="small" disabled={job.releaseState === 'PUBLISHED'} onClick={() => action(job, 'validate')}>
            校验
          </Button>
          <Button size="small" disabled={job.releaseState === 'PUBLISHED'} onClick={() => action(job, 'publish')}>
            发布
          </Button>
          <Button
            size="small"
            type="primary"
            disabled={
              !capabilities.dynamicCredentialBinding ||
              job.releaseState !== 'PUBLISHED' ||
              job.desiredState === 'RUNNING'
            }
            onClick={() => action(job, 'start')}
          >
            启动
          </Button>
          <Button size="small" disabled={job.desiredState === 'STOPPED'} onClick={() => action(job, 'stop')}>
            停止
          </Button>
          <Button size="small" disabled={job.desiredState !== 'RUNNING'} onClick={() => action(job, 'restart')}>
            重启
          </Button>
          <Popconfirm
            title="确认删除这个已停止任务？"
            onConfirm={async () => {
              await realtimeApi.remove(job.id);
              await load();
            }}
          >
            <Button size="small" danger disabled={job.desiredState !== 'STOPPED'}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div>
        <Typography.Title level={3} style={{ marginBottom: 4 }}>
          实时同步
        </Typography.Title>
        <Typography.Text type="secondary">MySQL CDC → MySQL / PostgreSQL，使用固定 Yak CDC Runtime。</Typography.Text>
      </div>
      <Alert
        type="error"
        showIcon
        message="Runtime 尚无按部署动态凭据接口，启动已被安全阻止"
        description="可以创建、校验和发布逻辑定义；合并前需要 Runtime 提供安全的按部署凭据绑定与内存内解析。不会用明文 YAML 或假定 Flink CDC 自动展开占位符来绕过。"
      />
      <Card size="small" title="Runtime 能力">
        <Descriptions size="small" column={4}>
          <Descriptions.Item label="Runtime">{capabilities.runtimeVersion || '-'}</Descriptions.Item>
          <Descriptions.Item label="Flink">{capabilities.flinkVersion || '-'}</Descriptions.Item>
          <Descriptions.Item label="Flink CDC">{capabilities.flinkCdcVersion || '-'}</Descriptions.Item>
          <Descriptions.Item label="语义">{capabilities.deliverySemantics || '-'}</Descriptions.Item>
          <Descriptions.Item label="Sources">{capabilities.connectors?.sources?.join(', ') || '-'}</Descriptions.Item>
          <Descriptions.Item label="Sinks" span={3}>
            {capabilities.connectors?.sinks?.join(', ') || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card
        title="任务定义"
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder="搜索任务"
              onSearch={(value) => {
                setKeyword(value);
                setPageNo(1);
              }}
            />
            <Button onClick={load}>刷新</Button>
            <Button type="primary" onClick={() => setEditor({ open: true })}>
              新建任务
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={jobs}
          columns={columns}
          scroll={{ x: 1450 }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (nextPage, nextSize) => {
              setPageNo(nextPage);
              setPageSize(nextSize);
            },
          }}
        />
      </Card>
      <JobEditor
        open={editor.open}
        job={editor.job}
        dataSources={dataSources}
        onClose={() => setEditor({ open: false })}
        onSaved={() => {
          setEditor({ open: false });
          void load();
        }}
      />
      <Drawer width={820} title={detail?.name} open={Boolean(detail)} onClose={() => setDetail(undefined)}>
        {detail && (
          <Tabs
            items={[
              {
                key: 'overview',
                label: '运行概览',
                children: (
                  <Descriptions column={1} bordered size="small">
                    <Descriptions.Item label="定义版本">
                      v{detail.definitionVersion} / 已发布 v{detail.publishedVersion || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                      {detail.releaseState} · {detail.desiredState} · {detail.observedState}
                    </Descriptions.Item>
                    <Descriptions.Item label="部署摘要">
                      {detail.latestDeployment?.specSummary || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Engine Job ID">
                      {detail.latestDeployment?.engineJobId || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Runtime Revision">
                      {detail.latestDeployment?.runtimeRevision || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="最近错误">{detail.lastError || '-'}</Descriptions.Item>
                  </Descriptions>
                ),
              },
              {
                key: 'events',
                label: '状态事件',
                children: (
                  <Timeline
                    items={events.map((event) => ({
                      color: event.toState === 'FAILED' || event.toState === 'CONFLICT' ? 'red' : 'blue',
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
                ),
              },
              {
                key: 'logs',
                label: '临时日志',
                children: (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    <Button
                      onClick={async () => {
                        try {
                          setLogs((await realtimeApi.logs(detail.id)).data.logs);
                        } catch (error: any) {
                          message.error(error?.message || '日志不可用');
                        }
                      }}
                    >
                      读取最近日志
                    </Button>
                    <pre
                      style={{
                        whiteSpace: 'pre-wrap',
                        maxHeight: 520,
                        overflow: 'auto',
                        background: '#111',
                        color: '#ddd',
                        padding: 12,
                      }}
                    >
                      {logs || '尚未读取'}
                    </pre>
                  </Space>
                ),
              },
              {
                key: 'observability',
                label: 'Checkpoint / Metrics',
                children: (
                  <Alert
                    type="warning"
                    showIcon
                    message="当前 Runtime Gateway 不支持 Checkpoint 与 Metrics API"
                    description="页面不会直接访问 Flink REST，也不会伪造吞吐或 Checkpoint 数据。"
                  />
                ),
              },
            ]}
          />
        )}
      </Drawer>
    </Space>
  );
}
