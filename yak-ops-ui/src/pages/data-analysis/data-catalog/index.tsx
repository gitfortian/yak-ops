import { BRAND_THEME } from '@/styles/brand';
import type { ColumnsType } from 'antd/es/table';
import {
  Button,
  ConfigProvider,
  Input,
  Progress,
  Segmented,
  Select,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import {
  BarChart3,
  Bookmark,
  ChevronRight,
  Clock3,
  Database,
  FolderTree,
  GitBranch,
  Layers3,
  MoreHorizontal,
  RefreshCw,
  Rows3,
  Search,
  ShieldCheck,
  Star,
  TableProperties,
  UserRound,
} from 'lucide-react';
import { useMemo, useState } from 'react';

type DatasetSourceType = 'SQL' | 'Flink' | 'Table';

type DatasetRow = {
  id: string;
  name: string;
  description: string;
  sourceType: DatasetSourceType;
  sourceName: string;
  domain: string;
  tags: string[];
  fieldCount: number;
  dimensionCount: number;
  metricCount: number;
  rowCount: number;
  refreshMode: string;
  updatedAt: string;
  qualityScore: number;
  owner: string;
  chartCount: number;
  dashboardCount: number;
  mine?: boolean;
  favorite?: boolean;
  recent?: boolean;
};

const datasets: DatasetRow[] = [
  {
    id: 'ds-order-operation',
    name: '订单经营分析',
    description: '面向经营分析的订单、销售额、利润和区域汇总数据。',
    sourceType: 'SQL',
    sourceName: '订单聚合 SQL',
    domain: '电商业务',
    tags: ['经营分析', 'T+1'],
    fieldCount: 18,
    dimensionCount: 8,
    metricCount: 10,
    rowCount: 2102331,
    refreshMode: '每天 01:00',
    updatedAt: '10 分钟前',
    qualityScore: 98,
    owner: '张三',
    chartCount: 6,
    dashboardCount: 2,
    mine: true,
    favorite: true,
    recent: true,
  },
  {
    id: 'ds-user-growth',
    name: '用户增长分析',
    description: '注册、活跃、留存和渠道转化等用户增长核心指标。',
    sourceType: 'Flink',
    sourceName: '用户行为实时聚合',
    domain: '用户增长',
    tags: ['实时', '用户'],
    fieldCount: 26,
    dimensionCount: 12,
    metricCount: 14,
    rowCount: 8564012,
    refreshMode: '实时更新',
    updatedAt: '刚刚',
    qualityScore: 96,
    owner: '李四',
    chartCount: 9,
    dashboardCount: 3,
    favorite: true,
    recent: true,
  },
  {
    id: 'ds-quality-daily',
    name: '数据质量日报',
    description: '汇总数据质量监控结果、异常规则和每日质量趋势。',
    sourceType: 'SQL',
    sourceName: 'quality_daily_summary',
    domain: '数据质量',
    tags: ['质量', '日报'],
    fieldCount: 15,
    dimensionCount: 7,
    metricCount: 8,
    rowCount: 128430,
    refreshMode: '每小时',
    updatedAt: '18 分钟前',
    qualityScore: 100,
    owner: '王五',
    chartCount: 4,
    dashboardCount: 1,
    mine: true,
    recent: true,
  },
  {
    id: 'ds-task-operation',
    name: '任务运行概览',
    description: '离线同步、开发任务和工作流实例的运行统计。',
    sourceType: 'Table',
    sourceName: 'yak_task_execution_summary',
    domain: '平台运维',
    tags: ['运维', '任务'],
    fieldCount: 22,
    dimensionCount: 10,
    metricCount: 12,
    rowCount: 642190,
    refreshMode: '每 10 分钟',
    updatedAt: '6 分钟前',
    qualityScore: 97,
    owner: '赵六',
    chartCount: 7,
    dashboardCount: 2,
    favorite: true,
  },
  {
    id: 'ds-channel-conversion',
    name: '渠道转化分析',
    description: '按渠道、地区和日期统计访问、转化与投入产出。',
    sourceType: 'SQL',
    sourceName: '渠道转化宽表 SQL',
    domain: '市场营销',
    tags: ['渠道', '转化'],
    fieldCount: 20,
    dimensionCount: 11,
    metricCount: 9,
    rowCount: 3268120,
    refreshMode: '每天 02:00',
    updatedAt: '2 小时前',
    qualityScore: 94,
    owner: '陈晨',
    chartCount: 5,
    dashboardCount: 2,
    recent: true,
  },
  {
    id: 'ds-customer-service',
    name: '客户服务分析',
    description: '服务工单、响应时长、处理结果和满意度分析数据。',
    sourceType: 'Table',
    sourceName: 'customer_service_fact',
    domain: '客户服务',
    tags: ['客服', '服务'],
    fieldCount: 17,
    dimensionCount: 9,
    metricCount: 8,
    rowCount: 986420,
    refreshMode: '每小时',
    updatedAt: '42 分钟前',
    qualityScore: 92,
    owner: '周敏',
    chartCount: 3,
    dashboardCount: 1,
  },
  {
    id: 'ds-cost-analysis',
    name: '平台成本分析',
    description: '计算资源、存储和任务运行成本的统一分析数据。',
    sourceType: 'SQL',
    sourceName: '成本归集 SQL',
    domain: '财务成本',
    tags: ['成本', '月度'],
    fieldCount: 14,
    dimensionCount: 6,
    metricCount: 8,
    rowCount: 86420,
    refreshMode: '每天 03:00',
    updatedAt: '3 小时前',
    qualityScore: 99,
    owner: '孙悦',
    chartCount: 2,
    dashboardCount: 1,
    mine: true,
  },
  {
    id: 'ds-source-assets',
    name: '数据源资产概览',
    description: '数据源、数据库、Schema 和表资产的基础统计数据。',
    sourceType: 'Table',
    sourceName: 'resource_asset_snapshot',
    domain: '数据资产',
    tags: ['资产', '元数据'],
    fieldCount: 16,
    dimensionCount: 10,
    metricCount: 6,
    rowCount: 35280,
    refreshMode: '每天 00:30',
    updatedAt: '5 小时前',
    qualityScore: 95,
    owner: '刘洋',
    chartCount: 2,
    dashboardCount: 1,
  },
];

const domainItems = [
  { key: 'all', label: '全部数据', icon: Database },
  { key: '电商业务', label: '电商业务', icon: FolderTree },
  { key: '用户增长', label: '用户增长', icon: FolderTree },
  { key: '数据质量', label: '数据质量', icon: FolderTree },
  { key: '平台运维', label: '平台运维', icon: FolderTree },
  { key: '市场营销', label: '市场营销', icon: FolderTree },
  { key: '客户服务', label: '客户服务', icon: FolderTree },
  { key: '财务成本', label: '财务成本', icon: FolderTree },
  { key: '数据资产', label: '数据资产', icon: FolderTree },
];

const sourceTypeLabel: Record<DatasetSourceType, string> = {
  SQL: 'SQL 任务',
  Flink: 'Flink 任务',
  Table: '数据表',
};

const formatRows = (value: number) => {
  if (value >= 10000000) return `${(value / 10000000).toFixed(1)} 千万`;
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 1000000 ? 0 : 1)} 万`;
  return value.toLocaleString();
};

const DataCatalogPage = () => {
  const [scope, setScope] = useState<string>('all');
  const [activeDomain, setActiveDomain] = useState('all');
  const [keyword, setKeyword] = useState('');
  const [sourceType, setSourceType] = useState<string>('all');
  const [owner, setOwner] = useState<string>('all');
  const [selectedId, setSelectedId] = useState(datasets[0].id);

  const filteredData = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return datasets.filter((item) => {
      if (scope === 'mine' && !item.mine) return false;
      if (scope === 'favorite' && !item.favorite) return false;
      if (scope === 'recent' && !item.recent) return false;
      if (activeDomain !== 'all' && item.domain !== activeDomain) return false;
      if (sourceType !== 'all' && item.sourceType !== sourceType) return false;
      if (owner !== 'all' && item.owner !== owner) return false;
      if (!normalizedKeyword) return true;
      return [
        item.name,
        item.description,
        item.sourceName,
        item.domain,
        ...item.tags,
      ].some((value) => value.toLowerCase().includes(normalizedKeyword));
    });
  }, [activeDomain, keyword, owner, scope, sourceType]);

  const selectedDataset =
    datasets.find((item) => item.id === selectedId) ?? filteredData[0] ?? datasets[0];

  const columns: ColumnsType<DatasetRow> = [
    {
      title: '数据集',
      dataIndex: 'name',
      width: 310,
      render: (_, record) => (
        <div className="min-w-0 py-1">
          <div className="flex items-center gap-2">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f4f5f7] text-[#5b616e]">
              <TableProperties size={14} />
            </div>
            <button
              type="button"
              className="truncate border-0 bg-transparent p-0 text-left text-[14px] font-medium text-[#161823] hover:underline"
              onClick={() => setSelectedId(record.id)}
            >
              {record.name}
            </button>
            {record.favorite ? <Star size={13} className="shrink-0 fill-[#f5a524] text-[#f5a524]" /> : null}
          </div>
          <div className="ml-9 mt-1 truncate text-[12px] text-[#8a8f99]">
            {record.description}
          </div>
          <div className="ml-9 mt-1.5 flex gap-1">
            {record.tags.map((tag) => (
              <Tag key={tag} bordered={false} className="m-0 bg-[#f5f6f7] text-[11px] text-[#676d78]">
                {tag}
              </Tag>
            ))}
          </div>
        </div>
      ),
    },
    {
      title: '来源',
      width: 160,
      render: (_, record) => (
        <div>
          <div className="text-[13px] text-[#30343b]">{sourceTypeLabel[record.sourceType]}</div>
          <div className="mt-1 max-w-[150px] truncate text-[12px] text-[#90949c]" title={record.sourceName}>
            {record.sourceName}
          </div>
        </div>
      ),
    },
    {
      title: '业务域',
      dataIndex: 'domain',
      width: 105,
      render: (value: string) => <span className="text-[13px] text-[#4a4f57]">{value}</span>,
    },
    {
      title: '规模',
      width: 120,
      render: (_, record) => (
        <div className="space-y-1 text-[12px] text-[#676d78]">
          <div>{record.fieldCount} 个字段</div>
          <div>{formatRows(record.rowCount)} 行</div>
        </div>
      ),
    },
    {
      title: '更新',
      width: 125,
      render: (_, record) => (
        <div>
          <div className="text-[12px] text-[#4a4f57]">{record.refreshMode}</div>
          <div className="mt-1 flex items-center gap-1 text-[12px] text-[#92969f]">
            <Clock3 size={12} />
            {record.updatedAt}
          </div>
        </div>
      ),
    },
    {
      title: '质量',
      width: 88,
      render: (_, record) => (
        <div className="flex items-center gap-1.5 text-[13px] text-[#30343b]">
          <ShieldCheck size={14} className="text-[#6d7480]" />
          {record.qualityScore}
        </div>
      ),
    },
    {
      title: '消费情况',
      width: 120,
      render: (_, record) => (
        <div className="space-y-1 text-[12px] text-[#676d78]">
          <div>{record.chartCount} 个图表</div>
          <div>{record.dashboardCount} 个仪表盘</div>
        </div>
      ),
    },
    {
      title: '负责人',
      dataIndex: 'owner',
      width: 90,
      render: (value: string) => (
        <div className="flex items-center gap-1.5 text-[12px] text-[#4a4f57]">
          <UserRound size={13} />
          {value}
        </div>
      ),
    },
    {
      title: '操作',
      fixed: 'right',
      width: 128,
      render: (_, record) => (
        <div className="flex items-center gap-1">
          <Button
            type="link"
            size="small"
            href="/data-analysis/chart-analysis"
            onClick={(event) => event.stopPropagation()}
          >
            创建分析
          </Button>
          <Tooltip title="更多">
            <Button
              type="text"
              size="small"
              icon={<MoreHorizontal size={15} />}
              onClick={(event) => event.stopPropagation()}
            />
          </Tooltip>
        </div>
      ),
    },
  ];

  const domainCount = (domain: string) =>
    domain === 'all' ? datasets.length : datasets.filter((item) => item.domain === domain).length;

  const resetFilters = () => {
    setKeyword('');
    setSourceType('all');
    setOwner('all');
    setActiveDomain('all');
    setScope('all');
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[650px] flex-col overflow-hidden bg-white text-[#161823]">
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-[#e8e9ec] px-5">
          <div>
            <h1 className="m-0 text-[20px] font-semibold">数据目录</h1>
            <div className="mt-0.5 text-[12px] text-[#8a8f99]">发现、理解并消费已发布的数据集</div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-[12px] text-[#8a8f99]">共 {datasets.length} 个可分析数据集</span>
            <Button icon={<RefreshCw size={14} />}>刷新</Button>
          </div>
        </div>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <aside className="flex w-[218px] shrink-0 flex-col border-r border-[#e8e9ec] bg-[#fbfbfc]">
            <div className="border-b border-[#eceef1] p-3">
              <Input
                allowClear
                value={keyword}
                prefix={<Search size={14} className="text-[#a0a4ac]" />}
                placeholder="搜索数据"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-2">
              <div className="px-2 pb-2 pt-1 text-[12px] font-medium text-[#8a8f99]">数据范围</div>
              {[
                { key: 'all', label: '全部数据', icon: Layers3, count: datasets.length },
                { key: 'mine', label: '我的数据', icon: UserRound, count: datasets.filter((item) => item.mine).length },
                { key: 'favorite', label: '我的收藏', icon: Bookmark, count: datasets.filter((item) => item.favorite).length },
                { key: 'recent', label: '最近使用', icon: Clock3, count: datasets.filter((item) => item.recent).length },
              ].map((item) => {
                const Icon = item.icon;
                const active = scope === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setScope(item.key)}
                    className={`mb-0.5 flex h-9 w-full items-center rounded-md border-0 px-2.5 text-left text-[13px] transition-colors ${
                      active ? 'bg-white font-medium text-[#161823] shadow-sm' : 'bg-transparent text-[#5f6570] hover:bg-white'
                    }`}
                  >
                    <Icon size={14} className="mr-2" />
                    <span className="flex-1">{item.label}</span>
                    <span className="text-[11px] text-[#a0a4ac]">{item.count}</span>
                  </button>
                );
              })}

              <div className="mt-4 px-2 pb-2 text-[12px] font-medium text-[#8a8f99]">业务域</div>
              {domainItems.map((item) => {
                const Icon = item.icon;
                const active = activeDomain === item.key;
                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setActiveDomain(item.key)}
                    className={`mb-0.5 flex h-8 w-full items-center rounded-md border-0 px-2.5 text-left text-[13px] transition-colors ${
                      active ? 'bg-[#f0f1f3] font-medium text-[#161823]' : 'bg-transparent text-[#676d78] hover:bg-[#f4f5f6]'
                    }`}
                  >
                    <Icon size={13} className="mr-2" />
                    <span className="flex-1 truncate">{item.label}</span>
                    <span className="text-[11px] text-[#a0a4ac]">{domainCount(item.key)}</span>
                  </button>
                );
              })}
            </div>
          </aside>

          <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
            <div className="shrink-0 border-b border-[#e8e9ec] bg-white px-4 py-3">
              <div className="flex items-center justify-between gap-4">
                <Segmented
                  size="small"
                  value={scope}
                  onChange={(value) => setScope(String(value))}
                  options={[
                    { label: '全部', value: 'all' },
                    { label: '我的', value: 'mine' },
                    { label: '收藏', value: 'favorite' },
                    { label: '最近使用', value: 'recent' },
                  ]}
                />

                <div className="flex min-w-0 flex-1 justify-end gap-2">
                  <Input
                    allowClear
                    value={keyword}
                    prefix={<Search size={14} className="text-[#a0a4ac]" />}
                    placeholder="搜索数据集、字段描述、来源任务"
                    className="max-w-[340px]"
                    onChange={(event) => setKeyword(event.target.value)}
                  />
                  <Select
                    value={sourceType}
                    className="w-[118px]"
                    onChange={setSourceType}
                    options={[
                      { label: '全部来源', value: 'all' },
                      { label: 'SQL 任务', value: 'SQL' },
                      { label: 'Flink 任务', value: 'Flink' },
                      { label: '数据表', value: 'Table' },
                    ]}
                  />
                  <Select
                    value={owner}
                    className="w-[112px]"
                    onChange={setOwner}
                    options={[
                      { label: '全部负责人', value: 'all' },
                      ...Array.from(new Set(datasets.map((item) => item.owner))).map((value) => ({
                        label: value,
                        value,
                      })),
                    ]}
                  />
                  <Button onClick={resetFilters}>重置</Button>
                </div>
              </div>
            </div>

            <div className="flex min-h-0 flex-1 overflow-hidden">
              <section className="min-w-0 flex-1 overflow-hidden p-4 pr-3">
                <div className="mb-2 flex h-7 items-center justify-between">
                  <div className="text-[12px] text-[#8a8f99]">
                    找到 <span className="font-medium text-[#30343b]">{filteredData.length}</span> 个数据集
                  </div>
                  <div className="text-[12px] text-[#a0a4ac]">点击数据集查看详情</div>
                </div>

                <Table<DatasetRow>
                  rowKey="id"
                  size="small"
                  bordered
                  columns={columns}
                  dataSource={filteredData}
                  scroll={{ x: 1240, y: 'calc(100vh - 278px)' }}
                  pagination={{
                    pageSize: 8,
                    showSizeChanger: false,
                    showTotal: (total) => `共 ${total} 条`,
                  }}
                  locale={{ emptyText: '暂无匹配的数据集' }}
                  onRow={(record) => ({
                    onClick: () => setSelectedId(record.id),
                    style: { cursor: 'pointer' },
                  })}
                />
              </section>

              <aside className="w-[294px] shrink-0 overflow-y-auto border-l border-[#e8e9ec] bg-[#fcfcfd]">
                <div className="p-4">
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-[#e5e7eb] bg-white text-[#59606c]">
                      <TableProperties size={19} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[15px] font-semibold text-[#161823]">{selectedDataset.name}</div>
                      <div className="mt-1 text-[12px] leading-5 text-[#8a8f99]">{selectedDataset.description}</div>
                    </div>
                  </div>

                  <div className="mt-3 flex flex-wrap gap-1">
                    <Tag bordered={false} className="m-0 bg-[#f0f1f3] text-[#5f6570]">
                      {selectedDataset.domain}
                    </Tag>
                    {selectedDataset.tags.map((tag) => (
                      <Tag key={tag} bordered={false} className="m-0 bg-[#f5f6f7] text-[#737984]">
                        {tag}
                      </Tag>
                    ))}
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-2">
                    <Button type="primary" icon={<BarChart3 size={14} />} href="/data-analysis/chart-analysis">
                      创建分析
                    </Button>
                    <Button icon={<GitBranch size={14} />}>查看来源</Button>
                  </div>
                </div>

                <div className="border-t border-[#eceef1] p-4">
                  <div className="mb-3 text-[13px] font-medium text-[#30343b]">数据概况</div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                    <div>
                      <div className="text-[11px] text-[#9a9fa8]">字段</div>
                      <div className="mt-1 flex items-center gap-1 text-[13px] font-medium text-[#30343b]">
                        <Rows3 size={13} /> {selectedDataset.fieldCount}
                      </div>
                    </div>
                    <div>
                      <div className="text-[11px] text-[#9a9fa8]">数据量</div>
                      <div className="mt-1 text-[13px] font-medium text-[#30343b]">{formatRows(selectedDataset.rowCount)} 行</div>
                    </div>
                    <div>
                      <div className="text-[11px] text-[#9a9fa8]">维度</div>
                      <div className="mt-1 text-[13px] font-medium text-[#30343b]">{selectedDataset.dimensionCount}</div>
                    </div>
                    <div>
                      <div className="text-[11px] text-[#9a9fa8]">指标</div>
                      <div className="mt-1 text-[13px] font-medium text-[#30343b]">{selectedDataset.metricCount}</div>
                    </div>
                  </div>
                </div>

                <div className="border-t border-[#eceef1] p-4">
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-[13px] font-medium text-[#30343b]">数据质量</span>
                    <span className="text-[12px] font-medium text-[#4f5661]">{selectedDataset.qualityScore} 分</span>
                  </div>
                  <Progress
                    percent={selectedDataset.qualityScore}
                    showInfo={false}
                    size="small"
                    strokeColor="#8b929d"
                    trailColor="#eceef1"
                  />
                  <div className="mt-2 flex items-center gap-1.5 text-[12px] text-[#7d838d]">
                    <ShieldCheck size={13} /> 最近质量检测正常
                  </div>
                </div>

                <div className="border-t border-[#eceef1] p-4">
                  <div className="mb-3 text-[13px] font-medium text-[#30343b]">来源与更新</div>
                  <div className="space-y-3 text-[12px]">
                    <div>
                      <div className="text-[#9a9fa8]">来源任务</div>
                      <div className="mt-1 flex items-center gap-1.5 text-[#4f5661]">
                        <GitBranch size={13} />
                        <span className="truncate">{selectedDataset.sourceName}</span>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <div className="text-[#9a9fa8]">更新策略</div>
                        <div className="mt-1 text-[#4f5661]">{selectedDataset.refreshMode}</div>
                      </div>
                      <div>
                        <div className="text-[#9a9fa8]">最近更新</div>
                        <div className="mt-1 text-[#4f5661]">{selectedDataset.updatedAt}</div>
                      </div>
                    </div>
                    <div>
                      <div className="text-[#9a9fa8]">负责人</div>
                      <div className="mt-1 flex items-center gap-1.5 text-[#4f5661]">
                        <UserRound size={13} /> {selectedDataset.owner}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="border-t border-[#eceef1] p-4">
                  <div className="mb-3 text-[13px] font-medium text-[#30343b]">消费情况</div>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="rounded-md border border-[#e7e9ec] bg-white p-3">
                      <div className="text-[18px] font-semibold text-[#30343b]">{selectedDataset.chartCount}</div>
                      <div className="mt-1 text-[11px] text-[#969ba4]">关联图表</div>
                    </div>
                    <div className="rounded-md border border-[#e7e9ec] bg-white p-3">
                      <div className="text-[18px] font-semibold text-[#30343b]">{selectedDataset.dashboardCount}</div>
                      <div className="mt-1 text-[11px] text-[#969ba4]">关联仪表盘</div>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="mt-3 flex w-full items-center justify-between border-0 bg-transparent p-0 text-[12px] text-[#666d78] hover:text-[#161823]"
                  >
                    查看完整消费血缘
                    <ChevronRight size={14} />
                  </button>
                </div>
              </aside>
            </div>
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default DataCatalogPage;
