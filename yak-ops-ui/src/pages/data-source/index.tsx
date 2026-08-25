import YakButton from "@/components/YakButton";
import YakTab from "@/components/YakTab";
import { YAK_OPS_PERMISSIONS } from "@/constants/yakOpsPermissions";
import usePermissionAccess from "@/hooks/usePermissionAccess";
import { API_SUCCESS_CODE } from "@/services/http/response";
import { useIntl } from "@umijs/max";
import { Input, message, Modal, Pagination, Select, Spin } from "antd";
import { motion } from "framer-motion";
import {
  CheckCircle2,
  Database,
  Grid2X2,
  LayoutList,
  Pencil,
  Plus,
  Search,
  Server,
  Trash2,
  Unplug,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import AddOrEditDataSourceModal from "./components/AddOrEditDataSourceModal";
import DataSourceStatus from "./components/DataSourceStatus";
import {
  COMMON_DB_OPTIONS,
  ENVIRONMENT_OPTIONS,
  environmentTagConfigMap,
  PAGE_ANIMATION,
  PAGE_DEFAULT_PAGINATION,
} from "./constants";
import DatabaseIcons from "./icon/DatabaseIcons";
import {
  deleteDataSource,
  fetchDataSourceDetail,
  fetchDataSourcePage,
  fetchDataSourceSummary,
  testDataSourceConnection,
} from "./service";
import type {
  DataSourceId,
  DataSourceModalRef,
  DataSourcePageParams,
  DataSourceRecord,
  DataSourceSummary,
  PaginationInfo,
} from "./types";
import { DataSourceOperateType } from "./types";

const { confirm } = Modal;

type DataSourceViewMode = "grid" | "list";

const EMPTY_SUMMARY: DataSourceSummary = {
  total: 0,
  connected: 0,
  disconnected: 0,
  unknown: 0,
  environmentCount: 0,
};

const ENVIRONMENT_FILTER_OPTIONS = ENVIRONMENT_OPTIONS.map((item) => ({
  ...item,
  label: environmentTagConfigMap[item.value]?.text || item.label,
}));

const recordKey = (id?: DataSourceId) => String(id ?? "");

const DataSourceEmptyIllustration = () => (
  <svg
    width="220"
    height="158"
    viewBox="0 0 220 158"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <circle cx="67" cy="27" r="18" fill="#ffe9ee" />
    <path
      d="M67 19V35M59 27H75"
      stroke="#fe2c55"
      strokeWidth="3.5"
      strokeLinecap="round"
    />

    <ellipse cx="115" cy="145" rx="77" ry="5" fill="#161823" opacity="0.045" />

    <rect
      x="94"
      y="40"
      width="82"
      height="66"
      rx="5"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path d="M94 55H176" stroke="#515151" strokeWidth="1.5" />
    <circle cx="103" cy="48" r="2" fill="#c6cacd" />
    <circle cx="110" cy="48" r="2" fill="#c6cacd" />
    <circle cx="117" cy="48" r="2" fill="#c6cacd" />

    <ellipse
      cx="135"
      cy="72"
      rx="17"
      ry="6"
      fill="#f3f4f5"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M118 72V91C118 95 125.5 98 135 98C144.5 98 152 95 152 91V72"
      fill="#f8f9fa"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M118 81C118 85 125.5 88 135 88C144.5 88 152 85 152 81"
      stroke="#c6cacd"
      strokeWidth="1.5"
    />

    <circle
      cx="62"
      cy="88"
      r="10"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M50 121C50 106 54 98 62 98C70 98 75 106 75 120V135H48L50 121Z"
      fill="#515151"
    />
    <path
      d="M70 104C79 103 86 96 96 86"
      stroke="#515151"
      strokeWidth="2"
      strokeLinecap="round"
    />
    <circle
      cx="97"
      cy="85"
      r="3"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />

    <path
      d="M164 116C164 108 170 102 178 102C186 102 192 108 192 116V134H164V116Z"
      fill="#e7e9eb"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M170 113H186M170 120H186M170 127H181"
      stroke="#9aa0a6"
      strokeWidth="1.5"
      strokeLinecap="round"
    />
    <path
      d="M153 108C157 112 159 116 160 121"
      stroke="#c6cacd"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeDasharray="3 4"
    />

    <circle cx="184" cy="39" r="2.5" fill="#ffd8e1" />
    <circle cx="191" cy="33" r="1.5" fill="#c6cacd" />
  </svg>
);

interface DataSourceEmptyStateProps {
  filtered: boolean;
  canCreate: boolean;
  onReset: () => void;
  onCreate: () => void;
}

const DataSourceEmptyState = ({
  filtered,
  canCreate,
  onReset,
  onCreate,
}: DataSourceEmptyStateProps) => (
  <div className="mt-1 flex min-h-[360px] items-center justify-center rounded-[10px] bg-[#fafafa]">
    <div className="flex w-[340px] -translate-y-1 flex-col items-center">
      <DataSourceEmptyIllustration />

      <h3 className="mt-0.5 text-[14px] font-semibold leading-[22px] text-[#1c1f23]">
        {filtered ? "没有找到符合条件的数据源" : "还没有创建数据源"}
      </h3>
      <div className="mt-3.5">
        {filtered ? (
          <YakButton size="small" onClick={onReset}>
            重置筛选
          </YakButton>
        ) : (
          canCreate && (
            <YakButton
              type="primary"
              size="small"
              onClick={onCreate}
            >
              新建数据源
            </YakButton>
          )
        )}
      </div>
    </div>
  </div>
);

const DataSourcePage = () => {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);
  const requestSeqRef = useRef(0);
  const { can } = usePermissionAccess();

  const canCreate = can(YAK_OPS_PERMISSIONS.dataSource.create);
  const canUpdate = can(YAK_OPS_PERMISSIONS.dataSource.update);
  const canDelete = can(YAK_OPS_PERMISSIONS.dataSource.delete);
  const canTest = can(YAK_OPS_PERMISSIONS.dataSource.test);

  const [loading, setLoading] = useState(false);
  const [dataSourceList, setDataSourceList] = useState<DataSourceRecord[]>([]);
  const [summary, setSummary] = useState<DataSourceSummary>(EMPTY_SUMMARY);
  const [pagination, setPagination] = useState<PaginationInfo>(
    PAGE_DEFAULT_PAGINATION
  );
  const [searchKeyword, setSearchKeyword] = useState("");
  const [dbTypeFilter, setDbTypeFilter] = useState<string | undefined>();
  const [environmentFilter, setEnvironmentFilter] = useState<
    string | undefined
  >();
  const [viewMode, setViewMode] = useState<DataSourceViewMode>("grid");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [testingId, setTestingId] = useState("");
  const [editingId, setEditingId] = useState("");

  const hasActiveFilters = Boolean(
    searchKeyword.trim() || dbTypeFilter || environmentFilter
  );

  const resetPage = useCallback(() => {
    setPagination((current) => ({
      ...current,
      pageNo: 1,
    }));
  }, []);

  const fetchList = useCallback(async () => {
    const requestSeq = requestSeqRef.current + 1;
    requestSeqRef.current = requestSeq;
    setLoading(true);

    const requestParams: DataSourcePageParams = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.trim() || undefined,
      dbType: dbTypeFilter,
      environment: environmentFilter,
    };

    try {
      const [pageResult, summaryResult] = await Promise.allSettled([
        fetchDataSourcePage(requestParams),
        fetchDataSourceSummary(),
      ]);

      if (requestSeq !== requestSeqRef.current) {
        return;
      }

      if (
        pageResult.status === "fulfilled" &&
        pageResult.value.code === API_SUCCESS_CODE
      ) {
        const records = pageResult.value.data?.bizData || [];
        const nextPagination =
          pageResult.value.data?.pagination || PAGE_DEFAULT_PAGINATION;

        if (
          records.length === 0 &&
          nextPagination.total > 0 &&
          requestParams.pageNo > 1
        ) {
          setPagination((current) => ({
            ...current,
            pageNo: Math.max(1, requestParams.pageNo - 1),
            total: nextPagination.total,
          }));
        } else {
          setDataSourceList(records);
          setPagination(nextPagination);
        }
      }

      if (
        summaryResult.status === "fulfilled" &&
        summaryResult.value.code === API_SUCCESS_CODE
      ) {
        setSummary(summaryResult.value.data || EMPTY_SUMMARY);
      }
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  }, [
    dbTypeFilter,
    environmentFilter,
    pagination.pageNo,
    pagination.pageSize,
    refreshVersion,
    searchKeyword,
  ]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => void fetchList(),
      searchKeyword.trim() ? 300 : 0
    );

    return () => window.clearTimeout(timer);
  }, [fetchList, searchKeyword]);

  const handleRefresh = useCallback(() => {
    setRefreshVersion((value) => value + 1);
  }, []);

  const handleResetFilters = useCallback(() => {
    setSearchKeyword("");
    setDbTypeFilter(undefined);
    setEnvironmentFilter(undefined);
    resetPage();
  }, [resetPage]);

  const handleCreate = () => {
    if (!canCreate) {
      return;
    }

    modalRef.current?.open({
      operateType: DataSourceOperateType.Create,
      onSuccess: handleRefresh,
    });
  };

  const handleEdit = async (record: DataSourceRecord) => {
    if (!canUpdate || !record.id || editingId) {
      return;
    }

    const id = recordKey(record.id);

    try {
      setEditingId(id);

      const response = await fetchDataSourceDetail(record.id);

      if (response.code !== API_SUCCESS_CODE || !response.data) {
        return;
      }

      modalRef.current?.open({
        operateType: DataSourceOperateType.Edit,
        currentRecord: response.data,
        onSuccess: handleRefresh,
      });
    } finally {
      setEditingId("");
    }
  };

  const handleDelete = (record: DataSourceRecord) => {
    if (!canDelete) {
      return;
    }

    confirm({
      title: intl.formatMessage({
        id: "pages.datasource.delete.confirmTitle",
        defaultMessage: "确认删除该数据源吗？",
      }),
      centered: true,
      content: (
        <span>
          即将删除数据源
          <span className="font-semibold text-[#fe2c55]"> [{record.name}]</span>
          。
          <br />
          删除后无法恢复，请谨慎操作。
        </span>
      ),
      okText: "删除",
      cancelText: "取消",
      okType: "primary",
      okButtonProps: {
        size: "small",
        danger: true,
      },
      cancelButtonProps: {
        size: "small",
      },
      maskClosable: true,

      async onOk() {
        if (!record.id) {
          message.error("数据源 ID 不存在");
          return;
        }

        const response = await deleteDataSource(record.id);

        if (response.code !== API_SUCCESS_CODE) {
          return;
        }

        message.success(response.message || "删除成功");
        handleRefresh();
      },
    });
  };

  const handleTestConnection = async (record: DataSourceRecord) => {
    if (!canTest || !record.id || testingId) {
      return;
    }

    const id = recordKey(record.id);

    try {
      setTestingId(id);

      const response = await testDataSourceConnection(record.id);

      if (response.code !== API_SUCCESS_CODE) {
        return;
      }

      message.success("连接测试成功");
      handleRefresh();
    } finally {
      setTestingId("");
    }
  };

  const environmentTabs = useMemo(
    () => [
      {
        key: "all",
        label: "全部",
        value: undefined,
      },
      ...ENVIRONMENT_FILTER_OPTIONS.map((item) => ({
        key: item.value,
        label: item.label,
        value: item.value,
      })),
    ],
    []
  );

  const renderDataSourceCard = (record: DataSourceRecord) => {
    const environmentConfig = environmentTagConfigMap[
      record.environment || ""
    ] || {
      text: record.environmentName || "未分类",
      color: "#667085",
      backgroundColor: "#f2f4f7",
      icon: null,
    };

    const currentId = recordKey(record.id);
    const actionAvailable = canTest || canUpdate || canDelete;
    const isListView = viewMode === "list";

    return (
      <motion.article
        key={record.id}
        variants={PAGE_ANIMATION.fadeUp}
        className={[
          "group min-w-0 overflow-hidden rounded-[9px] border border-black/[0.075] bg-white",
          "transition-[transform,border-color,box-shadow] duration-200",
          "hover:-translate-y-0.5 hover:border-black/[0.11] hover:shadow-[0_10px_28px_rgba(22,24,35,0.07)]",
          isListView
            ? "grid grid-cols-[minmax(360px,1.35fr)_minmax(360px,1fr)] max-xl:grid-cols-1"
            : "",
        ]
          .filter(Boolean)
          .join(" ")}
      >
        <div className="flex min-h-[92px] items-start justify-between gap-[15px] bg-[radial-gradient(circle_at_100%_0,rgba(88,110,255,0.08),transparent_37%),linear-gradient(110deg,#fbfcff_0%,#f7f8fc_100%)] px-[19px] pb-4 pt-[19px]">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-[47px] w-[47px] shrink-0 items-center justify-center rounded-xl border border-black/[0.055] bg-white shadow-[0_5px_14px_rgba(22,24,35,0.055)]">
              <DatabaseIcons dbType={record.dbType} width="30" height="30" />
            </div>

            <div className="min-w-0">
              <div className="flex min-w-0 items-center gap-2">
                <h3
                  title={record.name}
                  className="m-0 min-w-0 truncate text-[15px] font-semibold text-[#161823]"
                >
                  {record.name || "未命名数据源"}
                </h3>

                <span
                  className="inline-flex h-5 shrink-0 items-center gap-1 whitespace-nowrap rounded-full px-[7px] text-[9px] font-semibold"
                  style={{
                    color: environmentConfig.color,
                    background: environmentConfig.backgroundColor,
                  }}
                >
                  {environmentConfig.icon}
                  {record.environmentName || environmentConfig.text}
                </span>
              </div>

              <p
                title={record.jdbcUrl}
                className="mt-1.5 max-w-[410px] truncate text-[11px] text-black/[0.43]"
              >
                {record.jdbcUrl || "暂未配置连接地址"}
              </p>
            </div>
          </div>

          {actionAvailable && (
            <div className="flex shrink-0 -translate-y-1 gap-1 opacity-0 pointer-events-none transition-all duration-150 group-hover:translate-y-0 group-hover:opacity-100 group-hover:pointer-events-auto">
              {canTest && (
                <YakButton
                  type="text"
                  size="small"
                  iconOnly
                  title="测试连接"
                  loading={testingId === currentId}
                  disabled={Boolean(testingId) && testingId !== currentId}
                  className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0 !text-black/[0.52] hover:!text-[#4058c8]"
                  icon={<Unplug size={15} strokeWidth={1.9} />}
                  onClick={() => void handleTestConnection(record)}
                />
              )}

              {canUpdate && (
                <YakButton
                  type="text"
                  size="small"
                  iconOnly
                  title="编辑数据源"
                  loading={editingId === currentId}
                  disabled={Boolean(editingId) && editingId !== currentId}
                  className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0 !text-black/[0.52] hover:!text-[#4058c8]"
                  icon={<Pencil size={15} strokeWidth={1.9} />}
                  onClick={() => void handleEdit(record)}
                />
              )}

              {canDelete && (
                <YakButton
                  type="text"
                  size="small"
                  danger
                  iconOnly
                  title="删除数据源"
                  className="!h-[29px] !w-[29px] !rounded-[7px] !border !border-black/[0.07] !bg-white/90 !p-0"
                  icon={<Trash2 size={15} strokeWidth={1.9} />}
                  onClick={() => handleDelete(record)}
                />
              )}
            </div>
          )}
        </div>

        <div
          className={[
            "grid grid-cols-3 px-[19px] py-[15px]",
            isListView
              ? "items-center border-l border-black/[0.055] max-xl:border-l-0 max-xl:border-t"
              : "",
          ]
            .filter(Boolean)
            .join(" ")}
        >
          <div className="flex min-w-0 flex-col gap-1.5">
            <span className="text-[10px] text-black/[0.38]">连接状态</span>
            <DataSourceStatus status={record.connStatus} />
          </div>

          <div className="flex min-w-0 flex-col gap-1.5 border-l border-black/[0.06] pl-3.5">
            <span className="text-[10px] text-black/[0.38]">数据源类型</span>
            <strong className="truncate text-[11px] font-semibold text-black/[0.78]">
              {String(record.dbType || "-")}
            </strong>
          </div>

          <div className="flex min-w-0 flex-col gap-1.5 border-l border-black/[0.06] pl-3.5">
            <span className="text-[10px] text-black/[0.38]">最近更新</span>
            <strong className="truncate text-[11px] font-semibold text-black/[0.78]">
              {record.updateTime || "-"}
            </strong>
          </div>
        </div>
      </motion.article>
    );
  };

  return (
    <>
      <div className="min-h-full bg-[#f7f8fa] text-[#161823]">
        <motion.div
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
          className="min-h-[calc(100vh-132px)] rounded-[10px] border border-black/[0.025] bg-white px-[34px] pb-[38px] pt-[30px] shadow-[0_2px_12px_rgba(22,24,35,0.025)] max-xl:px-[26px]"
        >
          <motion.header
            variants={PAGE_ANIMATION.fadeUp}
            className="flex items-start justify-between gap-8"
          >
            <h1 className="m-0 text-2xl font-bold tracking-[-0.45px] text-[#161823]">
              数据源管理
            </h1>

            {canCreate && (
              <YakButton
                type="primary"
                size="large"
                icon={<Plus size={16} strokeWidth={2.1} />}
                className="!h-10 !shrink-0 !rounded-[7px] !px-[17px]"
                onClick={handleCreate}
              >
                新建数据源
              </YakButton>
            )}
          </motion.header>

          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="mt-[26px] grid grid-cols-4 overflow-hidden rounded-[9px] border border-black/[0.055] bg-[radial-gradient(circle_at_85%_10%,rgba(88,110,255,0.08),transparent_31%),linear-gradient(105deg,#fcfcff_0%,#f8f9ff_100%)] max-xl:grid-cols-2"
          >
            <div className="flex min-h-[92px] items-center gap-[13px] px-6 py-5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[#edf0ff] text-[#4e62d6]">
                <Database size={20} strokeWidth={1.8} />
              </span>
              <div className="flex min-w-0 flex-col">
                <span className="text-xs text-black/[0.45]">全部数据源</span>
                <strong className="mt-1.5 text-2xl font-bold leading-7 text-[#161823]">
                  {summary.total}
                </strong>
              </div>
            </div>

            <div className="flex min-h-[92px] items-center gap-[13px] border-l border-black/[0.055] px-6 py-5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[#eef9f0] text-[#25a244]">
                <CheckCircle2 size={20} strokeWidth={1.8} />
              </span>
              <div className="flex min-w-0 flex-col">
                <span className="text-xs text-black/[0.45]">连接正常</span>
                <strong className="mt-1.5 text-2xl font-bold leading-7 text-[#161823]">
                  {summary.connected}
                </strong>
              </div>
            </div>

            <div className="flex min-h-[92px] items-center gap-[13px] border-l border-black/[0.055] px-6 py-5 max-xl:border-l-0 max-xl:border-t">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[#fff0f0] text-[#e85959]">
                <XCircle size={20} strokeWidth={1.8} />
              </span>
              <div className="flex min-w-0 flex-col">
                <span className="text-xs text-black/[0.45]">连接异常</span>
                <strong className="mt-1.5 text-2xl font-bold leading-7 text-[#161823]">
                  {summary.disconnected}
                </strong>
              </div>
            </div>

            <div className="flex min-h-[92px] items-center gap-[13px] border-l border-black/[0.055] px-6 py-5 max-xl:border-t">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[#eef2f6] text-[#617084]">
                <Server size={20} strokeWidth={1.8} />
              </span>
              <div className="flex min-w-0 flex-col">
                <span className="text-xs text-black/[0.45]">运行环境</span>
                <strong className="mt-1.5 text-2xl font-bold leading-7 text-[#161823]">
                  {summary.environmentCount}
                </strong>
              </div>
            </div>
          </motion.section>

          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="mt-[26px] flex min-h-[62px] items-end justify-between gap-6 border-b border-black/[0.075] max-xl:flex-col max-xl:items-stretch max-xl:gap-3"
          >
            <div className="h-[35px]">
              <YakTab
                size="small"
                activeKey={environmentFilter || "all"}
                className={[
                  "[&_.ant-tabs-tab.ant-tabs-tab-active_.ant-tabs-tab-btn]:!text-[#292c35]",
                  "[&_.ant-tabs-tab::after]:!bg-[#252832]",
                ].join(" ")}
                items={environmentTabs.map((item) => ({
                  key: item.key,
                  label: item.label,
                }))}
                onChange={(key) => {
                  const target = environmentTabs.find(
                    (item) => item.key === key
                  );

                  setEnvironmentFilter(target?.value);
                  resetPage();
                }}
              />
            </div>

            <div className="flex items-center gap-2 pb-[11px] max-xl:justify-end max-xl:pb-3">
              <Select
                allowClear
                variant="filled"
                value={dbTypeFilter}
                className={[
                  "!w-[132px]",
                  "[&_.ant-select-selector]:!h-9",
                  "[&_.ant-select-selector]:!rounded-lg",
                  "[&_.ant-select-selection-item]:!leading-[36px]",
                  "[&_.ant-select-selection-placeholder]:!leading-[36px]",
                ].join(" ")}
                placeholder="数据源类型"
                options={COMMON_DB_OPTIONS}
                popupMatchSelectWidth={180}
                onChange={(value) => {
                  setDbTypeFilter(value);
                  resetPage();
                }}
              />

              <Input
                allowClear
                variant="filled"
                value={searchKeyword}
                prefix={<Search size={15} strokeWidth={1.8} />}
                className={[
                  "!w-[300px]",
                  "[&.ant-input-affix-wrapper]:!h-9",
                  "[&.ant-input-affix-wrapper]:!rounded-lg",
                  "[&_.ant-input]:!text-xs",
                ].join(" ")}
                placeholder="搜索名称或连接地址"
                onChange={(event) => {
                  setSearchKeyword(event.target.value);
                  resetPage();
                }}
              />

              {hasActiveFilters && (
                <YakButton
                  type="text"
                  size="small"
                  onClick={handleResetFilters}
                >
                  重置
                </YakButton>
              )}

              <div className="flex overflow-hidden rounded-[7px] border border-black/[0.09] bg-white">
                <YakButton
                  type="text"
                  iconOnly
                  title="卡片视图"
                  className={[
                    "!h-[34px] !w-[34px] !rounded-none !border-0 !p-0",
                    viewMode === "grid"
                      ? "!bg-[#f7f8fa] !text-[#252832]"
                      : "!bg-white !text-black/[0.53]",
                  ].join(" ")}
                  icon={<Grid2X2 size={16} strokeWidth={1.8} />}
                  onClick={() => setViewMode("grid")}
                />

                <YakButton
                  type="text"
                  iconOnly
                  title="列表视图"
                  className={[
                    "!h-[34px] !w-[34px] !rounded-none !border-0 !border-l !border-l-black/[0.09] !p-0",
                    viewMode === "list"
                      ? "!bg-[#f7f8fa] !text-[#252832]"
                      : "!bg-white !text-black/[0.53]",
                  ].join(" ")}
                  icon={<LayoutList size={17} strokeWidth={1.8} />}
                  onClick={() => setViewMode("list")}
                />
              </div>
            </div>
          </motion.section>

          <motion.div
            variants={PAGE_ANIMATION.fadeUp}
            className="mb-3 mt-[15px] text-[11px] text-black/40"
          >
            共找到
            <strong className="mx-1 font-semibold text-black/75">
              {pagination.total}
            </strong>
            个数据源
            {hasActiveFilters && " · 当前为筛选结果"}
          </motion.div>

          <Spin spinning={loading}>
            <motion.section
              variants={PAGE_ANIMATION.cardStagger}
              initial="hidden"
              animate="visible"
              className={
                viewMode === "list"
                  ? "grid grid-cols-1 gap-4"
                  : "grid grid-cols-[repeat(auto-fill,minmax(350px,1fr))] gap-4"
              }
            >
              {dataSourceList.map(renderDataSourceCard)}
            </motion.section>

            {!loading && dataSourceList.length === 0 && (
              <DataSourceEmptyState
                filtered={hasActiveFilters}
                canCreate={canCreate}
                onReset={handleResetFilters}
                onCreate={handleCreate}
              />
            )}
          </Spin>

          {pagination.total > 0 && (
            <div className="mt-6 flex justify-end">
              <Pagination
                current={pagination.pageNo}
                pageSize={pagination.pageSize}
                total={pagination.total}
                showSizeChanger
                showQuickJumper
                pageSizeOptions={[10, 20, 50, 100]}
                disabled={loading}
                showTotal={(total, range) =>
                  `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
                }
                onChange={(pageNo, pageSize) =>
                  setPagination((current) => ({
                    ...current,
                    pageNo,
                    pageSize,
                  }))
                }
              />
            </div>
          )}
        </motion.div>
      </div>

      <AddOrEditDataSourceModal ref={modalRef} />
    </>
  );
};

export default DataSourcePage;
