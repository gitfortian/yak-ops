import { YakButton } from '@/components/ui';
import type {
  ComputeEnvironmentOption,
  DataSourceOption,
  RealtimeAction,
  RealtimeJob,
} from '@/services/realtime-sync';
import { CopyOutlined } from '@ant-design/icons';
import { Empty, Table, Tooltip } from 'antd';
import type { TableColumnsType } from 'antd';
import { useMemo } from 'react';

import {
  REALTIME_OBSERVED_STATE_LABELS,
  REALTIME_RELEASE_STATE_LABELS,
} from '../constants';
import type { RealtimePaginationState } from '../types';
import RealtimeSyncActionColumn from './RealtimeSyncActionColumn';
import RealtimeSyncPagination from './RealtimeSyncPagination';
import RealtimeSyncStatusBadge from './RealtimeSyncStatusBadge';

interface RealtimeSyncTaskTableProps {
  jobs: RealtimeJob[];
  loading: boolean;
  pagination: RealtimePaginationState;
  dataSourceMap: Map<string, DataSourceOption>;
  environmentMap: Map<number, ComputeEnvironmentOption>;
  onPaginationChange: (page: number, pageSize: number) => void;
  onCopyTaskId: (id: number) => void;
  onEdit: (job: RealtimeJob) => void;
  onDetail: (job: RealtimeJob) => void;
  onDelete: (job: RealtimeJob) => Promise<void>;
  onAction: (job: RealtimeJob, action: RealtimeAction) => Promise<void>;
}

