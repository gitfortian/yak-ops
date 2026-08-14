import { history } from '@umijs/max';
import {
  Button,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Table,
  message,
  type TableColumnsType,
} from 'antd';
import { LayoutDashboard, Pencil, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  deleteDashboard,
  fetchDashboard,
  fetchDashboards,
  saveDashboardVersion,
  toDashboardDocument,
} from './dashboard-service';
import type { DashboardSummary } from './model';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

export default function DashboardListPage() {
  const [dashboards, setDashboards] = useState<DashboardSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [renameTarget, setRenameTarget] = useState<DashboardSummary>();
  const [renameValue, setRenameValue] = useState('');
  const [renaming, setRenaming] = useState(false);

  const loadDashboards = useCallback(async () => {
    setLoading(true);
    try {
      setDashboards(await fetchDashboards());
    } catch (error) {
      setDashboards([]);
      message.error(error instanceof Error ? error.message : '加载仪表盘失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboards();
  }, [loadDashboards]);

  useEffect(() => {
    setPage(1);
  }, [keyword]);

  const filteredDashboards = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return dashboards;
    return dashboards.filter((dashboard) => [dashboard.name, dashboard.description, dashboard.id]
      .some((field) => String(field || '').toLowerCase().includes(value)));
  }, [dashboards, keyword]);

  const pageCount = Math.max(1, Math.ceil(filteredDashboards.length / pageSize));
  const currentPage = Math.min(page, pageCount);
  const pageItems = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredDashboards.slice(start, start + pageSize);
  }, [currentPage, filteredDashboards, pageSize]);

  const openRename = (dashboard: DashboardSummary) => {
    setRenameTarget(dashboard);
    setRenameValue(dashboard.name);
  };

  const renameDashboard = async () => {
    const name = renameValue.trim();
    if (!renameTarget || !name) return void message.warning('请输入仪表盘名称');
    if (name === renameTarget.name) {
      setRenameTarget(undefined);
      return;
    }

    setRenaming(true);
    try {
      const detail = await fetchDashboard(renameTarget.id);
      const document = toDashboardDocument(detail);
      await saveDashboardVersion(renameTarget.id, { ...document, name });
      message.success('仪表盘名称已更新');
      setRenameTarget(undefined);
      await loadDashboards();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '重命名仪表盘失败');
    } finally {
      setRenaming(false);
    }
  };

  const removeDashboard = async (dashboard: DashboardSummary) => {
    try {
      await deleteDashboard(dashboard.id);
      message.success('仪表盘已删除');
      await loadDashboards();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除仪表盘失败');
    }
  };

  const columns: TableColumnsType<DashboardSummary> = [
    {
      title: '仪表盘',
      dataIndex: 'name',
      minWidth: 320,
      render: (_, record) => (
        <div className="min-w-0 py-0.5">
          <button
            type="button"
            className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[13px] font-medium text-[#161823] hover:underline"
            onClick={() => history.push(`/dashboard/${record.id}`)}
          >
            {record.name}
          </button>
          <div className="mt-1 max-w-[520px] truncate text-[11px] text-[#98a2b3]">
            {record.description || '暂无描述'}
          </div>
        </div>
      ),
    },
    {
      title: '当前版本',
      dataIndex: 'currentVersionNo',
      width: 120,
      render: (value: number) => (
        <span className="rounded-[3px] bg-[#f5f6f7] px-2 py-0.5 text-[11px] text-[#667085]">
          {value ? `V${value}` : '未保存'}
        </span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (value?: string) => <span className="text-[12px] text-[#667085]">{formatTime(value)}</span>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => <span className="text-[12px] text-[#667085]">{formatTime(value)}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      fixed: 'right',
      render: (_, record) => (
        <div className="flex items-center gap-1">
          <Button
            size="small"
            type="text"
            icon={<Pencil size={12} />}
            onClick={() => history.push(`/dashboard/${record.id}`)}
          >
            编辑
          </Button>
          <Button size="small" type="text" onClick={() => openRename(record)}>
            重命名
          </Button>
          <Popconfirm
            title="删除仪表盘"
            description={`确认删除“${record.name}”？此操作不可恢复。`}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => void removeDashboard(record)}
          >
            <Button size="small" type="text" danger icon={<Trash2 size={12} />}>
              删除
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100vh-48px)] bg-white px-5 py-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-[16px] font-semibold text-[#161823]">
            <LayoutDashboard size={17} />
            仪表盘
          </div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            管理数据消费仪表盘，进入画布后完成图表配置与排版。
          </div>
        </div>
        <Button
          type="primary"
          size="small"
          icon={<Plus size={13} />}
          onClick={() => history.push('/dashboard/new')}
        >
          新建仪表盘
        </Button>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3 border-y border-[#edf0f3] py-2.5">
        <Input
          allowClear
          size="small"
          className="w-[320px]"
          prefix={<Search size={13} className="text-[#98a2b3]" />}
          placeholder="搜索仪表盘名称、描述或 ID"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Button
          size="small"
          type="text"
          icon={<RefreshCw size={13} />}
          loading={loading}
          onClick={() => void loadDashboards()}
        >
          刷新
        </Button>
      </div>

      <Table<DashboardSummary>
        rowKey="id"
        size="small"
        pagination={false}
        loading={loading}
        dataSource={pageItems}
        columns={columns}
        scroll={{ x: 1050 }}
        className="mt-3"
        locale={{ emptyText: keyword ? '没有匹配的仪表盘' : '暂无仪表盘，点击右上角新建' }}
      />

      <div className="mt-3 flex items-center justify-between border-t border-[#edf0f3] pt-3">
        <span className="text-[11px] text-[#98a2b3]">共 {filteredDashboards.length} 个仪表盘</span>
        <Pagination
          size="small"
          current={currentPage}
          pageSize={pageSize}
          total={filteredDashboards.length}
          showSizeChanger
          pageSizeOptions={[10, 20, 50]}
          onChange={(nextPage, nextPageSize) => {
            setPage(nextPageSize === pageSize ? nextPage : 1);
            setPageSize(nextPageSize);
          }}
        />
      </div>

      <Modal
        title="重命名仪表盘"
        open={Boolean(renameTarget)}
        okText="保存"
        cancelText="取消"
        confirmLoading={renaming}
        onOk={() => void renameDashboard()}
        onCancel={() => !renaming && setRenameTarget(undefined)}
      >
        <Input
          autoFocus
          maxLength={128}
          value={renameValue}
          placeholder="请输入仪表盘名称"
          onChange={(event) => setRenameValue(event.target.value)}
          onPressEnter={() => void renameDashboard()}
        />
      </Modal>
    </div>
  );
}
