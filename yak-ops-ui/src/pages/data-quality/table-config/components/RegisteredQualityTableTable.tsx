import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import type { TableAssetView } from '@/services/data-quality';
import { Pagination, Spin, Table, Tag, Tooltip } from 'antd';

import { CheckResultTag } from '../../components/QualityStatus';
import { dataQualityTableClassName } from '../../components/tableStyle';
import { QUALITY_TABLE_PAGE_SIZE } from '../constants';

interface RegisteredQualityTableTableProps {
  assets: TableAssetView[];
  total: number;
  current: number;
  loading: boolean;
  onCurrentChange: (current: number) => void;
  onOpenRuleManagement: (record: TableAssetView) => void;
  onCreateMonitor: (record: TableAssetView) => void;
}

const RegisteredQualityTableTable = ({
  assets,
  total,
  current,
  loading,
  onCurrentChange,
  onOpenRuleManagement,
  onCreateMonitor,
}: RegisteredQualityTableTableProps) => (
  <Spin spinning={loading} wrapperClassName="min-h-0 flex-1">
    <div className="flex h-full min-h-0 flex-col">
      <div className="min-h-0 flex-1 overflow-auto">
        <Table<TableAssetView>
          rowKey="id"
          size="small"
          bordered
          pagination={false}
          dataSource={assets}
          scroll={{ x: 1250, y: 'calc(100vh - 260px)' }}
          className={dataQualityTableClassName()}
          locale={{
            emptyText: (
              <div className="flex min-h-[220px] items-center justify-center">
                <YakOpsEmpty
                  width={176}
                  height={120}
                  title="暂无已注册数据表"
                  description="点击右上角注册数据表后开始配置质量监控"
                />
              </div>
            ),
          }}
          columns={[
            {
              title: '表名 / 描述 / 路径',
              dataIndex: 'tableName',
              minWidth: 330,
              render: (_, record) => (
                <div className="min-w-0 py-1">
                  <div className="truncate font-medium text-[#172033]">
                    {record.tableName}
                  </div>
                  {record.remarks ? (
                    <div className="mt-1 line-clamp-1 text-xs text-[#667085]">
                      {record.remarks}
                    </div>
                  ) : null}
                  <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
                    {[
                      record.databaseName,
                      record.schemaName,
                      record.tableName,
                    ]
                      .filter(Boolean)
                      .join(' / ')}
                  </div>
                </div>
              ),
            },
            {
              title: '数据源 / 类型',
              width: 190,
              render: (_, record) => (
                <div className="space-y-1 py-0.5">
                  <div className="truncate text-[#344054]">
                    {record.dataSourceName}
                  </div>
                  <Tag className="!m-0 !border-0 !bg-[#f2f4f7] !text-[11px] !text-[#667085]">
                    {record.tableType || 'TABLE'}
                  </Tag>
                </div>
              ),
            },
            {
              title: '监控 / 规则',
              width: 170,
              render: (_, record) => (
                <div className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                  <span className="text-[#98a2b3]">监控数：</span>
                  <span className="font-medium text-[#344054]">
                    {record.monitorCount}
                  </span>
                  <span className="text-[#98a2b3]">规则数：</span>
                  <span className="font-medium text-[#344054]">
                    {record.ruleCount}
                  </span>
                </div>
              ),
            },
            {
              title: '最近状态',
              width: 190,
              render: (_, record) => (
                <div className="space-y-1.5 py-0.5">
                  <CheckResultTag value={record.lastResult} />
                  <div className="text-[11px] text-[#98a2b3]">
                    {record.lastRunTime || '暂无运行记录'}
                  </div>
                </div>
              ),
            },
            {
              title: '注册信息',
              width: 190,
              render: (_, record) => (
                <div className="space-y-1 text-xs">
                  <div className="text-[#344054]">{record.registeredBy}</div>
                  <div className="text-[#98a2b3]">{record.registeredAt}</div>
                </div>
              ),
            },
            {
              title: '操作',
              width: 210,
              fixed: 'right',
              render: (_, record) => (
                <div
                  className="flex items-center gap-1.5 whitespace-nowrap"
                  onClick={(event) => event.stopPropagation()}
                >
                  <YakButton
                    type="primary"
                    size="small"
                    className="!h-7 !rounded-md !px-2.5 !text-xs"
                    onClick={() => onCreateMonitor(record)}
                  >
                    新增监控
                  </YakButton>

                  <Tooltip
                    title={
                      record.monitorId
                        ? undefined
                        : '请先新增监控，再进行规则管理'
                    }
                  >
                    <span>
                      <YakButton
                        size="small"
                        disabled={!record.monitorId}
                        className="!h-7 !rounded-md !px-2.5 !text-xs"
                        onClick={() => onOpenRuleManagement(record)}
                      >
                        规则管理
                      </YakButton>
                    </span>
                  </Tooltip>
                </div>
              ),
            },
          ]}
        />
      </div>

      {total > 0 ? (
        <div className="flex shrink-0 justify-end border-t border-[#f0f0f1] px-3 py-3">
          <Pagination
            size="small"
            current={current}
            pageSize={QUALITY_TABLE_PAGE_SIZE}
            total={total}
            showSizeChanger={false}
            showTotal={(value) => `共 ${value} 张已注册表`}
            onChange={onCurrentChange}
          />
        </div>
      ) : null}
    </div>
  </Spin>
);

export default RegisteredQualityTableTable;
