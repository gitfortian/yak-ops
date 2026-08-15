import { Input, InputNumber, Modal, Switch } from 'antd';
import { Braces, GitBranch, Info, Link2, ServerCog } from 'lucide-react';
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
    ? '发布数据服务'
    : needsUpdate
      ? `更新到 SQL v${release?.currentRevisionNo || '-'}`
      : '保存数据服务';

  return (
    <Modal
      open={open}
      width={680}
      centered
      destroyOnHidden
      title={alreadyPublished ? '更新数据服务' : '发布为数据服务'}
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
        <div className="grid grid-cols-2 gap-2">
          <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2.5">
            <div className="flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
              <GitBranch size={13} /> SQL 发布版本
            </div>
            <div className="mt-1 text-[13px] font-medium text-[#344054]">
              {release ? `v${release.currentRevisionNo}` : '尚未发布'}
            </div>
          </div>
          <div className="border border-[#e5e7eb] bg-[#fafbfc] px-3 py-2.5">
            <div className="flex items-center gap-1.5 text-[11px] text-[#98a2b3]">
              <ServerCog size={13} /> 数据服务状态
            </div>
            <div className="mt-1 text-[13px] font-medium text-[#344054]">
              {!alreadyPublished ? '未发布' : needsUpdate ? '有新 SQL 版本待更新' : '已同步'}
            </div>
          </div>
        </div>

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
              当前参数：{detail.parameterNames.map((name) => `:${name}`).join('、')}
            </div>
          </div>
        ) : null}

        <div className="flex gap-2 bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
          <Info size={14} className="mt-0.5 shrink-0" />
          <div>
            SQL 与数据源固定继承当前 ONLINE 发布版本。后续编辑草稿或发布新的 SQL 版本不会自动改变线上 API；只有再次点击“发布为数据服务”才会更新服务快照。
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default PublishDataServiceModal;
