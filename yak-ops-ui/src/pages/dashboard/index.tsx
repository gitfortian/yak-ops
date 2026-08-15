import { history } from '@umijs/max';
import {
  Button,
  Divider,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  message,
} from 'antd';
import {
  CalendarDays,
  ChevronDown,
  Divide,
  LayoutDashboard,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  deleteDashboard,
  fetchDashboard,
  fetchDashboards,
  saveDashboardVersion,
  toDashboardDocument,
} from './dashboard-service';
import type { DashboardSummary } from './model';

type DashboardStatus = 'all' | 'configured' | 'unsaved';
type TimeRange = 'all' | '7d' | '30d';

const formatTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

const formatDate = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 10) : '-';

const getTime = (value?: string) => {
  if (!value) return 0;

  const time = new Date(value).getTime();

  return Number.isNaN(time) ? 0 : time;
};

export default function DashboardListPage() {
  const [dashboards, setDashboards] = useState<DashboardSummary[]>([]);
  const [loading, setLoading] = useState(true);

  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<DashboardStatus>('all');
  const [timeRange, setTimeRange] = useState<TimeRange>('all');

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
      message.error(
        error instanceof Error ? error.message : '加载仪表盘失败',
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboards();
  }, [loadDashboards]);

  useEffect(() => {
    setPage(1);
  }, [keyword, status, timeRange]);

  const configuredCount = useMemo(
    () =>
      dashboards.filter(
        (item) => Number(item.currentVersionNo) > 0,
      ).length,
    [dashboards],
  );

  const unsavedCount = dashboards.length - configuredCount;

  const filteredDashboards = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    const now = Date.now();

    return dashboards.filter((dashboard) => {
      if (
        value &&
        ![
          dashboard.name,
          dashboard.description,
          dashboard.id,
        ].some((field) =>
          String(field || '')
            .toLowerCase()
            .includes(value),
        )
      ) {
        return false;
      }

      const hasVersion =
        Number(dashboard.currentVersionNo) > 0;

      if (status === 'configured' && !hasVersion) {
        return false;
      }

      if (status === 'unsaved' && hasVersion) {
        return false;
      }

      if (timeRange !== 'all') {
        const updateTime = getTime(dashboard.updateTime);

        if (!updateTime) {
          return false;
        }

        const days = timeRange === '7d' ? 7 : 30;
        const range = days * 24 * 60 * 60 * 1000;

        if (now - updateTime > range) {
          return false;
        }
      }

      return true;
    });
  }, [dashboards, keyword, status, timeRange]);

  const pageCount = Math.max(
    1,
    Math.ceil(filteredDashboards.length / pageSize),
  );

  const currentPage = Math.min(page, pageCount);

  const pageItems = useMemo(() => {
    const start = (currentPage - 1) * pageSize;

    return filteredDashboards.slice(
      start,
      start + pageSize,
    );
  }, [currentPage, filteredDashboards, pageSize]);

  const openRename = (dashboard: DashboardSummary) => {
    setRenameTarget(dashboard);
    setRenameValue(dashboard.name);
  };

  const renameDashboard = async () => {
    const name = renameValue.trim();

    if (!renameTarget || !name) {
      message.warning('请输入仪表盘名称');
      return;
    }

    if (name === renameTarget.name) {
      setRenameTarget(undefined);
      return;
    }

    setRenaming(true);

    try {
      const detail = await fetchDashboard(renameTarget.id);
      const document = toDashboardDocument(detail);

      await saveDashboardVersion(renameTarget.id, {
        ...document,
        name,
      });

      message.success('仪表盘名称已更新');

      setRenameTarget(undefined);

      await loadDashboards();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : '重命名仪表盘失败',
      );
    } finally {
      setRenaming(false);
    }
  };

  const removeDashboard = async (
    dashboard: DashboardSummary,
  ) => {
    try {
      await deleteDashboard(dashboard.id);

      message.success('仪表盘已删除');

      await loadDashboards();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : '删除仪表盘失败',
      );
    }
  };

  const statusItems: Array<{
    key: DashboardStatus;
    label: string;
    count?: number;
  }> = [
    {
      key: 'all',
      label: '全部',
      count: dashboards.length,
    },
    {
      key: 'configured',
      label: '已配置',
      count: configuredCount,
    },
    {
      key: 'unsaved',
      label: '未保存',
      count: unsavedCount,
    },
  ];

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#f6f7f8] p-2">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        {/* 页面标题 */}
        <div className="text-[18px] font-semibold leading-7 text-[#161823]">
          仪表盘管理
        </div>

        {/* 顶部筛选区域 */}
        <div className="mt-2 flex min-h-[48px] items-center justify-between gap-5 ">
          {/* 左侧分类 */}
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              className="h-9 rounded-[8px] border-0 bg-[#f0f1f2] px-4 text-[13px] font-medium text-[#161823]"
            >
              仪表盘 ({dashboards.length})
            </button>
          </div>

          {/* 右侧筛选 */}
          <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
            <div className="mr-1 flex shrink-0 items-center">
              {statusItems.map((item) => {
                const active = status === item.key;

                return (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => setStatus(item.key)}
                    className={[
                      'relative h-9 border-0 bg-transparent px-3 text-[13px] transition-colors',
                      active
                        ? 'font-semibold text-[#161823]'
                        : 'font-normal text-[#8a9099] hover:text-[#444950]',
                    ].join(' ')}
                  >
                    {item.label}
                  </button>
                );
              })}
            </div>

            <Select<TimeRange>
              value={timeRange}
              onChange={setTimeRange}
              suffixIcon={
                <ChevronDown size={14} />
              }
              options={[
                {
                  value: 'all',
                  label: '所有时间',
                },
                {
                  value: '7d',
                  label: '最近 7 天',
                },
                {
                  value: '30d',
                  label: '最近 30 天',
                },
              ]}
              className="w-[122px]"
              size="middle"
              variant="filled"
              prefix={
                <CalendarDays
                  size={13}
                  className="text-[#667085]"
                />
              }
            />

            <Input
              allowClear
              value={keyword}
              onChange={(event) =>
                setKeyword(event.target.value)
              }
              prefix={
                <Search
                  size={15}
                  className="text-[#8a9099]"
                />
              }
              placeholder="搜索仪表盘"
              className="w-[180px]"
              size="middle"
              variant="filled"
            />

            <Button
              size="middle"
              type="text"
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={() => void loadDashboards()}
              className="bg-[#f5f6f7]"
            >
              刷新
            </Button>

            <Button
              type="primary"
              size="middle"
              icon={<Plus size={15} />}
              onClick={() =>
                history.push('/dashboard/new')
              }
            >
              新建仪表盘
            </Button>
          </div>
        </div>

        <Divider style={{padding:0, margin: "12px 0"}}/>

        {/* 仪表盘列表 */}
        <div className="relative">
          {loading && dashboards.length === 0 ? (
            <div className="flex h-[300px] items-center justify-center text-[13px] text-[#98a2b3]">
              正在加载仪表盘...
            </div>
          ) : pageItems.length === 0 ? (
            /* 空状态 */
            <div className="flex h-[360px] flex-col items-center justify-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-[12px] bg-[#f5f6f7] text-[#98a2b3]">
                <LayoutDashboard
                  size={22}
                  strokeWidth={1.7}
                />
              </div>

              <div className="mt-3 text-[13px] font-medium text-[#667085]">
                {keyword ||
                status !== 'all' ||
                timeRange !== 'all'
                  ? '没有匹配的仪表盘'
                  : '暂无仪表盘'}
              </div>

              {!keyword &&
                status === 'all' &&
                timeRange === 'all' && (
                  <Button
                    type="link"
                    className="mt-1 px-0 text-[12px]"
                    onClick={() =>
                      history.push('/dashboard/new')
                    }
                  >
                    创建第一个仪表盘
                  </Button>
                )}
            </div>
          ) : (
            <>
              <div>
                {pageItems.map((dashboard) => {
                  const hasVersion =
                    Number(
                      dashboard.currentVersionNo,
                    ) > 0;

                  return (
                    <div
                      key={dashboard.id}
                      className="
                        group
                        relative
                        -mx-3
                        flex
                        min-h-[200px]
                        gap-4
                        rounded-[8px]
                        border-b
                        border-[#f0f1f2]
                        px-3
                        py-5
                        transition-[background-color,box-shadow]
                        duration-150
                        ease-out
                        hover:z-[1]
                        hover:bg-[#f8f9fa]
                        hover:shadow-[inset_0_0_0_1px_rgba(22,24,35,0.035)]
                        last:border-b-0
                      "
                    >
                      {/* 左侧仪表盘缩略图 */}
                      <button
                        type="button"
                        onClick={() =>
                          history.push(
                            `/dashboard/${dashboard.id}`,
                          )
                        }
                        className="
                          relative
                          h-[160px]
                          w-[120px]
                          shrink-0
                          overflow-hidden
                          rounded-[6px]
                          border
                          border-[#e6e8eb]
                          bg-gradient-to-b
                          from-[#fafafa]
                          to-[#eceef1]
                          p-0
                          text-left
                          transition-[border-color,box-shadow]
                          duration-150
                          ease-out
                          group-hover:border-[#d9dce1]
                          group-hover:shadow-[0_2px_8px_rgba(22,24,35,0.05)]
                        "
                      >
                        {/* 图表模拟 */}
                        <div className="absolute left-3 right-3 top-4 flex h-[62px] items-end gap-1.5">
                          <span className="h-8 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
                          <span className="h-[46px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
                          <span className="h-7 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
                          <span className="h-[54px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
                        </div>

                        {/* 文本模拟 */}
                        <div className="absolute bottom-4 left-3 right-3 rounded-[5px] bg-white px-2 py-[10px] shadow-[0_1px_4px_rgba(0,0,0,0.04)]">
                          <div className="h-[4px] w-[72%] rounded-full bg-[#d5d9de]" />
                          <div className="mt-2 h-[4px] w-[48%] rounded-full bg-[#e1e4e8]" />
                        </div>

                        {/* 版本号 */}
                        <div className="absolute right-2 top-2 flex h-[18px] min-w-[24px] items-center justify-center rounded-[3px] bg-[rgba(22,24,35,0.62)] px-1.5 text-[10px] font-medium text-white">
                          V
                          {dashboard.currentVersionNo ||
                            0}
                        </div>
                      </button>

                      {/* 中间内容 */}
                      <div className="flex min-h-[160px] min-w-0 flex-1 flex-col pr-[300px]">
                        {/* 标题 */}
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() =>
                              history.push(
                                `/dashboard/${dashboard.id}`,
                              )
                            }
                            className="
                              max-w-[620px]
                              truncate
                              border-0
                              bg-transparent
                              p-0
                              text-left
                              text-[14px]
                              font-semibold
                              leading-5
                              text-[#161823]
                              transition-colors
                              hover:text-[#111318]
                              hover:underline
                            "
                          >
                            {dashboard.name}
                          </button>

                          <span
                            className={[
                              'shrink-0 text-[12px] leading-5',
                              hasVersion
                                ? 'text-[#20a464]'
                                : 'text-[#98a2b3]',
                            ].join(' ')}
                          >
                            {hasVersion
                              ? '已配置'
                              : '未保存'}
                          </span>
                        </div>

                        {/* 时间 + 描述 */}
                        <div className="mt-1 flex min-w-0 items-center gap-2 text-[12px] leading-5 text-[#8a9099]">
                          <span>
                            {formatTime(
                              dashboard.updateTime,
                            )}
                          </span>

                          <span className="text-[#d7dade]">
                            |
                          </span>

                          <span className="max-w-[560px] truncate">
                            {dashboard.description ||
                              '暂无描述'}
                          </span>
                        </div>

                        {/* 底部指标 */}
                        <div className="mt-auto flex h-[44px] items-start">
                          {/* 当前版本 */}
                          <div className="w-[116px] shrink-0">
                            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                              当前版本
                            </div>

                            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
                              {hasVersion
                                ? `V${dashboard.currentVersionNo}`
                                : '-'}
                            </div>
                          </div>

                          <div className="mx-5 mt-[3px] h-6 w-px shrink-0 bg-[#e5e7eb]" />

                          {/* 创建日期 */}
                          <div className="w-[122px] shrink-0">
                            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                              创建日期
                            </div>

                            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
                              {formatDate(
                                dashboard.createTime,
                              )}
                            </div>
                          </div>

                          <div className="mx-5 mt-[3px] h-6 w-px shrink-0 bg-[#e5e7eb]" />

                          {/* 更新日期 */}
                          <div className="w-[122px] shrink-0">
                            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                              更新日期
                            </div>

                            <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
                              {formatDate(
                                dashboard.updateTime,
                              )}
                            </div>
                          </div>

                          <div className="mx-5 mt-[3px] h-6 w-px shrink-0 bg-[#e5e7eb]" />

                          {/* ID */}
                          <div className="min-w-0 flex-1">
                            <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                              仪表盘 ID
                            </div>

                            <div
                              className="mt-[2px] max-w-[180px] truncate text-[14px] font-semibold leading-5 text-[#161823]"
                              title={String(
                                dashboard.id,
                              )}
                            >
                              {dashboard.id}
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* 右侧操作 */}
                      <div
                        className="
                          absolute
                          right-3
                          top-5
                          flex
                          items-center
                          gap-0
                          text-[12px]
                          opacity-80
                          transition-opacity
                          duration-150
                          group-hover:opacity-100
                        "
                      >
                        <Button
                          type="text"
                          size="small"
                          icon={
                            <Pencil size={12} />
                          }
                          className="h-7 px-1.5 text-[#667085]"
                          onClick={() =>
                            history.push(
                              `/dashboard/${dashboard.id}`,
                            )
                          }
                        >
                          编辑仪表盘
                        </Button>

                        <Button
                          type="text"
                          size="small"
                          className="h-7 px-1.5 text-[#667085]"
                          onClick={() =>
                            openRename(dashboard)
                          }
                        >
                          重命名
                        </Button>

                        <Popconfirm
                          title="删除仪表盘"
                          description={`确认删除“${dashboard.name}”？此操作不可恢复。`}
                          okText="删除"
                          cancelText="取消"
                          okButtonProps={{
                            danger: true,
                          }}
                          onConfirm={() =>
                            void removeDashboard(
                              dashboard,
                            )
                          }
                        >
                          <Button
                            type="text"
                            size="small"
                            danger
                            icon={
                              <Trash2 size={12} />
                            }
                            className="h-7 px-1.5"
                          >
                            删除
                          </Button>
                        </Popconfirm>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* 分页 / 没有更多 */}
              {filteredDashboards.length <=
              pageSize ? (
                <div className="py-10 text-center text-[13px] text-[#8a9099]">
                  没有更多仪表盘
                </div>
              ) : (
                <div className="flex items-center justify-between border-t border-[#f0f1f2] py-4">
                  <span className="text-[12px] text-[#98a2b3]">
                    共 {filteredDashboards.length}{' '}
                    个仪表盘
                  </span>

                  <Pagination
                    size="small"
                    current={currentPage}
                    pageSize={pageSize}
                    total={
                      filteredDashboards.length
                    }
                    showSizeChanger
                    pageSizeOptions={[
                      10,
                      20,
                      50,
                    ]}
                    onChange={(
                      nextPage,
                      nextPageSize,
                    ) => {
                      setPage(
                        nextPageSize === pageSize
                          ? nextPage
                          : 1,
                      );

                      setPageSize(
                        nextPageSize,
                      );
                    }}
                  />
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* 重命名 */}
      <Modal
        title="重命名仪表盘"
        open={Boolean(renameTarget)}
        okText="保存"
        cancelText="取消"
        confirmLoading={renaming}
        onOk={() =>
          void renameDashboard()
        }
        onCancel={() =>
          !renaming &&
          setRenameTarget(undefined)
        }
      >
        <Input
          autoFocus
          maxLength={128}
          value={renameValue}
          placeholder="请输入仪表盘名称"
          onChange={(event) =>
            setRenameValue(
              event.target.value,
            )
          }
          onPressEnter={() =>
            void renameDashboard()
          }
        />
      </Modal>
    </div>
  );
}