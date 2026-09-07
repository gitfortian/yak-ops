import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  batchReadMessages,
  getMessageDetail,
  type MessageDetail,
  type MessageLevel,
  type MessageStatus,
  markMessageRead,
  notifyMessageCountChanged,
  pageMessages,
  safeMessageActionPath,
  type SecurityMessage,
} from '@/services/security/messages';
import { satisfiesPermissionRequirement } from '@/utils/security/permission';
import { history, useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Checkbox,
  DatePicker,
  Empty,
  message,
  Modal,
  Pagination,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

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

const messageTypeLabel = (value?: string) =>
  (value && MESSAGE_TYPE_LABELS[value]) || value || '消息';

const formatMessageTime = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') return '-';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value);
};

const operationLogIdOf = (item?: SecurityMessage) =>
  item?.operationLogId ?? item?.oplogId;

type MessageDateRange = [Dayjs | null, Dayjs | null] | null;

export default function MessageList() {
  const { initialState } = useModel('@@initialState');
  const { projects } = useSecurityProject();
  const canReadLogs = satisfiesPermissionRequirement(
    initialState?.currentUser?.permissionCodes,
    {
      mode: 'one',
      permission: 'security:operation-log:read',
    },
  );

  const [items, setItems] = useState<SecurityMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selected, setSelected] = useState<Array<number | string>>([]);
  const [detail, setDetail] = useState<MessageDetail>();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<MessageStatus>();
  const [type, setType] = useState<string>();
  const [dateRange, setDateRange] = useState<MessageDateRange>(null);
  const requestSequence = useRef(0);

  const projectNameById = useMemo(
    () =>
      new Map(
        projects.map((project) => [String(project.id), project.projectName]),
      ),
    [projects],
  );

  // Project ownership follows projectId, matching the hardened backend security
  // boundary. scope remains presentation metadata and is not trusted for ownership.
  const ownershipLabel = useCallback(
    (item?: SecurityMessage) => {
      if (item?.projectId === undefined || item.projectId === null) return '系统';
      const projectName = projectNameById.get(String(item.projectId));
      return projectName ? `项目 · ${projectName}` : `项目 #${item.projectId}`;
    },
    [projectNameById],
  );

  const loadMessages = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    try {
      const result = await pageMessages({
        pageNum: page,
        pageSize,
        status,
        type,
        startTime: dateRange?.[0]?.valueOf(),
        endTime: dateRange?.[1]?.valueOf(),
      });
      if (sequence !== requestSequence.current) return;
      setItems(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch {
      if (sequence === requestSequence.current) {
        message.error('消息加载失败');
      }
    } finally {
      if (sequence === requestSequence.current) {
        setLoading(false);
      }
    }
  }, [dateRange, page, pageSize, status, type]);

  useEffect(() => {
    void loadMessages();
  }, [loadMessages]);

  const resetToFirstPage = () => {
    setSelected([]);
    setPage(1);
  };

  const openDetail = async (row: SecurityMessage) => {
    setDetailLoading(true);
    try {
      if (row.status === 'UNREAD') {
        await markMessageRead(row.id);
        setItems((current) =>
          current.map((item) =>
            item.id === row.id ? { ...item, status: 'READ' } : item,
          ),
        );
        setSelected((current) => current.filter((id) => id !== row.id));
        notifyMessageCountChanged();
      }
      setDetail(await getMessageDetail(row.id));
    } catch {
      message.error('消息详情加载失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const toggleSelected = (row: SecurityMessage, checked: boolean) => {
    setSelected((current) => {
      if (checked) {
        return current.includes(row.id) ? current : [...current, row.id];
      }
      return current.filter((id) => id !== row.id);
    });
  };

  const markSelectedRead = async () => {
    if (!selected.length) return;
    try {
      await batchReadMessages(selected);
      const selectedIds = new Set(selected);
      setItems((current) =>
        current.map((item) =>
          selectedIds.has(item.id) ? { ...item, status: 'READ' } : item,
        ),
      );
      setSelected([]);
      notifyMessageCountChanged();
      message.success('已标记为已读');
    } catch {
      message.error('批量标记已读失败');
    }
  };

  const detailActionPath = safeMessageActionPath(detail?.actionPath);
  const detailOperationLogId = operationLogIdOf(detail);

  return (
    <div className="min-h-[520px] bg-white">
      <div className="flex flex-wrap items-center gap-2 px-5 pb-4 pt-1">
        <Select<MessageStatus>
          allowClear
          variant="filled"
          className="w-32"
          placeholder="全部状态"
          value={status}
          options={[
            { label: '未读', value: 'UNREAD' },
            { label: '已读', value: 'READ' },
          ]}
          onChange={(value) => {
            setStatus(value);
            resetToFirstPage();
          }}
        />
        <Select<string>
          allowClear
          variant="filled"
          className="w-32"
          placeholder="全部类型"
          value={type}
          options={Object.entries(MESSAGE_TYPE_LABELS).map(([value, label]) => ({
            value,
            label,
          }))}
          onChange={(value) => {
            setType(value);
            resetToFirstPage();
          }}
        />
        <DatePicker.RangePicker
          allowClear
          variant="filled"
          value={dateRange}
          onChange={(value) => {
            setDateRange(value as MessageDateRange);
            resetToFirstPage();
          }}
        />

        <div className="ml-auto flex items-center gap-2">
          {selected.length > 0 ? (
            <Typography.Text type="secondary" className="text-xs">
              已选择 {selected.length} 条未读消息
            </Typography.Text>
          ) : null}
          <Button disabled={!selected.length} onClick={() => void markSelectedRead()}>
            批量已读
          </Button>
        </div>
      </div>

      <Spin spinning={loading}>
        <div className="min-h-[400px] border-t border-[rgba(22,24,35,0.08)]">
          {!loading && items.length === 0 ? (
            <div className="flex min-h-[400px] items-center justify-center">
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无消息" />
            </div>
          ) : (
            items.map((item) => {
              const unread = item.status === 'UNREAD';
              return (
                <div
                  key={item.id}
                  className="flex items-start gap-3 border-b border-[rgba(22,24,35,0.08)] px-5 py-4 transition-colors duration-150 hover:bg-[#fafafa]"
                >
                  <div className="flex h-6 shrink-0 items-center">
                    <Checkbox
                      aria-label={`选择消息：${item.title}`}
                      checked={selected.includes(item.id)}
                      disabled={!unread}
                      onChange={(event) => toggleSelected(item, event.target.checked)}
                    />
                  </div>

                  <button
                    type="button"
                    className="group min-w-0 flex-1 border-0 bg-transparent p-0 text-left"
                    onClick={() => void openDetail(item)}
                  >
                    <div className="flex min-w-0 items-start gap-3">
                      <span
                        aria-hidden="true"
                        className={[
                          'mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full',
                          unread ? 'bg-[#fe2c55]' : 'bg-transparent',
                        ].join(' ')}
                      />

                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-6">
                          <div
                            className={[
                              'min-w-0 truncate text-[14px] leading-6 text-[#161823]',
                              unread ? 'font-semibold' : 'font-medium',
                            ].join(' ')}
                          >
                            {item.title}
                          </div>
                          <time className="shrink-0 pt-0.5 text-[12px] leading-5 text-[rgba(22,24,35,0.42)]">
                            {formatMessageTime(item.createTime)}
                          </time>
                        </div>

                        <div className="mt-1 line-clamp-2 text-[13px] leading-5 text-[rgba(22,24,35,0.62)]">
                          {item.summary || '暂无摘要'}
                        </div>

                        <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[12px] leading-5 text-[rgba(22,24,35,0.42)]">
                          <span>{messageTypeLabel(item.type)}</span>
                          <span aria-hidden="true">·</span>
                          <span>{ownershipLabel(item)}</span>
                          {item.level ? (
                            <>
                              <span aria-hidden="true">·</span>
                              <span>{MESSAGE_LEVEL_LABELS[item.level]}</span>
                            </>
                          ) : null}
                          {unread ? (
                            <>
                              <span aria-hidden="true">·</span>
                              <span className="font-medium text-[#161823]">未读</span>
                            </>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  </button>
                </div>
              );
            })
          )}
        </div>
      </Spin>

      <div className="flex justify-end px-5 py-4">
        <Pagination
          current={page}
          pageSize={pageSize}
          total={total}
          showSizeChanger
          showTotal={(value) => `共 ${value} 条`}
          onChange={(nextPage, nextPageSize) => {
            setSelected([]);
            setPage(nextPageSize !== pageSize ? 1 : nextPage);
            setPageSize(nextPageSize);
          }}
        />
      </div>

      <Modal
        title={detail?.title ?? '消息详情'}
        width={640}
        open={Boolean(detail)}
        footer={null}
        destroyOnClose
        onCancel={() => setDetail(undefined)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Space direction="vertical" size="middle" className="w-full pt-2">
              <Space wrap>
                <Tag>{messageTypeLabel(detail.type)}</Tag>
                {detail.level ? <Tag>{MESSAGE_LEVEL_LABELS[detail.level]}</Tag> : null}
                <Tag>{ownershipLabel(detail)}</Tag>
                <Typography.Text type="secondary">
                  {formatMessageTime(detail.createTime)}
                </Typography.Text>
              </Space>

              <Typography.Paragraph className="whitespace-pre-wrap break-words">
                {detail.content ?? detail.summary ?? '-'}
              </Typography.Paragraph>

              {detailActionPath ? (
                <Button type="primary" onClick={() => history.push(detailActionPath)}>
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
          ) : null}
        </Spin>
      </Modal>
    </div>
  );
}