const RealtimeSyncTaskTable = ({
  jobs,
  loading,
  pagination,
  dataSourceMap,
  environmentMap,
  onPaginationChange,
  onCopyTaskId,
  onEdit,
  onDetail,
  onDelete,
  onAction,
}: RealtimeSyncTaskTableProps) => {
  const columns = useMemo<TableColumnsType<RealtimeJob>>(
    () => [
      {
        title: '名称 / ID',
        dataIndex: 'name',
        width: 250,
        render: (value, job) => (
          <div className="min-w-0 py-0.5">
            <button
              type="button"
              className="block max-w-full truncate text-left text-[13px] font-medium leading-5 text-[#344054] hover:text-[#ff4d4f]"
              title={value}
              onClick={() => onDetail(job)}
            >
              {value || '-'}
            </button>
            <div className="mt-0.5 flex h-5 items-center gap-1 text-[11px] leading-5 text-[#98a2b3]">
              <span className="truncate">
                ID：{job.id} · v{job.definitionVersion}
              </span>
              <Tooltip title="复制任务 ID">
                <YakButton
                  type="text"
                  size="small"
                  iconOnly
                  icon={<CopyOutlined className="text-[11px]" />}
                  className="!h-5 !w-5 !min-w-0 !p-0 !text-[#98a2b3] hover:!bg-[#f2f4f7] hover:!text-[#475467]"
                  onClick={(event) => {
                    event.stopPropagation();
                    onCopyTaskId(job.id);
                  }}
                />
              </Tooltip>
            </div>
          </div>
        ),
      },
      {
        title: '数据源同步方案',
        dataIndex: 'syncPlan',
        width: 300,
        render: (_value, job) => {
          const source = dataSourceMap.get(
            String(job.spec?.sourceDataSourceRef),
          );
          const sink = dataSourceMap.get(
            String(job.spec?.sinkDataSourceRef),
          );
          const sourceLabel =
            source?.label ||
            `数据源 #${job.spec?.sourceDataSourceRef || '-'}`;
          const sinkLabel =
            sink?.label || `数据源 #${job.spec?.sinkDataSourceRef || '-'}`;

          return (
            <div className="min-w-0 py-0.5 text-[12px] leading-5 text-[#667085]">
              <div className="flex min-w-0 items-center gap-2">
                <span
                  className="max-w-[118px] truncate font-medium text-[#475467]"
                  title={sourceLabel}
                >
                  {sourceLabel}
                </span>
                <span className="text-[#98a2b3]">→</span>
                <span
                  className="max-w-[118px] truncate font-medium text-[#475467]"
                  title={sinkLabel}
                >
                  {sinkLabel}
                </span>
              </div>
              <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                {source?.dbType || '-'} → {sink?.dbType || '-'} ·{' '}
                {job.spec?.tables?.length || 0} 张表
              </div>
            </div>
          );
        },
      },
      {
        title: '发布状态',
        dataIndex: 'releaseState',
        width: 115,
        align: 'center',
        render: (value, job) => (
          <div className="flex flex-col items-center gap-1">
            <RealtimeSyncStatusBadge
              state={value}
              label={REALTIME_RELEASE_STATE_LABELS[value] || value}
            />
            {job.publishedUpdateAvailable ? (
              <span className="text-[10px] leading-4 text-[#b54708]">
                有更新可应用
              </span>
            ) : null}
          </div>
        ),
      },
      {
        title: '运行状态',
        dataIndex: 'observedState',
        width: 145,
        render: (value, job) => (
          <div className="flex flex-col items-start gap-1">
            <RealtimeSyncStatusBadge
              state={value}
              label={REALTIME_OBSERVED_STATE_LABELS[value] || value}
            />
            <span className="text-[10px] leading-4 text-[#98a2b3]">
              期望：{job.desiredState === 'RUNNING' ? '运行' : '停止'}
            </span>
          </div>
        ),
      },
      {
        title: 'Flink 运行时',
        dataIndex: 'runtime',
        width: 210,
        render: (_value, job) => {
          const deploymentEnvironment =
            job.latestDeployment?.runtimeEnvironment;
          const definitionEnvironment = job.runtimeEnvironmentId
            ? environmentMap.get(job.runtimeEnvironmentId)
            : undefined;
          const environmentName =
            deploymentEnvironment?.name || definitionEnvironment?.name;

          return (
            <div className="text-[12px] leading-5 text-[#667085]">
              <div
                className="truncate font-medium text-[#475467]"
                title={environmentName}
              >
                {environmentName ||
                  `环境 #${job.runtimeEnvironmentId || '-'}`}
              </div>
              <div
                className="truncate text-[11px] text-[#98a2b3]"
                title={job.latestDeployment?.runtimeRevision}
              >
                {job.latestDeployment?.runtimeRevision || '尚未部署'}
              </div>
              {job.latestDeployment?.engineJobId ? (
                <div className="truncate text-[10px] text-[#b0b7c3]">
                  Job {job.latestDeployment.engineJobId}
                </div>
              ) : null}
            </div>
          );
        },
      },
      {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 170,
        render: (value?: string) => (
          <span className="whitespace-nowrap text-[12px] leading-5 text-[#98a2b3]">
            {value || '-'}
          </span>
        ),
      },
      {
        title: '操作',
        key: 'operate',
        fixed: 'right',
        width: 215,
        render: (_value, job) => (
          <RealtimeSyncActionColumn
            job={job}
            environment={environmentMap.get(job.runtimeEnvironmentId)}
            onEdit={onEdit}
            onDetail={onDetail}
            onDelete={onDelete}
            onAction={onAction}
          />
        ),
      },
    ],
    [
      dataSourceMap,
      environmentMap,
      onAction,
      onCopyTaskId,
      onDelete,
      onDetail,
      onEdit,
    ],
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1">
        <Table<RealtimeJob>
          rowKey="id"
          loading={loading}
          dataSource={jobs}
          columns={columns}
          bordered
          size="small"
          pagination={false}
          scroll={{ x: 'max-content' }}
          className={[
            'compact-sync-task-table',
            '[&_.ant-table]:!text-[13px]',
            '[&_.ant-table-container]:!border-[#eaecf0]',
            '[&_.ant-table-cell]:!align-middle',
            '[&_.ant-table-thead>tr>th]:!h-10',
            '[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb]',
            '[&_.ant-table-thead>tr>th]:!px-4',
            '[&_.ant-table-thead>tr>th]:!py-2',
            '[&_.ant-table-thead>tr>th]:!text-[12px]',
            '[&_.ant-table-thead>tr>th]:!font-medium',
            '[&_.ant-table-thead>tr>th]:!text-[#667085]',
            '[&_.ant-table-thead>tr>th]:!border-[#eaecf0]',
            '[&_.ant-table-tbody>tr>td]:!px-4',
            '[&_.ant-table-tbody>tr>td]:!py-2.5',
            '[&_.ant-table-tbody>tr>td]:!border-[#f0f2f5]',
            '[&_.ant-table-tbody>tr>td]:!text-[#667085]',
            '[&_.ant-table-tbody>tr:hover>td]:!bg-[#fafbfc]',
            '[&_.ant-table-cell-fix-right]:!bg-white',
            '[&_.ant-table-tbody>tr:hover_.ant-table-cell-fix-right]:!bg-[#fafbfc]',
            '[&_.ant-table-placeholder>td]:!h-[240px]',
          ].join(' ')}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <span className="text-[12px] text-[#98a2b3]">
                    暂无实时同步任务
                  </span>
                }
              />
            ),
          }}
        />
      </div>

      <div className="sticky bottom-0 z-20 mt-auto flex min-h-[56px] items-center justify-end border border-t-0 border-[#e5e7eb] bg-white px-5 py-3 shadow-[0_-4px_12px_rgba(16,24,40,0.04)]">
        <RealtimeSyncPagination
          total={pagination.total}
          current={pagination.current}
          pageSize={pagination.pageSize}
          onChange={onPaginationChange}
        />
      </div>
    </div>
  );
};

export default RealtimeSyncTaskTable;
