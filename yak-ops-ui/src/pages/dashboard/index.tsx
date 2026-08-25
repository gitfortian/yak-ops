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

type DashboardStatus = 'all' | 'published' | 'draft' | 'unpublished';
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

const lifecycleOf = (dashboard: DashboardSummary) => {
  const published = Number(dashboard.publishedVersionNo) > 0;
  const hasDraft =
    published &&
    dashboard.currentVersionId !== dashboard.publishedVersionId;

  return {
    published,
    hasDraft,
    state: !published
      ? ('unpublished' as const)
      : hasDraft
        ? ('draft' as const)
        : ('published' as const),
  };
};

function DashboardEmptyIllustration() {
  return (
    <svg
      width="336"
      height="220"
      viewBox="0 0 336 220"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className="select-none"
    >
      <defs>
        <linearGradient
          id="dashboard-shell"
          x1="76"
          y1="46"
          x2="260"
          y2="174"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#F7F8FA" />
        </linearGradient>

        <linearGradient
          id="dashboard-pink"
          x1="0"
          y1="0"
          x2="1"
          y2="1"
        >
          <stop stopColor="#FF6A87" />
          <stop offset="1" stopColor="#FE2C55" />
        </linearGradient>

        <linearGradient
          id="dashboard-blue"
          x1="0"
          y1="0"
          x2="1"
          y2="1"
        >
          <stop stopColor="#8FD8FF" />
          <stop offset="1" stopColor="#5A8BFF" />
        </linearGradient>

        <linearGradient
          id="dashboard-violet"
          x1="0"
          y1="0"
          x2="1"
          y2="1"
        >
          <stop stopColor="#C6B8FF" />
          <stop offset="1" stopColor="#8A78FF" />
        </linearGradient>

        <filter
          id="dashboard-shadow"
          x="44"
          y="24"
          width="248"
          height="186"
          filterUnits="userSpaceOnUse"
        >
          <feDropShadow
            dx="0"
            dy="12"
            stdDeviation="14"
            floodColor="#1F2937"
            floodOpacity="0.08"
          />
        </filter>

        <filter
          id="dashboard-card-shadow"
          x="0"
          y="0"
          width="336"
          height="220"
          filterUnits="userSpaceOnUse"
        >
          <feDropShadow
            dx="0"
            dy="7"
            stdDeviation="8"
            floodColor="#1F2937"
            floodOpacity="0.07"
          />
        </filter>
      </defs>

      {/* 装饰点 */}
      <circle cx="48" cy="52" r="3.5" fill="#FFD0DB" />
      <circle cx="287" cy="55" r="4" fill="#D7E5FF" />
      <circle cx="63" cy="177" r="4.5" fill="#E4DFFF" />
      <circle cx="286" cy="173" r="3" fill="#FFE7A8" />

      {/* 小星星 */}
      <path
        d="M40 87H52"
        stroke="#D8DCE3"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
      <path
        d="M46 81V93"
        stroke="#D8DCE3"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
      <path
        d="M284 92H296"
        stroke="#D8DCE3"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
      <path
        d="M290 86V98"
        stroke="#D8DCE3"
        strokeWidth="1.5"
        strokeLinecap="round"
      />

      {/* 主仪表盘 */}
      <g filter="url(#dashboard-shadow)">
        <rect
          x="76"
          y="43"
          width="184"
          height="126"
          rx="22"
          fill="url(#dashboard-shell)"
        />

        <rect
          x="76.75"
          y="43.75"
          width="182.5"
          height="124.5"
          rx="21.25"
          stroke="#E7E9ED"
          strokeWidth="1.5"
        />

        {/* 顶部 */}
        <circle
          cx="94"
          cy="61"
          r="3"
          fill="#FE2C55"
          fillOpacity="0.55"
        />
        <circle
          cx="105"
          cy="61"
          r="3"
          fill="#F5C451"
          fillOpacity="0.7"
        />
        <circle
          cx="116"
          cy="61"
          r="3"
          fill="#5BC28A"
          fillOpacity="0.7"
        />

        <rect
          x="130"
          y="57.5"
          width="64"
          height="7"
          rx="3.5"
          fill="#E9EBEF"
        />

        {/* KPI 卡片 */}
        <rect
          x="91"
          y="79"
          width="48"
          height="33"
          rx="10"
          fill="#FBFBFC"
          stroke="#ECEEF1"
        />

        <rect
          x="101"
          y="88"
          width="17"
          height="5"
          rx="2.5"
          fill="#D8DCE3"
        />

        <rect
          x="101"
          y="98"
          width="27"
          height="7"
          rx="3.5"
          fill="url(#dashboard-pink)"
        />

        {/* 折线图 */}
        <rect
          x="147"
          y="79"
          width="97"
          height="61"
          rx="12"
          fill="#FBFBFC"
          stroke="#ECEEF1"
        />

        <path
          d="M159 126H232"
          stroke="#EEF0F3"
          strokeWidth="1.2"
          strokeLinecap="round"
        />

        <path
          d="M159 113H232"
          stroke="#F1F2F4"
          strokeWidth="1.2"
          strokeLinecap="round"
        />

        <path
          d="M159 123C168 117 174 120 181 111C189 101 196 108 204 99C212 91 220 101 232 90"
          stroke="url(#dashboard-blue)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />

        <circle
          cx="232"
          cy="90"
          r="3.5"
          fill="#5A8BFF"
        />

        {/* 柱状图 */}
        <rect
          x="91"
          y="121"
          width="48"
          height="31"
          rx="10"
          fill="#FFF7F9"
          stroke="#F9E9ED"
        />

        <rect
          x="101"
          y="139"
          width="6"
          height="7"
          rx="3"
          fill="#FFD0DB"
        />

        <rect
          x="111"
          y="133"
          width="6"
          height="13"
          rx="3"
          fill="#FF9FB3"
        />

        <rect
          x="121"
          y="126"
          width="6"
          height="20"
          rx="3"
          fill="url(#dashboard-pink)"
        />
      </g>

      {/* 左侧悬浮图 */}
      <g filter="url(#dashboard-card-shadow)">
        <rect
          x="34"
          y="101"
          width="58"
          height="46"
          rx="14"
          fill="#FFFFFF"
        />

        <rect
          x="34.75"
          y="101.75"
          width="56.5"
          height="44.5"
          rx="13.25"
          stroke="#E7E9ED"
          strokeWidth="1.5"
        />

        <rect
          x="46"
          y="112"
          width="23"
          height="5"
          rx="2.5"
          fill="#D8DCE3"
        />

        <path
          d="M46 133C51 127 56 135 61 128C66 122 73 128 80 119"
          stroke="url(#dashboard-violet)"
          strokeWidth="2.6"
          strokeLinecap="round"
        />

        <circle
          cx="80"
          cy="119"
          r="2.8"
          fill="#8A78FF"
        />
      </g>

      {/* 右侧环形图 */}
      <g filter="url(#dashboard-card-shadow)">
        <rect
          x="244"
          y="104"
          width="58"
          height="48"
          rx="14"
          fill="#FFFFFF"
        />

        <rect
          x="244.75"
          y="104.75"
          width="56.5"
          height="46.5"
          rx="13.25"
          stroke="#E7E9ED"
          strokeWidth="1.5"
        />

        <circle
          cx="273"
          cy="128"
          r="12"
          stroke="#E5EDFF"
          strokeWidth="7"
        />

        <path
          d="M273 116A12 12 0 1 1 264.5 136.5"
          stroke="url(#dashboard-blue)"
          strokeWidth="7"
          strokeLinecap="round"
        />

        <circle
          cx="273"
          cy="128"
          r="4.2"
          fill="#FFFFFF"
        />
      </g>

      {/* 底部状态条 */}
      <g filter="url(#dashboard-card-shadow)">
        <rect
          x="116"
          y="174"
          width="104"
          height="25"
          rx="12.5"
          fill="#FFFFFF"
        />

        <rect
          x="116.75"
          y="174.75"
          width="102.5"
          height="23.5"
          rx="11.75"
          stroke="#E7E9ED"
          strokeWidth="1.5"
        />

        <circle
          cx="129"
          cy="186.5"
          r="4"
          fill="#FE2C55"
          fillOpacity="0.72"
        />

        <rect
          x="139"
          y="183.5"
          width="31"
          height="6"
          rx="3"
          fill="#D8DCE3"
        />

        <rect
          x="176"
          y="183.5"
          width="31"
          height="6"
          rx="3"
          fill="#F0F1F3"
        />
      </g>
    </svg>
  );
}

