import { Button, Input, Table, Tag, Tooltip, message, type TableColumnsType } from 'antd';
import { RefreshCw, Search } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchDataServiceLogs, type DataServiceCallLog } from '../service';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

const callerLabel = (record: DataServiceCallLog) => {
  if (record.callerType === 'CONSOLE') return '控制台测试';
  if (record.callerType === 'API_KEY') return record.apiKeyName || 'API Key';
  return '公开调用';
};

export default function DataServiceLogsPage() {
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const response = await fetchDataServiceLogs();
      setLogs(response.data || []);
    } catch (error: any) {
      message.error(error?.message || '加载调用记录失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return logs;
    return logs.filter((item) =>
      [
        item.serviceName,
        item.servicePath,
        item.apiKeyName,
        item.apiKeyPrefix,
        item.callerType,
        item.paramsJson,
        item.errorMessage,
      ]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(value)));
  }, [keyword, logs]);

  const columns: TableColumnsType<DataServiceCallLog> = [
    {
      title: 'API 服务',
      dataIndex: 'serviceName',
      minWidth: 220,
      render: (_, record) => (
        <div className="py-1">
          <div className="font-medium text-[#161823]">{record.serviceName}</div>
          <div className="mt-1 font-mono text-xs text-black/40">{record.servicePath}</div>
        </div>
      ),
    },
    {
      title: '调用方',
      key: 'caller',
      width: 170,
      render: (_, record) => (
        <div>
          <div className="text-black/65">{callerLabel(record)}</div>
          {record.apiKeyPrefix ? (
            <div className="mt-1 font-mono text-[11px] text-black/35">{record.apiKeyPrefix}••••</div>
          ) : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'success',
      width: 90,
      render: (value: boolean) => value
        ? <Tag bordered={false}>成功</Tag>
        : <Tag bordered={false}>失败</Tag>,
    },
    { title: '耗时', dataIndex: 'durationMs', width: 100, render: (value) => `${value ?? 0} ms` },
    { title: '返回行数', dataIndex: 'rowCount', width: 100 },
    {
      title: '请求参数',
      dataIndex: 'paramsJson',
      minWidth: 220,
      ellipsis: true,
      render: (value) => <Tooltip title={value}><span className="font-mono text-xs text-black/55">{value || '{}'}</span></Tooltip>,
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      minWidth: 220,
      ellipsis: true,
      render: (value) => value ? <Tooltip title={value}><span>{value}</span></Tooltip> : <span className="text-black/25">-</span>,
    },
    { title: '调用时间', dataIndex: 'createTime', width: 170, render: formatTime },
  ];

  return (
    <div className="h-full bg-white px-6 py-5">
      <div className="mb-5">
        <h1 className="m-0 text-xl font-semibold text-[#161823]">调用记录</h1>
        <p className="mb-0 mt-1 text-sm text-black/45">查看最近 200 次数据服务调用、调用方身份和执行结果。</p>
      </div>
      <div className="mb-3 flex items-center justify-between gap-3">
        <Input
          allowClear
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          prefix={<Search size={15} className="text-black/30" />}
          placeholder="搜索服务、调用方、Key 前缀或错误信息"
          className="max-w-[360px]"
        />
        <Button icon={<RefreshCw size={15} />} onClick={() => void load()}>刷新</Button>
      </div>
      <Table<DataServiceCallLog>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={filtered}
        columns={columns}
        pagination={false}
        scroll={{ x: 1320, y: 'calc(100vh - 250px)' }}
      />
    </div>
  );
}
