import { Input, Modal } from 'antd';
import { Database, GitBranch, Info, Layers3 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import type {
  DevelopmentReleaseDatasetState,
} from '../../dataset-service';
import type { DevelopmentReleaseSummary } from '../../types';

interface PublishDatasetModalProps {
  open: boolean;
  nodeName: string;
  release?: DevelopmentReleaseSummary;
  datasetState?: DevelopmentReleaseDatasetState;
  publishing: boolean;
  onCancel: () => void;
  onPublish: (payload: { name?: string; description?: string }) => void;
}

const stripSqlSuffix = (value: string) => value.replace(/\.sql$/i, '');

const PublishDatasetModal = ({
  open,
  nodeName,
  release,
  datasetState,
  publishing,
  onCancel,
  onPublish,
}: PublishDatasetModalProps) => {
  const detail = datasetState?.detail || undefined;
  const currentVersion = detail?.currentVersion || undefined;
  const alreadyPublished = Boolean(datasetState?.published && detail);
  const needsUpdate = Boolean(
    release && (!currentVersion || currentVersion.sourceTaskRevisionNo !== release.currentRevisionNo),
  );
  const [name, setName] = useState(stripSqlSuffix(nodeName));
  const [description, setDescription] = useState('');

  useEffect(() => {
    if (!open) return;
    setName(detail?.dataset.name || stripSqlSuffix(nodeName));
    setDescription(detail?.dataset.description || '');
  }, [detail?.dataset.description, detail?.dataset.name, nodeName, open]);

  const fieldSummary = useMemo(() => {
    const fields = detail?.fields || [];
    const dimensions = fields.filter((field) => field.defaultRole === 'DIMENSION').length;
    const measures = fields.filter((field) => field.defaultRole === 'MEASURE').length;
    return { total: fields.length, dimensions, measures };
  }, [detail?.fields]);

  const nextVersionNo = (currentVersion?.versionNo || 0) + 1;
  const confirmText = !alreadyPublished
    ? '发布数据集'
    : needsUpdate
      ? `更新至 DV${nextVersionNo}`
      : '已是最新版本';

  return (
    <Modal
      open={open}
      width={560}
      centered
      destroyOnHidden
      title={alreadyPublished ? '更新 Dataset' : '发布为 Dataset'}
      okText={confirmText}
      cancelText="取消"
      confirmLoading={publishing}
      okButtonProps={{
        disabled: !release || !needsUpdate || (!alreadyPublished && !name.trim()),
      }}
      onCancel={onCancel}
      onOk={() => onPublish({
        name: alreadyPublished ? undefined : name.trim(),
        description: alreadyPublished ? undefined : description.trim() || undefined,
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
              <Layers3 size={13} /> Dataset 版本
            </div>
            <div className="mt-1 text-[13px] font-medium text-[#344054]">
              {currentVersion ? `DV${currentVersion.versionNo}` : '未发布'}
            </div>
          </div>
        </div>

        {!alreadyPublished ? (
          <>
            <div>
              <div className="mb-1.5 text-[12px] font-medium text-[#344054]">数据集名称</div>
              <Input
                value={name}
                maxLength={200}
                placeholder="请输入数据集名称"
                onChange={(event) => setName(event.target.value)}
              />
            </div>
            <div>
              <div className="mb-1.5 text-[12px] font-medium text-[#344054]">描述</div>
              <Input.TextArea
                value={description}
                maxLength={2000}
                autoSize={{ minRows: 3, maxRows: 5 }}
                placeholder="说明这个 Dataset 的业务口径和使用场景（可选）"
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
          </>
        ) : (
          <div className="border border-[#e5e7eb] px-3 py-3">
            <div className="flex items-center gap-2">
              <Database size={15} className="text-[#667085]" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-[13px] font-medium text-[#344054]">{detail?.dataset.name}</div>
                <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                  Dataset #{detail?.dataset.id} · {detail?.dataset.status === 'OFFLINE' ? '已下线' : '已上线'}
                </div>
              </div>
            </div>
            <div className="mt-3 grid grid-cols-3 gap-2 text-center">
              <div className="bg-[#f8f9fb] px-2 py-2">
                <div className="text-[13px] font-medium text-[#344054]">{fieldSummary.total}</div>
                <div className="text-[10px] text-[#98a2b3]">当前字段</div>
              </div>
              <div className="bg-[#f8f9fb] px-2 py-2">
                <div className="text-[13px] font-medium text-[#344054]">{fieldSummary.dimensions}</div>
                <div className="text-[10px] text-[#98a2b3]">维度</div>
              </div>
              <div className="bg-[#f8f9fb] px-2 py-2">
                <div className="text-[13px] font-medium text-[#344054]">{fieldSummary.measures}</div>
                <div className="text-[10px] text-[#98a2b3]">指标</div>
              </div>
            </div>
          </div>
        )}

        <div className="flex gap-2 bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
          <Info size={14} className="mt-0.5 shrink-0" />
          <div>
            发布时会基于 SQL 当前线上不可变版本自动发现结果字段；数值字段默认识别为指标，其他字段默认识别为维度。后续 SQL 发布新版本时，只会追加新的 DatasetVersion，不会创建新的 Dataset。
          </div>
        </div>

        {alreadyPublished && !needsUpdate ? (
          <div className="text-[11px] text-[#667085]">
            当前 Dataset 已经同步到 SQL v{release?.currentRevisionNo}，无需重复发布。
          </div>
        ) : null}
        {detail?.dataset.status === 'OFFLINE' && needsUpdate ? (
          <div className="text-[11px] text-[#b54708]">
            当前 Dataset 已下线。更新版本不会自动重新上线，仍需在 Dataset 生命周期中显式上线。
          </div>
        ) : null}
      </div>
    </Modal>
  );
};

export default PublishDatasetModal;
