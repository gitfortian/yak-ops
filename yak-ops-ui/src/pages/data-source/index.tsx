import { useIntl } from '@umijs/max';
import { message, Modal, Pagination, Spin } from 'antd';
import { motion } from 'framer-motion';
import { useRef } from 'react';

import AddOrEditDataSourceModal from './components/AddOrEditDataSourceModal';
import DataSourceCard from './components/DataSourceCard';
import DataSourceEmptyState from './components/DataSourceEmptyState';
import DataSourcePageHeader from './components/DataSourcePageHeader';
import DataSourceSummaryCards from './components/DataSourceSummaryCards';
import DataSourceToolbar from './components/DataSourceToolbar';
import {
  DATA_SOURCE_PAGE_SIZE_OPTIONS,
  PAGE_ANIMATION,
} from './constants';
import { useDataSourcePage } from './hooks/useDataSourcePage';
import type {
  DataSourceModalRef,
  DataSourceRecord,
} from './types';
import {
  DataSourceOperateType,
  dataSourceRecordKey,
} from './types';

const { confirm } = Modal;

const DataSourcePage = () => {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);
  const {
    loading,
    records,
    summary,
    pagination,
    keyword,
    dbType,
    environment,
    viewMode,
    hasActiveFilters,
    permissions,
    testingId,
    editingId,
    setKeyword,
    setDbType,
    setEnvironment,
    setViewMode,
    resetFilters,
    changePage,
    refresh,
    loadRecordForEdit,
    removeRecord,
    testRecord,
  } = useDataSourcePage();

  const handleCreate = () => {
    if (!permissions.canCreate) return;
    modalRef.current?.open({
      operateType: DataSourceOperateType.Create,
      onSuccess: refresh,
    });
  };

  const handleEdit = async (record: DataSourceRecord) => {
    try {
      const detail = await loadRecordForEdit(record);
      if (!detail) return;
      modalRef.current?.open({
        operateType: DataSourceOperateType.Edit,
        currentRecord: detail,
        onSuccess: refresh,
      });
    } catch {
      // The shared request layer owns request error feedback.
    }
  };

  const handleDelete = (record: DataSourceRecord) => {
    if (!permissions.canDelete) return;

    confirm({
      title: intl.formatMessage({
        id: 'pages.datasource.delete.confirmTitle',
        defaultMessage: '确认删除该数据源吗？',
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
      okText: '删除',
      cancelText: '取消',
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        if (record.id === undefined || record.id === null) {
          message.error('数据源 ID 不存在');
          return;
        }

        try {
          const deleted = await removeRecord(record.id);
          if (deleted) message.success('删除成功');
        } catch {
          // The shared request layer owns request error feedback.
        }
      },
    });
  };

  const handleTestConnection = async (record: DataSourceRecord) => {
    try {
      const connected = await testRecord(record);
      if (connected) message.success('连接测试成功');
    } catch {
      // The shared request layer owns request error feedback.
    }
  };

  return (
    <>
      <div className="relative min-h-full overflow-hidden bg-[#f7f8fa] text-[#242731]">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-[120px] -top-[150px] h-[360px] w-[620px] rounded-full bg-[radial-gradient(ellipse_at_center,rgba(175,220,239,0.18)_0%,rgba(213,235,244,0.08)_48%,rgba(255,255,255,0)_74%)] blur-[24px]"
        />
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-[220px] -right-[170px] h-[430px] w-[620px] rounded-full bg-[radial-gradient(circle_at_center,rgba(230,238,181,0.20),rgba(255,255,255,0)_70%)] blur-[22px]"
        />

        <motion.main
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
          className="relative z-10 px-4 pb-4 pt-4"
        >
          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="rounded-[22px] border border-[#f1f1f1] bg-white/[0.76] px-[22px] pb-[22px] pt-6 backdrop-blur-[8px]"
          >
            <DataSourcePageHeader
              canCreate={permissions.canCreate}
              onCreate={handleCreate}
            />
            <DataSourceSummaryCards summary={summary} />
          </motion.section>

          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="mt-4 min-h-[420px] rounded-[22px] border border-[#f1f1f1] bg-white/[0.82] px-[22px] pb-[22px] pt-5 backdrop-blur-[8px]"
          >
            <div className="flex items-end justify-between gap-6">
              <div>
                <h2 className="m-0 text-[18px] font-semibold leading-7 tracking-[-0.3px] text-[#292c35]">
                  数据源列表
                </h2>
                <p className="mb-0 mt-0.5 text-[12px] leading-5 text-[#9a9ea7]">
                  按运行环境与类型筛选，快速检查连接状态
                </p>
              </div>
            </div>

            <DataSourceToolbar
              environment={environment}
              dbType={dbType}
              keyword={keyword}
              viewMode={viewMode}
              hasActiveFilters={hasActiveFilters}
              onEnvironmentChange={setEnvironment}
              onDbTypeChange={setDbType}
              onKeywordChange={setKeyword}
              onViewModeChange={setViewMode}
              onReset={resetFilters}
            />

            <motion.div
              variants={PAGE_ANIMATION.fadeUp}
              className="mb-3.5 mt-4 flex items-center gap-1 text-[11px] leading-5 text-[#9b9fa8]"
            >
              <span>共找到</span>
              <strong className="font-semibold text-[#5f646e]">
                {pagination.total}
              </strong>
              <span>个数据源</span>
              {hasActiveFilters ? (
                <span className="ml-1 rounded-full bg-[#f3f4f6] px-2 py-0.5 text-[10px] text-[#858a94]">
                  筛选结果
                </span>
              ) : null}
            </motion.div>

            <Spin spinning={loading}>
              <motion.section
                variants={PAGE_ANIMATION.cardStagger}
                initial="hidden"
                animate="visible"
                className={
                  viewMode === 'list'
                    ? 'grid grid-cols-1 gap-[14px]'
                    : 'grid grid-cols-1 gap-[14px] md:grid-cols-2 2xl:grid-cols-3'
                }
              >
                {records.map((record, index) => (
                  <DataSourceCard
                    key={
                      dataSourceRecordKey(record.id) ||
                      `${record.name || 'data-source'}-${index}`
                    }
                    record={record}
                    viewMode={viewMode}
                    permissions={permissions}
                    testingId={testingId}
                    editingId={editingId}
                    onEdit={(item) => void handleEdit(item)}
                    onDelete={handleDelete}
                    onTestConnection={(item) => void handleTestConnection(item)}
                  />
                ))}
              </motion.section>

              {!loading && records.length === 0 ? (
                <DataSourceEmptyState
                  filtered={hasActiveFilters}
                  canCreate={permissions.canCreate}
                  onReset={resetFilters}
                  onCreate={handleCreate}
                />
              ) : null}
            </Spin>

            {pagination.total > 0 ? (
              <div className="mt-6 flex justify-end border-t border-[#f0f1f3] pt-4">
                <Pagination
                  current={pagination.pageNo}
                  pageSize={pagination.pageSize}
                  total={pagination.total}
                  showSizeChanger
                  showQuickJumper
                  pageSizeOptions={DATA_SOURCE_PAGE_SIZE_OPTIONS}
                  disabled={loading}
                  showTotal={(total, range) =>
                    `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
                  }
                  onChange={changePage}
                />
              </div>
            ) : null}
          </motion.section>
        </motion.main>
      </div>

      <AddOrEditDataSourceModal ref={modalRef} />
    </>
  );
};

export default DataSourcePage;
