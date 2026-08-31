import { SecurityQueryTable } from '@/components/security';
import {
  batchReadMessages,
  getMessageDetail,
  type MessageDetail,
  type MessageLevel,
  type MessageStatus,
  markMessageRead,
  pageMessages,
  safeMessageActionPath,
  type SecurityMessage,
} from '@/services/security/messages';
import { satisfiesPermissionRequirement } from '@/utils/security/permission';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { history, useModel } from '@umijs/max';
import { Alert, Button, Drawer, message, Space, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useRef, useState } from 'react';

export const MESSAGE_COUNT_CHANGED_EVENT = 'yak-message-count-changed';

const MESSAGE_TYPE_LABELS: Record<string, string> = {
  SYSTEM: '系统',
  SECURITY: '安全',
  TASK: '任务',
  QUALITY: '质量',
};

const MESSAGE_LEVEL_LABELS: Record<MessageLevel, string> = {
  INFO: '信息',
  SUCCESS: '成功',
  WARNING: '警告',
  ERROR: '错误',
};

const MESSAGE_LEVEL_COLORS: Record<MessageLevel, string> = {
  INFO: 'blue',
  SUCCESS: 'green',
  WARNING: 'orange',
  ERROR: 'red',
};

const notifyCountChanged = () =>
  window.dispatchEvent(new Event(MESSAGE_COUNT_CHANGED_EVENT));

const messageTypeLabel = (value?: string) =>
  (value && MESSAGE_TYPE_LABELS[value]) || value || '消息';

const formatMessageTime = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') return '-';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value);
};

const toTimestamp = (value?: string) => {
  if (!value) return undefined;
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.valueOf() : undefined;
};

const operationLogIdOf = (item?: SecurityMessage) =>
  item?.operationLogId ?? item?.oplogId;

export default function MessageList({ compact = false }: { compact?: boolean }) {
  const actionRef = useRef<ActionType>();
  const { initialState } = useModel('@@initialState');
  const canReadLogs = satisfiesPermissionRequirement(
    initialState?.currentUser?.permissionCodes,
    {
      mode: 'one',
      permission: 'security:operation-log:read',
    },
  );
  const [selected, setSelected] = useState<Array<number | string>>([]);
  const [detail, setDetail] = useState<MessageDetail>();

  const read = async (row: SecurityMessage) => {
    if (row.status === 'UNREAD') {
      await markMessageRead(row.id);
      notifyCountChanged();
      actionRef.current?.reload();
    }
    setDetail(await getMessageDetail(row.id));
  };

  const columns: ProColumns<SecurityMessage>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      search: false,
      render: (_, row) => (
        <Button
          type="link"
          className={row.status === 'UNREAD' ? 'font-semibold' : ''}
          onClick={() => read(row)}
        >
          {row.title}
        </Button>
      ),
    },
    { title: '摘要', dataIndex: 'summary', search: false, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      valueType: 'select',
      valueEnum: {
        SYSTEM: { text: '系统' },
        SECURITY: { text: '安全' },
        TASK: { text: '任务' },
        QUALITY: { text: '质量' },
      },
      render: (_, row) => messageTypeLabel(row.type),
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: {
        UNREAD: { text: '未读', status: 'Processing' },
        READ: { text: '已读', status: 'Default' },
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTimeRange',
      render: (_, row) => formatMessageTime(row.createTime),
    },
    {
      title: '关联日志',
      search: false,
      render: (_, row) => {
        const operationLogId = operationLogIdOf(row);
        if (!operationLogId) return '-';
        if (!canReadLogs) {
          return <Typography.Text type="secondary">不可访问</Typography.Text>;
        }
        return (
          <Button
            type="link"
            onClick={() =>
              history.push(
                `/system/oplogs?messageLogId=${encodeURIComponent(operationLogId)}`,
              )
            }
          >
            查看日志
          </Button>
        );
      },
    },
  ];

  const detailActionPath = safeMessageActionPath(detail?.actionPath);
  const detailOperationLogId = operationLogIdOf(detail);

  return (
    <>
      <SecurityQueryTable<SecurityMessage>
        actionRef={actionRef}
        columns={
          compact
            ? columns.filter(
                (column) =>
                  column.dataIndex !== 'summary' && column.dataIndex !== 'type',
              )
            : columns
        }
        search={compact ? false : undefined}
        pagination={{
          defaultPageSize: compact ? 5 : 10,
          showSizeChanger: !compact,
        }}
        rowSelection={{
          selectedRowKeys: selected,
          onChange: (keys) => setSelected(keys as Array<number | string>),
          getCheckboxProps: (row) => ({ disabled: row.status === 'READ' }),
        }}
        request={async (params) => {
          const range = params.createTime as [string, string] | undefined;
          const result = await pageMessages({
            pageNum: params.current ?? 1,
            pageSize: params.pageSize ?? (compact ? 5 : 10),
            status: params.status as MessageStatus,
            type: params.type as string,
            startTime: toTimestamp(range?.[0]),
            endTime: toTimestamp(range?.[1]),
          });
          return {
            data: result.records,
            total: result.total,
            success: true,
          };
        }}
        tableAlertRender={() => `已选择 ${selected.length} 条未读消息`}
        tableAlertOptionRender={() => (
          <Button
            disabled={!selected.length}
            onClick={async () => {
              await batchReadMessages(selected);
              setSelected([]);
              message.success('已标记为已读');
              notifyCountChanged();
              actionRef.current?.reload();
            }}
          >
            批量已读
          </Button>
        )}
      />

      <Drawer
        title={detail?.title ?? '消息详情'}
        width={560}
        open={Boolean(detail)}
        onClose={() => setDetail(undefined)}
        destroyOnClose
      >
        {detail && (
          <Space direction="vertical" size="middle" className="w-full">
            <Space wrap>
              <Tag>{messageTypeLabel(detail.type)}</Tag>
              {detail.level ? (
                <Tag color={MESSAGE_LEVEL_COLORS[detail.level]}>
                  {MESSAGE_LEVEL_LABELS[detail.level]}
                </Tag>
              ) : null}
              {detail.scope ? (
                <Tag>
                  {detail.scope === 'PROJECT'
                    ? `项目${detail.projectId ? ` #${detail.projectId}` : ''}`
                    : '系统'}
                </Tag>
              ) : null}
              <Typography.Text type="secondary">
                {formatMessageTime(detail.createTime)}
              </Typography.Text>
            </Space>

            <Typography.Paragraph className="whitespace-pre-wrap break-words">
              {detail.content ?? detail.summary ?? '-'}
            </Typography.Paragraph>

            {detailActionPath ? (
              <Button
                type="primary"
                onClick={() => history.push(detailActionPath)}
              >
                前往处理
              </Button>
            ) : null}

            {detailOperationLogId &&
              (canReadLogs ? (
                <Button
                  onClick={() =>
                    history.push(
                      `/system/oplogs?messageLogId=${encodeURIComponent(detailOperationLogId)}`,
                    )
                  }
                >
                  查看关联操作日志
                </Button>
              ) : (
                <Alert
                  type="warning"
                  showIcon
                  message="关联日志不可访问"
                  description="当前身份没有操作日志查看权限。"
                />
              ))}
          </Space>
        )}
      </Drawer>
    </>
  );
}
