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
      <div className="min-h-full bg-[#f7f8fa] text-[#242731]">
        <motion.main
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
          className="px-4 pb-4 pt-4"
        >
          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="rounded-[18px] border border-[#eef0f2] bg-white px-6 pb-5 pt-5 shadow-[0_2px_10px_rgba(31,35,41,0.025)] max-md:px-4"
          >
            <DataSourcePageHeader
              canCreate={permissions.canCreate}
              onCreate={handleCreate}
            />

            <DataSourceSummaryCards summary={summary} />

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

            <Spin spinning={loading}>
              <motion.section
                variants={PAGE_ANIMATION.cardStagger}
                initial="hidden"
                animate="visible"
                className={
                  viewMode === 'list'
                    ? 'mt-4 grid grid-cols-1 gap-[14px]'
                    : 'mt-4 grid grid-cols-1 gap-[14px] md:grid-cols-2 2xl:grid-cols-3'
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
              <div className="mt-5 flex justify-end border-t border-[#eef0f2] pt-4">
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
