import { EyeOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Typography } from 'antd';
import { useMemo } from 'react';

import { YakButton } from '@/components/ui';
import type { OperationLog } from '@/services/security/operationLogs';

import { formatSystemDateTime } from '../../utils';

interface UseOperationLogColumnsOptions {
  onDetail: (log: OperationLog) => void;
}

export function useOperationLogColumns({
  onDetail,
}: UseOperationLogColumnsOptions): ProColumns<OperationLog>[] {
  return useMemo<ProColumns<OperationLog>[]>(
    () => [
      {
        title: '日志 ID',
        dataIndex: 'id',
        width: 110,
        copyable: true,
        search: false,
      },
      {
        title: '操作',
        dataIndex: 'operateType',
        width: 190,
        search: false,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="truncate font-medium text-slate-700">
              {row.operateType || '-'}
            </div>
            {row.operationMethods && (
              <div className="mt-1">
                <Tag className="!mr-0">{row.operationMethods}</Tag>
              </div>
            )}
          </div>
        ),
      },
      {
        title: '操作人',
        dataIndex: 'operator',
        width: 180,
        search: false,
        render: (_, row) => (
          <div className="min-w-0">
            <div className="truncate text-slate-700">
              {row.operator || '-'}
            </div>
            <div className="mt-1 truncate font-mono text-xs text-slate-400">
              {row.operatorIp || '-'}
            </div>
          </div>
        ),
      },
      {
        title: '操作页面',
        dataIndex: 'operatePage',
        width: 180,
        ellipsis: true,
        search: false,
        renderText: (value) => value || '-',
      },
      {
        title: '操作对象',
        dataIndex: 'target',
        width: 260,
        search: false,
        render: (_, row) => (
          <div className="min-w-0">
            <Typography.Text
              ellipsis={{ tooltip: row.target }}
              copyable={row.target ? { text: row.target } : false}
              className="max-w-full"
            >
              {row.target || '-'}
            </Typography.Text>
            <div className="mt-1 text-xs text-slate-400">
              {row.targetType || '未分类'}
            </div>
          </div>
        ),
      },
      {
        title: '操作时间',
        dataIndex: 'createTime',
        width: 175,
        search: false,
        renderText: (value) => formatSystemDateTime(value),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 90,
        fixed: 'right',
        render: (_, row) => (
          <YakButton
            type="text"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => onDetail(row)}
          >
            详情
          </YakButton>
        ),
      },
    ],
    [onDetail],
  );
}