function DashboardEmptyState({
  onCreate,
}: {
  onCreate: () => void;
}) {
  return (
    <div className="flex min-h-[460px] flex-col items-center justify-center pb-10 pt-4 text-center">
      <DashboardEmptyIllustration />

      <div className="mt-1 text-[15px] font-semibold leading-6 text-[#30333b]">
        还没有仪表盘
      </div>

      <div className="mt-1.5 max-w-[360px] text-[12px] leading-5 text-[#8a9099]">
        把指标、图表和趋势放在同一个画布里，做一个属于你的数据视图。
      </div>

      <Button
        type="link"
        icon={<Plus size={14} />}
        onClick={onCreate}
        className="mt-2 h-8 px-2 text-[12px] font-medium"
      >
        创建第一个仪表盘
      </Button>
    </div>
  );
}

function DashboardFilterEmptyState({
  onReset,
}: {
  onReset: () => void;
}) {
  return (
    <div className="flex min-h-[420px] flex-col items-center justify-center pb-10 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-[14px] bg-[#f5f6f7] text-[#98a2b3]">
        <LayoutDashboard
          size={22}
          strokeWidth={1.7}
        />
      </div>

      <div className="mt-3 text-[13px] font-medium text-[#667085]">
        没有匹配的仪表盘
      </div>

      <div className="mt-1 text-[12px] leading-5 text-[#a3a8b0]">
        换个关键词或清空筛选条件再试试。
      </div>

      <Button
        type="link"
        onClick={onReset}
        className="mt-1 h-8 px-2 text-[12px]"
      >
        清空筛选
      </Button>
    </div>
  );
}

