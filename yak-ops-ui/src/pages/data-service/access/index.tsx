import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import {
  listDataServiceAccessOverview,
  listDataServiceKeys,
  type DataServiceAccessOverviewItem,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceAuthMode,
  type DataServiceIpAccessMode,
} from '@/services/data-service';
import {
  Drawer,
  Input,
  Select,
  Spin,
  Table,
  message,
  type TableColumnsType,
} from 'antd';
import { KeyRound, Network, RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import DataServiceAccessControlPanel from '../components/DataServiceAccessControlPanel';
import DataServiceApiCallPanel from '../components/DataServiceApiCallPanel';

type AuthFilter = 'ALL' | DataServiceAuthMode;
type IpFilter = 'ALL' | DataServiceIpAccessMode;
type AccessDrawerTab = 'AUTH' | 'IP';

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

const ipModeLabel: Record<DataServiceIpAccessMode, string> = {
  NONE: '不限制',
  ALLOWLIST: '白名单',
  DENYLIST: '黑名单',
};

const isActiveKey = (key: DataServiceApiKey) => {
  if (!key.enabled) return false;
  if (!key.expiresAt) return true;
  return new Date(key.expiresAt).getTime() > Date.now();
};

const toApiCallService = (item: DataServiceAccessOverviewItem): DataServiceApi => ({
  id: item.apiId,
  name: item.name,
  path: item.path,
  runtimePath: item.runtimePath,
  dataSourceId: 0,
  sql: '',
  parameterNames: item.parameterNames || [],
  maxRows: 0,
  timeoutSeconds: 0,
  enabled: item.enabled,
  authMode: item.authMode,
});

const Metric = ({ label, value, note }: { label: string; value: number; note: string }) => (
  <div className="min-w-0 px-4 py-3">
    <div className="text-[11px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 text-[20px] font-semibold tracking-[-.02em] text-[#161823]">{value}</div>
    <div className="mt-0.5 truncate text-[10px] text-[#a0a5ad]">{note}</div>
  </div>
);

export default function DataServiceAccessPage() {
  const [records, setRecords] = useState<DataServiceAccessOverviewItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [authFilter, setAuthFilter] = useState<AuthFilter>('ALL');
  const [ipFilter, setIpFilter] = useState<IpFilter>('ALL');
  const [selected, setSelected] = useState<DataServiceAccessOverviewItem>();
  const [drawerTab, setDrawerTab] = useState<AccessDrawerTab>('AUTH');
  const [keys, setKeys] = useState<DataServiceApiKey[]>([]);
  const [keysLoading, setKeysLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRecords((await listDataServiceAccessOverview()) || []);
    } catch (error: any) {
      message.error(error?.message || '加载数据服务访问控制失败');
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const metrics = useMemo(() => ({
    total: records.length,
    apiKey: records.filter((item) => item.authMode === 'API_KEY').length,
    network: records.filter((item) => item.ipAccessMode !== 'NONE').length,
    dual: records.filter(
      (item) => item.authMode === 'API_KEY' && item.ipAccessMode !== 'NONE',
    ).length,
  }), [records]);

  const filtered = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    return records.filter((item) => {
      if (authFilter !== 'ALL' && item.authMode !== authFilter) return false;
      if (ipFilter !== 'ALL' && item.ipAccessMode !== ipFilter) return false;
      if (!text) return true;
      return item.name.toLowerCase().includes(text)
        || item.path.toLowerCase().includes(text)
        || item.runtimePath.toLowerCase().includes(text);
    });
  }, [authFilter, ipFilter, keyword, records]);

  const patchItem = useCallback((apiId: number, patch: Partial<DataServiceAccessOverviewItem>) => {
    setRecords((current) => current.map((item) =>
      item.apiId === apiId ? { ...item, ...patch } : item));
    setSelected((current) => current?.apiId === apiId
      ? { ...current, ...patch }
      : current);
  }, []);

  const openConfig = async (item: DataServiceAccessOverviewItem) => {
    setSelected(item);
    setDrawerTab('AUTH');
    setKeys([]);
    setKeysLoading(true);
    try {
      setKeys((await listDataServiceKeys(item.apiId)) || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API Key 失败');
    } finally {
      setKeysLoading(false);
    }
  };

  const closeConfig = () => {
    setSelected(undefined);
    setKeys([]);
    void load();
  };

  const handleAuthModeChange = (mode: DataServiceAuthMode) => {
    if (!selected) return;
    patchItem(selected.apiId, { authMode: mode });
  };

  const handleKeysChange = (nextKeys: DataServiceApiKey[]) => {
    setKeys(nextKeys);
    if (!selected) return;
    patchItem(selected.apiId, {
      apiKeyCount: nextKeys.length,
      activeApiKeyCount: nextKeys.filter(isActiveKey).length,
    });
  };

  const columns: TableColumnsType<DataServiceAccessOverviewItem> = [
    {
      title: 'API',
      key: 'api',
      minWidth: 220,
      render: (_, record) => (
        <div className="min-w-0 py-0.5">
          <div className="truncate text-[12px] font-medium text-[#344054]">{record.name}</div>
          <div className="mt-0.5 truncate font-mono text-[10px] text-[#98a2b3]">{record.path}</div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 88,
      render: (enabled: boolean) => (
        <span className="inline-flex items-center gap-1.5 text-[11px] text-[#667085]">
          <span className={`h-1.5 w-1.5 rounded-full ${enabled ? 'bg-[#20c77a]' : 'bg-[#b0b5bd]'}`} />
          {enabled ? '运行中' : '已停用'}
        </span>
      ),
    },
    {
      title: '调用认证',
      key: 'auth',
      width: 165,
      render: (_, record) => (
        <div>
          <div className="text-[11px] font-medium text-[#475467]">
            {record.authMode === 'API_KEY' ? 'API Key' : '公开访问'}
          </div>
          <div className="mt-0.5 text-[10px] text-[#98a2b3]">
            {record.authMode === 'API_KEY'
              ? `${record.activeApiKeyCount}/${record.apiKeyCount} 个有效 Key`
              : `${record.apiKeyCount} 个 Key`}
          </div>
        </div>
      ),
    },
    {
      title: '来源限制',
      key: 'network',
      width: 165,
      render: (_, record) => {
        const active = record.ipAccessMode === 'ALLOWLIST'
          ? record.activeAllowlistRuleCount
          : record.ipAccessMode === 'DENYLIST'
            ? record.activeDenylistRuleCount
            : 0;
        return (
          <div>
            <div className="text-[11px] font-medium text-[#475467]">
              {ipModeLabel[record.ipAccessMode]}
            </div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              {record.ipAccessMode === 'NONE' ? '未启用 IP 限制' : `${active} 条规则生效`}
            </div>
          </div>
        );
      },
    },
    {
      title: '规则',
      key: 'rules',
      width: 130,
      render: (_, record) => (
        <span className="text-[10px] text-[#667085]">
          白 {record.allowlistRuleCount} · 黑 {record.denylistRuleCount}
        </span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 150,
      render: (value?: string | null) => (
        <span className="text-[10px] text-[#98a2b3]">{formatTime(value)}</span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 96,
      fixed: 'right',
      render: (_, record) => (
        <YakButton
          type="text"
          size="small"
          icon={<ShieldCheck size={13} />}
          onClick={() => void openConfig(record)}
        >
          配置
        </YakButton>
      ),
    },
  ];

  const callService = selected ? toApiCallService(selected) : undefined;

  return (
    <div className="h-full overflow-y-auto bg-[#f6f7f8] p-3">
      <div className="min-h-full rounded-[10px] bg-white px-5 pb-5 pt-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="m-0 text-[17px] font-semibold text-[#161823]">访问控制</h1>
            <div className="mt-1 text-[12px] text-[#98a2b3]">
              集中管理 API Key 认证、调用配额与 IP/CIDR 黑白名单
            </div>
          </div>
          <YakButton
            type="text"
            icon={<RefreshCw size={14} />}
            loading={loading}
            onClick={() => void load()}
            className="bg-[#f5f6f7]"
          >
            刷新
          </YakButton>
        </div>

        <div className="my-4 border-t border-[#f0f0f0]" />

        <div className="grid grid-cols-2 divide-x divide-[#f0f0f0] rounded-[8px] bg-[#f8f9fa] md:grid-cols-4">
          <Metric label="数据服务" value={metrics.total} note="当前项目空间" />
          <Metric label="API Key 认证" value={metrics.apiKey} note="需要调用凭证" />
          <Metric label="来源限制" value={metrics.network} note="已启用黑/白名单" />
          <Metric label="双重保护" value={metrics.dual} note="Key + IP 同时启用" />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            allowClear
            variant="filled"
            prefix={<Search size={13} className="text-[#98a2b3]" />}
            placeholder="搜索 API 名称或路径"
            className="w-[280px]"
          />
          <Select<AuthFilter>
            value={authFilter}
            onChange={setAuthFilter}
            variant="filled"
            className="w-[150px]"
            options={[
              { value: 'ALL', label: '全部认证方式' },
              { value: 'NONE', label: '公开访问' },
              { value: 'API_KEY', label: 'API Key' },
            ]}
          />
          <Select<IpFilter>
            value={ipFilter}
            onChange={setIpFilter}
            variant="filled"
            className="w-[150px]"
            options={[
              { value: 'ALL', label: '全部来源策略' },
              { value: 'NONE', label: '不限制' },
              { value: 'ALLOWLIST', label: '白名单' },
              { value: 'DENYLIST', label: '黑名单' },
            ]}
          />
          <div className="ml-auto text-[11px] text-[#98a2b3]">共 {filtered.length} 个 API</div>
        </div>

        <div className="mt-3">
          <Table<DataServiceAccessOverviewItem>
            rowKey="apiId"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={filtered}
            scroll={{ x: 1050 }}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50],
              showTotal: (total) => `共 ${total} 条`,
            }}
            locale={{
              emptyText: (
                <YakEmpty
                  compact
                  title="暂无可管理的数据服务"
                  description="发布数据服务后，可在这里统一配置调用认证和来源访问策略。"
                />
              ),
            }}
            className="[&_.ant-table-container]:!border-t [&_.ant-table-container]:!border-[#f0f0f0] [&_.ant-table-thead>tr>th]:!bg-[#fafafa] [&_.ant-table-thead>tr>th]:!text-[11px] [&_.ant-table-tbody>tr>td]:!py-3"
          />
        </div>
      </div>

      <Drawer
        open={Boolean(selected)}
        width={920}
        onClose={closeConfig}
        title={selected ? (
          <div className="min-w-0">
            <div className="truncate text-[14px] font-semibold text-[#161823]">{selected.name}</div>
            <div className="mt-0.5 truncate font-mono text-[10px] font-normal text-[#98a2b3]">
              {selected.runtimePath}
            </div>
          </div>
        ) : '访问控制'}
      >
        {selected ? (
          <div className="-mt-3">
            <YakTab
              activeKey={drawerTab}
              onChange={(key) => setDrawerTab(key as AccessDrawerTab)}
              items={[
                { key: 'AUTH', label: '调用认证' },
                { key: 'IP', label: 'IP 黑白名单' },
              ]}
            />

            <div className="mt-3 rounded-lg bg-[#f7f7f8] p-3">
              {drawerTab === 'AUTH' ? (
                keysLoading || !callService ? (
                  <div className="flex min-h-[360px] items-center justify-center rounded-lg bg-white">
                    <Spin />
                  </div>
                ) : (
                  <DataServiceApiCallPanel
                    service={callService}
                    keys={keys}
                    canManageAccess
                    onAuthModeChange={handleAuthModeChange}
                    onKeysChange={handleKeysChange}
                  />
                )
              ) : (
                <DataServiceAccessControlPanel key={selected.apiId} apiId={selected.apiId} />
              )}
            </div>

            <div className="mt-3 flex items-start gap-2 rounded-lg bg-[#f7f7f8] px-3 py-2.5 text-[10px] leading-5 text-[#667085]">
              {drawerTab === 'AUTH' ? <KeyRound size={13} className="mt-1 shrink-0" /> : <Network size={13} className="mt-1 shrink-0" />}
              <span>
                {drawerTab === 'AUTH'
                  ? 'API Key 与每分钟调用上限属于调用身份策略；切换认证方式不会改变已发布 SQL。'
                  : '来源 IP 策略会在 API Key 鉴权和限流之前执行，命中拒绝规则时直接返回 403。'}
              </span>
            </div>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
