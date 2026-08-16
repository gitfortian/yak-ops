import { Input, InputNumber, Modal, Switch } from 'antd';
import { ArrowRight, Braces, GitBranch, Info, Link2, ServerCog } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import type {
  DevelopmentReleaseDataServiceState,
  PublishDevelopmentDataServicePayload,
} from '../../data-service-publication';
import type { DevelopmentReleaseSummary } from '../../types';

interface PublishDataServiceModalProps {
  open: boolean;
  nodeName: string;
  release?: DevelopmentReleaseSummary;
  dataServiceState?: DevelopmentReleaseDataServiceState;
  publishing: boolean;
  onCancel: () => void;
  onPublish: (payload: PublishDevelopmentDataServicePayload) => void;
}

const stripSqlSuffix = (value: string) => value.replace(/\.sql$/i, '');

const PublishDataServiceModal = ({
  open,
  nodeName,
  release,
  dataServiceState,
  publishing,
  onCancel,
  onPublish,
}: PublishDataServiceModalProps) => {
  const detail = dataServiceState?.detail || undefined;
  const alreadyPublished = Boolean(dataServiceState?.published && detail);
  const needsUpdate = Boolean(dataServiceState?.updateAvailable);
  const apiRevisionNo = detail?.sourceRevisionNo;
  const [name, setName] = useState(stripSqlSuffix(nodeName));
  const [path, setPath] = useState('');
  const [maxRows, setMaxRows] = useState(1000);
  const [timeoutSeconds, setTimeoutSeconds] = useState(30);
  const [enabled, setEnabled] = useState(true);
  const [description, setDescription] = useState('');

  useEffect(() => {
    if (!open) return;
    setName(detail?.name || stripSqlSuffix(nodeName));
    setPath(detail?.path || (release ? `/query/${release.assetId}` : ''));
    setMaxRows(detail?.maxRows || 1000);
    setTimeoutSeconds(detail?.timeoutSeconds || 30);
    setEnabled(detail?.enabled ?? true);
    setDescription(detail?.description || '');
  }, [
    detail?.description,
    detail?.enabled,
    detail?.maxRows,
    detail?.name,
    detail?.path,
    detail?.timeoutSeconds,
    nodeName,
    open,
    release,
  ]);

  const runtimePath = useMemo(() => {
    const normalized = path.trim();
    if (!normalized) return '/api/v1/data-service/runtime/...';
    return `/api/v1/data-service/runtime${normalized.startsWith('/') ? normalized : `/${normalized}`}`;
  }, [path]);

  const canPublish = Boolean(
    release
      && release.status === 'ONLINE'
      && name.trim()
      && path.trim()
      && maxRows >= 1
      && maxRows <= 10000
      && timeoutSeconds >= 1
      && timeoutSeconds <= 3600,
  );

  const confirmText = !alreadyPublished
    ? `发布 API · SQL v${release?.currentRevisionNo || '-'}`
    : needsUpdate
      ? `更新 API · v${apiRevisionNo || '-'} → v${release?.currentRevisionNo || '-'}`
      : '保存 API 配置';

  const modalTitle = !alreadyPublished
    ? '发布 API'
    : needsUpdate
      ? '更新 API'
      : 'API 发布配置';

  const statusTitle = !alreadyPublished
    ? '未发布'
    : needsUpdate
      ? '待更新'
      : detail?.enabled === false
        ? '已同步 · 已停用'
        : '已同步';

  const statusDescription = !alreadyPublished
    ? `本次将以当前 ONLINE SQL v${release?.currentRevisionNo || '-'} 创建稳定的 API 快照。`
    : needsUpdate
      ? `线上 API 仍运行 SQL v${apiRevisionNo || '-'}。只有确认更新后，Runtime 才会切换到当前 ONLINE SQL v${release?.currentRevisionNo || '-'}。`
      : detail?.enabled === false
        ? `API 快照已经同步 SQL v${apiRevisionNo || release?.currentRevisionNo || '-'}，但当前服务处于停用状态。`
        : `线上 API 已经同步当前 ONLINE SQL v${apiRevisionNo || release?.currentRevisionNo || '-'}。`;

  return (
    <Modal
      open={open}
      width={700}
      centered
      destroyOnHidden
      title={modalTitle}
      okText={confirmText}
      cancelText="取消"
      confirmLoading={publishing}
      okButtonProps={{ disabled: !canPublish }}
      onCancel={onCancel}
      onOk={() => onPublish({
        name: name.trim(),
        path: path.trim(),
        maxRows,
        timeoutSeconds,
        enabled,
        description: description.trim() || undefined,
      })}
    >
      <div className="space-y-4 py-2">
        <div className="grid grid-cols-[1fr_36px_1fr] items-stretch gap-2">
          <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2.5">
            <div className="flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
              <ServerCog size={13} /> 线上 API 快照
            </div>
            <div className="mt-1 text-[13px] font-medium text-[#344054]">
              {alreadyPublished ? `SQL v${apiRevisionNo || '-'}` : '未发布'}
            </div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              {alreadyPublished ? `API #${detail?.id}` : '尚未创建 Runtime 快照'}
            </div>
          </div>

          <div className="flex items-center justify-center text-[#98a2b3]">
            <ArrowRight
              size={16}
              className={needsUpdate || !alreadyPublished ? 'text-[var(--yak-brand-color)]' : undefined}
            />
          </div>

          <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2.5">
            <div className="flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
              <GitBranch size={13} /> 当前 ONLINE SQL
            </div>
            <div className="mt-1 text-[13px] font-medium text-[#344054]">
              {release ? `SQL v${release.currentRevisionNo}` : '尚未发布'}
            </div>
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">
              {release ? `Revision #${release.currentRevisionId}` : '请先发布 SQL 版本'}
            </div>
          </div>
        </div>

        <div
          className={[
            'flex gap-2 border px-3 py-2.5 text-[11px] leading-5',
            needsUpdate || !alreadyPublished
              ? 'border-[rgba(254,44,85,.18)] bg-[rgba(254,44,85,.035)] text-[#667085]'
              : 'border-[#e5e7eb] bg-[#fafafa] text-[#667085]',
          ].join(' ')}
        >
          <ServerCog
            size={14}
            className={[
              'mt-0.5 shrink-0',
              needsUpdate || !alreadyPublished ? 'text-[var(--yak-brand-color)]' : 'text-[#667085]',
            ].join(' ')}
          />
          <div>
            <div className={[
              'font-medium',
              needsUpdate || !alreadyPublished ? 'text-[var(--yak-brand-color)]' : 'text-[#344054]',
            ].join(' ')}>
              {statusTitle}
            </div>
            <div>{statusDescription}</div>
          </div>
        </div>

        {release?.hasNewerRevision ? (
          <div className="flex gap-2 border border-[#e5e7eb] bg-[#fafafa] px-3 py-2 text-[11px] leading-5 text-[#667085]">
            <Info size={14} className="mt-0.5 shrink-0" />
            <div>
              数据开发还有 SQL v{release.latestRevisionNo} 尚未切换为当前 ONLINE 版本；本次 API 发布只使用 SQL v{release.currentRevisionNo}。
            </div>
          </div>
        ) : null}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <div className="mb-1.5 text-[12px] font-medium text-[#344054]">服务名称</div>
            <Input
              value={name}
              maxLength={200}
              placeholder="请输入服务名称"
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] font-medium text-[#344054]">接口路径</div>
            <Input
              value={path}
              maxLength={255}
              placeholder="/query/orders"
              onChange={(event) => setPath(event.target.value)}
            />
          </div>
        </div>

        <div className="border border-[#e5e7eb] bg-[#fafafa] px-3 py-2.5">
          <div className="flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
            <Link2 size={13} /> Runtime Endpoint
          </div>
          <div className="mt-1 break-all font-mono text-[11px] text-[#475467]">
            GET {runtimePath}
          </div>
        </div>

        <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-3">
          <div>
            <div className="mb-1.5 text-[12px] font-medium text-[#344054]">最大返回行数</div>
            <InputNumber
              className="w-full"
              min={1}
              max={10000}
              precision={0}
              value={maxRows}
              onChange={(value) => setMaxRows(value || 1000)}
            />
          </div>
          <div>
            <div className="mb-1.5 text-[12px] font-medium text-[#344054]">查询超时（秒）</div>
            <InputNumber
              className="w-full"
              min={1}
              max={3600}
              precision={0}
              value={timeoutSeconds}
              onChange={(value) => setTimeoutSeconds(value || 30)}
            />
          </div>
          <div className="pb-1">
            <div className="mb-1.5 text-[12px] font-medium text-[#344054]">启用</div>
            <Switch checked={enabled} onChange={setEnabled} />
          </div>
        </div>

        <div>
          <div className="mb-1.5 text-[12px] font-medium text-[#344054]">描述</div>
          <Input.TextArea
            value={description}
            maxLength={2000}
            autoSize={{ minRows: 2, maxRows: 4 }}
            placeholder="接口用途、业务口径或调用说明（可选）"
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>

        {alreadyPublished && detail?.parameterNames?.length ? (
          <div className="flex items-start gap-2 border border-[#e5e7eb] px-3 py-2.5">
            <Braces size={14} className="mt-0.5 shrink-0 text-[#667085]" />
            <div className="min-w-0 text-[11px] leading-5 text-[#667085]">
              当前线上 API 参数：{detail.parameterNames.map((parameter) => `:${parameter}`).join('、')}
            </div>
          </div>
        ) : null}

        <div className="flex gap-2 bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
          <Info size={14} className="mt-0.5 shrink-0" />
          <div>
            API 的 SQL 与数据源固定继承当前 ONLINE 发布版本。编辑草稿或生成新的 SQL Revision 不会自动改变线上 API；只有明确执行“发布 API / 更新 API”才会刷新 Runtime 快照。
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default PublishDataServiceModal;
