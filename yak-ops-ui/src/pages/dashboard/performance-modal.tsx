import { fetchAnalyses } from '@/components/analysis/analysis-service';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Modal,
  Table,
  Tooltip,
  type TableColumnsType,
} from 'antd';
import { Download, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchDashboard } from './dashboard-service';
import type { AnalysisAsset, DashboardWidget } from './model';
import {
  fetchDashboardQueryPerformance,
  type DashboardQueryPerformance,
} from './performance-service';

const formatTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false });
};

const escapeCsv = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;

const widgetDatasetId = (widget: DashboardWidget, analyses: AnalysisAsset[]) => (
  widget.inlineAnalysis?.datasetId
  ?? (widget.analysisId ? analyses.find((item) => item.id === widget.analysisId)?.datasetId : undefined)
);

const widgetTitle = (widget: DashboardWidget, analyses: AnalysisAsset[]) => (
  widget.title?.trim()
  || (widget.analysisId ? analyses.find((item) => item.id === widget.analysisId)?.name : undefined)
  || '未命名组件'
);

export function DashboardPerformanceModal({
  open,
  dashboardId,
  dashboardName,
  onClose,
}: {
  open: boolean;
  dashboardId: string;
  dashboardName: string;
  onClose: () => void;
}) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [records, setRecords] = useState<DashboardQueryPerformance[]>([]);
  const [detail, setDetail] = useState<DashboardQueryPerformance>();
  const [widgets, setWidgets] = useState<DashboardWidget[]>([]);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);

  const widgetNamesByDataset = useMemo(() => {
    const result = new Map<string, string[]>();
    widgets.forEach((widget) => {
      const datasetId = widgetDatasetId(widget, analyses);
      if (!datasetId) return;
      const current = result.get(datasetId) ?? [];
      const title = widgetTitle(widget, analyses);
      if (!current.includes(title)) current.push(title);
      result.set(datasetId, current);
    });
    return result;
  }, [analyses, widgets]);

  const load = useCallback(async () => {
    if (!/^\d+$/.test(dashboardId)) return;
    setLoading(true);
    setError(undefined);
    try {
      const [dashboard, nextAnalyses] = await Promise.all([
        fetchDashboard(dashboardId),
        fetchAnalyses(),
      ]);
      setWidgets(dashboard.widgets);
      setAnalyses(nextAnalyses);
      const datasetIds = Array.from(new Set(dashboard.widgets
        .map((widget) => widgetDatasetId(widget, nextAnalyses))
        .filter((value): value is string => Boolean(value))));
      setRecords(await fetchDashboardQueryPerformance(datasetIds, 100));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '读取性能分析记录失败');
    } finally {
      setLoading(false);
    }
  }, [dashboardId]);

  useEffect(() => {
    if (open) void load();
  }, [load, open]);

  const exportDetails = () => {
    const header = [
      '一级资源名称', '二级资源名称', '数据集', '查询ID', '数据源等待(ms)', '查询准备阶段(ms)',
      'SQL执行时间(ms)', '结果处理时间(ms)', '总查询时间(ms)', '返回行数', '查询开始时间', '数据源ID', 'SQL',
    ];
    const rows = records.map((record) => [
      dashboardName,
      (widgetNamesByDataset.get(record.datasetId) ?? []).join(' / '),
      record.datasetName || record.datasetId,
      record.queryId,
      record.waitMillis,
      record.prepareMillis,
      record.executeMillis,
      record.transferMillis,
      record.totalMillis,
      record.returnedRows,
      formatTime(record.startedAt),
      record.dataSourceId ?? '',
      record.sql,
    ]);
    const csv = `\uFEFF${[header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${dashboardName || 'dashboard'}-performance.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const columns: TableColumnsType<DashboardQueryPerformance> = [
    {
      title: '一级资源名称',
      width: 130,
      render: () => <span className="text-[#344054]">{dashboardName || '仪表盘'}</span>,
    },
    {
      title: '二级资源名称',
      width: 180,
      render: (_, record) => {
        const names = widgetNamesByDataset.get(record.datasetId) ?? [];
        const value = names.length ? names.join(' / ') : '-';
        return <Tooltip title={value}><span className="block max-w-[160px] truncate">{value}</span></Tooltip>;
      },
    },
    {
      title: '数据集',
      width: 150,
      render: (_, record) => record.datasetName || record.datasetId,
    },
    {
      title: '查询ID',
      dataIndex: 'queryId',
      width: 150,
      render: (value: string, record) => (
        <Button type="link" className="!h-auto !p-0 !text-[11px]" onClick={() => setDetail(record)}>
          {value.slice(0, 12)}…
        </Button>
      ),
    },
    { title: '查询等待时间 (ms)', dataIndex: 'waitMillis', width: 135, align: 'right' },
    { title: '查询准备阶段 (ms)', dataIndex: 'prepareMillis', width: 135, align: 'right' },
    { title: 'SQL执行时间 (ms)', dataIndex: 'executeMillis', width: 125, align: 'right' },
    { title: '结果处理时间 (ms)', dataIndex: 'transferMillis', width: 130, align: 'right' },
    {
      title: '总查询时间 (ms)',
      dataIndex: 'totalMillis',
      width: 125,
      align: 'right',
      render: (value: number) => (
        <span className={value >= 1000 ? 'font-semibold text-[var(--yak-brand-color)]' : 'font-medium text-[#161823]'}>
          {value}
        </span>
      ),
    },
    { title: '返回行数', dataIndex: 'returnedRows', width: 90, align: 'right' },
    {
      title: '查询开始时间',
      dataIndex: 'startedAt',
      width: 170,
      render: (value: string) => formatTime(value),
    },
  ];

  return (
    <>
      <Modal
        title={<span className="text-[14px] font-semibold text-[#161823]">{dashboardName || '仪表盘'} · 性能分析</span>}
        width="min(1240px, calc(100vw - 48px))"
        open={open}
        onCancel={onClose}
        footer={null}
        destroyOnClose={false}
        styles={{ body: { paddingTop: 8 } }}
      >
        <div className="mb-3 flex items-center justify-between">
          <div>
            <div className="text-[12px] font-semibold text-[#344054]">核心信息</div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">最近 100 条当前已保存仪表盘的 Dataset SQL 查询记录</div>
          </div>
          <div className="flex items-center gap-1">
            <Button type="text" size="small" icon={<RefreshCw size={12} />} loading={loading} onClick={() => void load()}>
              刷新
            </Button>
            <Button size="small" icon={<Download size={12} />} disabled={!records.length} onClick={exportDetails}>
              导出详细信息
            </Button>
          </div>
        </div>

        {error ? <Alert className="mb-3" type="error" showIcon message={error} /> : null}

        <Table<DashboardQueryPerformance>
          rowKey="queryId"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={records}
          pagination={false}
          scroll={{ x: 1500, y: 430 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="当前进程尚未采集到该仪表盘的查询记录，请操作或刷新图表后再查看"
              />
            ),
          }}
        />
      </Modal>

      <Modal
        title="查询详情"
        width={820}
        open={Boolean(detail)}
        onCancel={() => setDetail(undefined)}
        footer={<Button onClick={() => setDetail(undefined)}>关闭</Button>}
      >
        {detail ? (
          <div>
            <Descriptions
              size="small"
              bordered
              column={3}
              items={[
                { key: 'queryId', label: '查询 ID', children: detail.queryId, span: 3 },
                { key: 'dataset', label: '数据集', children: detail.datasetName },
                { key: 'version', label: '版本', children: `V${detail.datasetVersionNo}` },
                { key: 'rows', label: '返回行数', children: detail.returnedRows },
                { key: 'wait', label: '数据源等待', children: `${detail.waitMillis} ms` },
                { key: 'prepare', label: '准备阶段', children: `${detail.prepareMillis} ms` },
                { key: 'execute', label: 'SQL 执行', children: `${detail.executeMillis} ms` },
                { key: 'transfer', label: '结果处理', children: `${detail.transferMillis} ms` },
                { key: 'total', label: '总耗时', children: `${detail.totalMillis} ms` },
                { key: 'start', label: '开始时间', children: formatTime(detail.startedAt) },
              ]}
            />
            <div className="mt-4 text-[12px] font-semibold text-[#344054]">最终执行 SQL</div>
            <pre className="mt-2 max-h-[320px] overflow-auto whitespace-pre-wrap break-all border border-[#e4e7ec] bg-[#f7f8fa] p-3 font-mono text-[11px] leading-5 text-[#344054]">
              {detail.sql || '-- SQL 不可用'}
            </pre>
            <div className="mt-2 text-[10px] leading-5 text-[#98a2b3]">
              SQL 执行时间包含 JDBC 驱动取数；结果处理时间仅统计 Query Runtime 对返回结果的截断与整理。
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
