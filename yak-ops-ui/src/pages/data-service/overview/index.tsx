import { Button, Empty, Table, Tag, Tooltip, message, type TableColumnsType } from 'antd';
import {
  Activity,
  AlertCircle,
  CheckCircle2,
  Gauge,
  RefreshCw,
  Server,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  fetchDataServiceLogs,
  fetchDataServices,
  type DataServiceApi,
  type DataServiceCallLog,
} from '../service';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

const MetricCard = ({
  label,
  value,
  note,
  icon,
}: {
  label: string;
  value: string | number;
  note: string;
  icon: React.ReactNode;
}) => (
  <div className="rounded-[6px] border border-[#e6e8eb] bg-white px-4 py-4">
    <div className="flex items-start justify-between gap-3">
      <div>
        <div className="text-[11px] text-[#98a2b3]">{label}</div>
        <div className="mt-2 text-[24px] font-semibold tracking-[-.02em] text-[#1d2939]">{value}</div>
      </div>
      <div className="flex h-8 w-8 items-center justify-center rounded-[5px] bg-[#f6f7f8] text-[#667085]">
        {icon}
      </div>
    </div>
    <div className="mt-2 text-[11px] text-[#98a2b3]">{note}</div>
  </div>
);

export default function DataServiceOverviewPage() {
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [serviceResponse, logResponse] = await Promise.all([
        fetchDataServices(),
        fetchDataServiceLogs(),
      ]);
      setServices(serviceResponse.data || []);
      setLogs(logResponse.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载数据服务运行概览失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const successCalls = useMemo(() => logs.filter((item) => item.success).length, [logs]);
  const failureCalls = logs.length - successCalls;
  const successRate = logs.length ? Math.round((successCalls / logs.length) * 1000) / 10 : 0;
  const averageDuration = logs.length
    ? Math.round(logs.reduce((sum, item) => sum + (item.durationMs || 0), 0) / logs.length)
    : 0;
  const runningServices = services.filter((item) => item.enabled).length;

  const hotApis = useMemo(() => {
    const counts = new Map<number, { calls: number; success: number; duration: number }>();
    logs.forEach((item) => {
      const current = counts.get(item.apiId) || { calls: 0, success: 0, duration: 0 };
      current.calls += 1;
      current.success += item.success ? 1 : 0;
      current.duration += item.durationMs || 0;
      counts.set(item.apiId, current);
    });

    return services
      .map((service) => ({ service, stats: counts.get(service.id) }))
      .filter((item): item is { service: DataServiceApi; stats: { calls: number; success: number; duration: number } } => Boolean(item.stats))
      .sort((left, right) => right.stats.calls - left.stats.calls)
      .slice(0, 8);
  }, [logs, services]);

  const maxCalls = hotApis[0]?.stats.calls || 1;
  const recentFailures = useMemo(
    () => logs.filter((item) => !item.success).slice(0, 8),
    [logs],
  );

  const failureColumns: TableColumnsType<DataServiceCallLog> = [
    {
      title: 'API',
      dataIndex: 'serviceName',
      minWidth: 180,
      render: (_, record) => (
        <div>
          <div className="font-medium text-[#344054]">{record.serviceName}</div>
          <div className="mt-0.5 truncate font-mono text-[10px] text-[#98a2b3]">{record.servicePath}</div>
        </div>
      ),
    },
    { title: '耗时', dataIndex: 'durationMs', width: 90, render: (value) => `${value || 0} ms` },
    {
      title: '错误',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (value) => value
        ? <Tooltip title={value}><span className="text-[#b42318]">{value}</span></Tooltip>
        : <span className="text-[#98a2b3]">未知错误</span>,
    },
    { title: '时间', dataIndex: 'createTime', width: 160, render: formatTime },
  ];

  return (
    <div className="h-full overflow-y-auto bg-[#fafafa] px-6 py-5">
      <div className="mx-auto max-w-[1280px]">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h1 className="m-0 text-xl font-semibold text-[#161823]">运行概览</h1>
            <p className="mb-0 mt-1 text-sm text-black/45">
              基于当前 API 状态和最近 200 条调用记录做轻量统计，不额外引入计量平台。
            </p>
          </div>
          <Button loading={loading} icon={<RefreshCw size={15} />} onClick={() => void load()}>刷新</Button>
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
          <MetricCard
            label="API 总数"
            value={services.length}
            note={`${runningServices} 个运行中`}
            icon={<Server size={16} strokeWidth={1.8} />}
          />
          <MetricCard
            label="近期调用"
            value={logs.length}
            note="最近调用记录窗口"
            icon={<Activity size={16} strokeWidth={1.8} />}
          />
          <MetricCard
            label="成功率"
            value={`${successRate}%`}
            note={`${successCalls} 成功 / ${failureCalls} 失败`}
            icon={<CheckCircle2 size={16} strokeWidth={1.8} />}
          />
          <MetricCard
            label="平均耗时"
            value={`${averageDuration} ms`}
            note="基于近期调用计算"
            icon={<Gauge size={16} strokeWidth={1.8} />}
          />
          <MetricCard
            label="失败调用"
            value={failureCalls}
            note="建议优先查看异常 API"
            icon={<AlertCircle size={16} strokeWidth={1.8} />}
          />
        </div>

        <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(480px,1.25fr)]">
          <section className="rounded-[6px] border border-[#e6e8eb] bg-white p-5">
            <div className="mb-4">
              <div className="text-[14px] font-semibold text-[#30323b]">热门 API</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">按最近调用次数排序</div>
            </div>

            {hotApis.length ? (
              <div className="space-y-4">
                {hotApis.map(({ service, stats }, index) => {
                  const rate = stats.calls ? Math.round((stats.success / stats.calls) * 1000) / 10 : 0;
                  const average = stats.calls ? Math.round(stats.duration / stats.calls) : 0;
                  return (
                    <div key={service.id}>
                      <div className="flex items-center justify-between gap-4 text-[12px]">
                        <div className="flex min-w-0 items-center gap-2">
                          <span className="w-4 shrink-0 text-[10px] text-[#98a2b3]">{index + 1}</span>
                          <span className="truncate font-medium text-[#344054]">{service.name}</span>
                          {!service.enabled ? <Tag bordered={false}>已停用</Tag> : null}
                        </div>
                        <div className="shrink-0 text-[11px] text-[#98a2b3]">
                          {stats.calls} 次 · {rate}% · {average} ms
                        </div>
                      </div>
                      <div className="ml-6 mt-2 h-1.5 overflow-hidden rounded-full bg-[#f0f2f4]">
                        <div
                          className="h-full rounded-full bg-[#667085]"
                          style={{ width: `${Math.max(4, (stats.calls / maxCalls) * 100)}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调用数据" />
            )}
          </section>

          <section className="rounded-[6px] border border-[#e6e8eb] bg-white p-5">
            <div className="mb-4">
              <div className="text-[14px] font-semibold text-[#30323b]">最近失败</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">快速发现需要处理的调用异常</div>
            </div>
            <Table<DataServiceCallLog>
              rowKey="id"
              size="small"
              loading={loading}
              pagination={false}
              dataSource={recentFailures}
              columns={failureColumns}
              scroll={{ x: 680 }}
              locale={{ emptyText: '近期没有失败调用' }}
            />
          </section>
        </div>
      </div>
    </div>
  );
}