export default function DashboardListPage() {
  const [dashboards, setDashboards] = useState<DashboardSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<DashboardStatus>('all');
  const [timeRange, setTimeRange] = useState<TimeRange>('all');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [renameTarget, setRenameTarget] =
    useState<DashboardSummary>();

  const [renameValue, setRenameValue] =
    useState('');

  const [renaming, setRenaming] =
    useState(false);

  const loadDashboards = useCallback(async () => {
    setLoading(true);

    try {
      setDashboards(await fetchDashboards());
    } catch (error) {
      setDashboards([]);

      message.error(
        error instanceof Error
          ? error.message
          : '加载仪表盘失败',
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

  const lifecycleCounts = useMemo(
    () =>
      dashboards.reduce(
        (result, item) => {
          result[lifecycleOf(item).state] += 1;
          return result;
        },
        {
          published: 0,
          draft: 0,
          unpublished: 0,
        },
      ),
    [dashboards],
  );

  const filteredDashboards = useMemo(() => {
    const value = keyword
      .trim()
      .toLowerCase();

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

      if (
        status !== 'all' &&
        lifecycleOf(dashboard).state !== status
      ) {
        return false;
      }

      if (timeRange !== 'all') {
        const updateTime = getTime(
          dashboard.updateTime,
        );

        if (!updateTime) {
          return false;
        }

        const days =
          timeRange === '7d'
            ? 7
            : 30;

        if (
          now - updateTime >
          days * 24 * 60 * 60 * 1000
        ) {
          return false;
        }
      }

      return true;
    });
  }, [
    dashboards,
    keyword,
    status,
    timeRange,
  ]);

  const pageCount = Math.max(
    1,
    Math.ceil(
      filteredDashboards.length /
        pageSize,
    ),
  );

  const currentPage = Math.min(
    page,
    pageCount,
  );

  const pageItems = useMemo(() => {
    const start =
      (currentPage - 1) * pageSize;

    return filteredDashboards.slice(
      start,
      start + pageSize,
    );
  }, [
    currentPage,
    filteredDashboards,
    pageSize,
  ]);

  const openRename = (
    dashboard: DashboardSummary,
  ) => {
    setRenameTarget(dashboard);
    setRenameValue(dashboard.name);
  };

  const renameDashboard = async () => {
    const name = renameValue.trim();

    if (!renameTarget || !name) {
      message.warning(
        '请输入仪表盘名称',
      );
      return;
    }

    if (name === renameTarget.name) {
      setRenameTarget(undefined);
      return;
    }

    setRenaming(true);

    try {
      const detail =
        await fetchDashboard(
          renameTarget.id,
        );

      const document =
        toDashboardDocument(detail);

      await saveDashboardVersion(
        renameTarget.id,
        {
          ...document,
          name,
        },
      );

      message.success(
        '名称已保存到新草稿版本',
      );

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
      await deleteDashboard(
        dashboard.id,
      );

      message.success(
        '仪表盘已删除',
      );

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
    count: number;
  }> = [
    {
      key: 'all',
      label: '全部',
      count: dashboards.length,
    },
    {
      key: 'published',
      label: '已发布',
      count:
        lifecycleCounts.published,
    },
    {
      key: 'draft',
      label: '有草稿',
      count:
        lifecycleCounts.draft,
    },
    {
      key: 'unpublished',
      label: '未发布',
      count:
        lifecycleCounts.unpublished,
    },
  ];

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#f6f7f8]">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <div className="text-[18px] font-semibold leading-7 text-[#161823]">
          仪表盘管理
        </div>

        <div className="mt-2 flex min-h-[48px] items-center justify-between gap-5">
          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              className="h-9 rounded-[8px] border-0 bg-[#f0f1f2] px-4 text-[13px] font-medium text-[#161823]"
            >
              仪表盘 ({dashboards.length})
            </button>
          </div>

          <div className="flex min-w-0 flex-1 items-center justify-end gap-2">
            <div className="mr-1 flex shrink-0 items-center">
              {statusItems.map(
                (item) => {
                  const active =
                    status ===
                    item.key;

                  return (
                    <button
                      key={item.key}
                      type="button"
                      onClick={() =>
                        setStatus(
                          item.key,
                        )
                      }
                      className={[
                        'relative h-9 border-0 bg-transparent px-3 text-[13px] transition-colors',
                        active
                          ? 'font-semibold text-[#161823]'
                          : 'font-normal text-[#8a9099] hover:text-[#444950]',
                      ].join(' ')}
                    >
                      {item.label}

                      {item.key !==
                        'all' &&
                      item.count ? (
                        <span className="ml-1 text-[10px] font-normal text-[#b0b5bd]">
                          {
                            item.count
                          }
                        </span>
                      ) : null}
                    </button>
                  );
                },
              )}
            </div>

            <Select<TimeRange>
              value={timeRange}
              onChange={
                setTimeRange
              }
              suffixIcon={
                <ChevronDown
                  size={14}
                />
              }
              options={[
                {
                  value: 'all',
                  label:
                    '所有时间',
                },
                {
                  value: '7d',
                  label:
                    '最近 7 天',
                },
                {
                  value: '30d',
                  label:
                    '最近 30 天',
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
                setKeyword(
                  event.target
                    .value,
                )
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
              icon={
                <RefreshCw
                  size={14}
                />
              }
              loading={loading}
              onClick={() =>
                void loadDashboards()
              }
              className="bg-[#f5f6f7]"
            >
              刷新
            </Button>

            <Button
              type="primary"
              size="middle"
              icon={
                <Plus
                  size={15}
                />
              }
              onClick={() =>
                history.push(
                  '/dashboard/new',
                )
              }
            >
              新建仪表盘
            </Button>
          </div>
        </div>

        <Divider
          style={{
            padding: 0,
            margin: '12px 0',
          }}
        />

        <div className="relative">
          {loading &&
          dashboards.length ===
            0 ? (
            <div className="flex min-h-[420px] items-center justify-center text-[13px] text-[#98a2b3]">
              正在加载仪表盘...
            </div>
          ) : pageItems.length ===
            0 ? (
            keyword.trim() ||
            status !== 'all' ||
            timeRange !==
              'all' ? (
              <DashboardFilterEmptyState
                onReset={() => {
                  setKeyword('');
                  setStatus(
                    'all',
                  );
                  setTimeRange(
                    'all',
                  );
                }}
              />
            ) : (
              <DashboardEmptyState
                onCreate={() =>
                  history.push(
                    '/dashboard/new',
                  )
                }
              />
            )
          ) : (
            <>
              <div>
                {pageItems.map(
                  (dashboard) => {
                    const lifecycle =
                      lifecycleOf(
                        dashboard,
                      );

                    const openPath =
                      lifecycle.published
                        ? `/dashboard/${dashboard.id}`
                        : `/dashboard/${dashboard.id}/edit`;

                    const statusLabel =
                      lifecycle.state ===
                      'published'
                        ? `已发布 V${dashboard.publishedVersionNo}`
                        : lifecycle.state ===
                            'draft'
                          ? `有草稿 V${dashboard.currentVersionNo}`
                          : `未发布 · 草稿 V${dashboard.currentVersionNo}`;

                    const statusClass =
                      lifecycle.state ===
                      'published'
                        ? 'text-[#20a464]'
                        : lifecycle.state ===
                            'draft'
                          ? 'text-[#667085]'
                          : 'text-[#98a2b3]';

                    return (
                      <div
                        key={
                          dashboard.id
                        }
                        className="group relative -mx-3 flex min-h-[200px] gap-4 rounded-[8px] border-b border-[#f0f1f2] px-3 py-5 transition-[background-color,box-shadow] duration-150 ease-out hover:z-[1] hover:bg-[#f8f9fa] hover:shadow-[inset_0_0_0_1px_rgba(22,24,35,0.035)] last:border-b-0"
                      >
                        <button
                          type="button"
                          onClick={() =>
                            history.push(
                              openPath,
                            )
                          }
                          className="relative h-[160px] w-[120px] shrink-0 overflow-hidden rounded-[6px] border border-[#e6e8eb] bg-gradient-to-b from-[#fafafa] to-[#eceef1] p-0 text-left transition-[border-color,box-shadow] duration-150 ease-out group-hover:border-[#d9dce1] group-hover:shadow-[0_2px_8px_rgba(22,24,35,0.05)]"
                        >
                          <div className="absolute left-3 right-3 top-4 flex h-[62px] items-end gap-1.5">
                            <span className="h-8 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />

                            <span className="h-[46px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />

                            <span className="h-7 flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />

                            <span className="h-[54px] flex-1 rounded-[2px] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.04)]" />
                          </div>

                          <div className="absolute bottom-4 left-3 right-3 rounded-[5px] bg-white px-2 py-[10px] shadow-[0_1px_4px_rgba(0,0,0,0.04)]">
                            <div className="h-[4px] w-[72%] rounded-full bg-[#d5d9de]" />

                            <div className="mt-2 h-[4px] w-[48%] rounded-full bg-[#e1e4e8]" />
                          </div>

                          <div className="absolute right-2 top-2 flex h-[18px] min-w-[24px] items-center justify-center rounded-[3px] bg-[rgba(22,24,35,0.62)] px-1.5 text-[10px] font-medium text-white">
                            V
                            {dashboard.currentVersionNo ||
                              0}
                          </div>
                        </button>

                        <div className="flex min-h-[160px] min-w-0 flex-1 flex-col pr-[300px]">
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() =>
                                history.push(
                                  openPath,
                                )
                              }
                              className="max-w-[620px] truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold leading-5 text-[#161823] transition-colors hover:text-[#111318] hover:underline"
                            >
                              {
                                dashboard.name
                              }
                            </button>

                            <span
                              className={`shrink-0 text-[12px] leading-5 ${statusClass}`}
                            >
                              {
                                statusLabel
                              }
                            </span>
                          </div>

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

                          <div className="mt-auto flex h-[44px] items-start">
                            <div className="w-[116px] shrink-0">
                              <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                                草稿版本
                              </div>

                              <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
                                {dashboard.currentVersionNo
                                  ? `V${dashboard.currentVersionNo}`
                                  : '-'}
                              </div>
                            </div>

                            <div className="mx-5 mt-[3px] h-6 w-px shrink-0 bg-[#e5e7eb]" />

                            <div className="w-[122px] shrink-0">
                              <div className="text-[12px] leading-[18px] text-[#a3a8b0]">
                                发布版本
                              </div>

                              <div className="mt-[2px] text-[14px] font-semibold leading-5 text-[#161823]">
                                {lifecycle.published
                                  ? `V${dashboard.publishedVersionNo}`
                                  : '-'}
                              </div>
                            </div>

                            <div className="mx-5 mt-[3px] h-6 w-px shrink-0 bg-[#e5e7eb]" />

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
                                {
                                  dashboard.id
                                }
                              </div>
                            </div>
                          </div>
                        </div>

                        <div className="absolute right-3 top-5 flex items-center gap-0 text-[12px] opacity-80 transition-opacity duration-150 group-hover:opacity-100">
                          <Button
                            type="text"
                            size="small"
                            icon={
                              <Pencil
                                size={12}
                              />
                            }
                            className="h-7 px-1.5 text-[#667085]"
                            onClick={() =>
                              history.push(
                                `/dashboard/${dashboard.id}/edit`,
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
                              openRename(
                                dashboard,
                              )
                            }
                          >
                            重命名
                          </Button>

                          <Popconfirm
                            title="删除仪表盘"
                            description={`确认删除“${dashboard.name}”？草稿和已发布版本都会被删除，此操作不可恢复。`}
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
                                <Trash2
                                  size={
                                    12
                                  }
                                />
                              }
                              className="h-7 px-1.5"
                            >
                              删除
                            </Button>
                          </Popconfirm>
                        </div>
                      </div>
                    );
                  },
                )}
              </div>

              {filteredDashboards.length <=
              pageSize ? (
                <div className="py-10 text-center text-[13px] text-[#8a9099]">
                  没有更多仪表盘
                </div>
              ) : (
                <div className="flex items-center justify-between border-t border-[#f0f1f2] py-4">
                  <span className="text-[12px] text-[#98a2b3]">
                    共{' '}
                    {
                      filteredDashboards.length
                    }{' '}
                    个仪表盘
                  </span>

                  <Pagination
                    size="small"
                    current={
                      currentPage
                    }
                    pageSize={
                      pageSize
                    }
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
                        nextPageSize ===
                          pageSize
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

      <Modal
        title="重命名仪表盘"
        open={Boolean(
          renameTarget,
        )}
        okText="保存为草稿"
        cancelText="取消"
        confirmLoading={renaming}
        onOk={() =>
          void renameDashboard()
        }
        onCancel={() =>
          !renaming &&
          setRenameTarget(
            undefined,
          )
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

        {renameTarget?.publishedVersionNo ? (
          <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
            重命名会生成新的草稿版本，不会立即修改当前已发布版本。
          </div>
        ) : null}
      </Modal>
    </div>
  );
}