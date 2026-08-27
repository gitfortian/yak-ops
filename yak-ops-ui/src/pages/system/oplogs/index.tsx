import { FileSearchOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  useCallback,
  useEffect,
  useRef,
} from 'react';

import {
  SecurityPagination,
  SecurityQueryTable,
} from '@/components/security';
import type { OperationLog } from '@/services/security/operationLogs';

import SystemManagementPage from '../components/SystemManagementPage';
import OperationLogDetailDrawer, {
  type OperationLogDetailDrawerRef,
} from './components/OperationLogDetailDrawer';
import OperationLogFilterBar from './components/OperationLogFilterBar';
import { useOperationLogColumns } from './hooks/useOperationLogColumns';
import { useOperationLogs } from './hooks/useOperationLogs';

export default function OperationLogsPage() {
  const detailRef = useRef<OperationLogDetailDrawerRef>(null);
  const {
    logs,
    options,
    isLoading,
    pagination,
    refreshLogs,
    searchLogs,
    changePage,
  } = useOperationLogs();

  useEffect(() => {
    const value = new URLSearchParams(
      history.location.search,
    ).get('messageLogId');
    if (!value) return;

    const logId = Number(value);
    if (Number.isSafeInteger(logId) && logId > 0) {
      void detailRef.current?.open(logId);
    }
  }, []);

  const showDetail = useCallback((log: OperationLog) => {
    void detailRef.current?.open(log.id);
  }, []);

  const columns = useOperationLogColumns({
    onDetail: showDetail,
  });

  return (
    <SystemManagementPage
      title="操作日志"
      titleId="system-operation-logs-title"
      icon={<FileSearchOutlined className="text-slate-500" />}
      className="min-h-[calc(100vh-64px)] overflow-hidden"
    >
      <div className="shrink-0">
        <OperationLogFilterBar
          options={options}
          loading={isLoading}
          onSearch={searchLogs}
          onRefresh={refreshLogs}
        />

        <SecurityQueryTable<OperationLog>
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={isLoading}
          pagination={false}
          search={false}
          options={false}
          toolBarRender={false}
          bordered
          scroll={{ x: 'max-content' }}
        />
      </div>

      <div className="min-h-6 flex-1" />

      <SecurityPagination
        current={pagination.current}
        pageSize={pagination.pageSize}
        total={pagination.total}
        disabled={isLoading}
        onChange={changePage}
      />

      <OperationLogDetailDrawer ref={detailRef} />
    </SystemManagementPage>
  );
}
