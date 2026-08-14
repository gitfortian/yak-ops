import { Input, Modal, Select, Spin } from 'antd';
import { Database, GitBranch, Info, Layers3 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import type {
  DevelopmentDatasetFieldDraft,
  DevelopmentReleaseDatasetState,
  PublishDevelopmentDatasetPayload,
} from '../../dataset-service';
import type { DevelopmentReleaseSummary } from '../../types';

interface PublishDatasetModalProps {
  open: boolean;
  nodeName: string;
  release?: DevelopmentReleaseSummary;
  datasetState?: DevelopmentReleaseDatasetState;
  previewFields: DevelopmentDatasetFieldDraft[];
  previewLoading: boolean;
  previewError?: string;
  publishing: boolean;
  onCancel: () => void;
  onPublish: (payload: PublishDevelopmentDatasetPayload) => void;
}

const stripSqlSuffix = (value: string) => value.replace(/\.sql$/i, '');

const fieldTypeLabel: Record<DevelopmentDatasetFieldDraft['dataType'], string> = {
  STRING: '字符串',
  NUMBER: '数值',
  DATE: '日期',
  DATETIME: '日期时间',
  BOOLEAN: '布尔',
  UNKNOWN: '未知',
};

const PublishDatasetModal = ({
  open,
  nodeName,
  release,
  datasetState,
  previewFields,
  previewLoading,
  previewError,
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
  const [fields, setFields] = useState<DevelopmentDatasetFieldDraft[]>([]);

  useEffect(() => {
    if (!open) return;
    setName(detail?.dataset.name || stripSqlSuffix(nodeName));
    setDescription(detail?.dataset.description || '');
  }, [detail?.dataset.description, detail?.dataset.name, nodeName, open]);

  useEffect(() => {
    if (!open) return;
    const currentByPhysicalName = new Map(
      (detail?.fields || []).map((field) => [field.physicalName.toLowerCase(), field]),
    );
    setFields(previewFields.map((field) => {
      const current = currentByPhysicalName.get(field.physicalName.toLowerCase());
      return {
        ...field,
        fieldId: current?.fieldId || field.fieldId,
        displayName: current?.displayName || field.displayName,
        description: current?.description || field.description,
        defaultRole: current?.defaultRole || field.defaultRole,
      };
    }));
  }, [detail?.fields, open, previewFields]);

  const fieldSummary = useMemo(() => {
    const dimensions = fields.filter((field) => field.defaultRole === 'DIMENSION').length;
    const measures = fields.filter((field) => field.defaultRole === 'MEASURE').length;
    return { total: fields.length, dimensions, measures };
  }, [fields]);

  const nextVersionNo = (currentVersion?.versionNo || 0) + 1;
  const confirmText = !alreadyPublished
    ? '发布数据集'
    : needsUpdate
      ? `更新至 DV${nextVersionNo}`
      : '已是最新版本';
  const canPublish = Boolean(
    release
      && release.status === 'ONLINE'
      && needsUpdate
      && !previewLoading
      && !previewError
      && fields.length
      && (alreadyPublished || name.trim()),
  );

  const updateField = (
    physicalName: string,
    patch: Partial<DevelopmentDatasetFieldDraft>,
  ) => setFields((current) => current.map((field) => (
    field.physicalName === physicalName ? { ...field, ...patch } : field
  )));

  return (
    <Modal
      open={open}
      width={760}
      centered
      destroyOnHidden
      title={alreadyPublished ? '更新 Dataset' : '发布为 Dataset'}
      okText={confirmText}
      cancelText="取消"
      confirmLoading={publishing}
      okButtonProps={{ disabled: !canPublish }}
      onCancel={onCancel}
      onOk={() => onPublish({
        name: alreadyPublished ? undefined : name.trim(),
        description: alreadyPublished ? undefined : description.trim() || undefined,
        fields,
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
          <div className="grid grid-cols-2 gap-3">
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
              <Input
                value={description}
                maxLength={2000}
                placeholder="业务口径或使用场景（可选）"
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-2 border border-[#e5e7eb] px-3 py-2.5">
            <Database size={15} className="text-[#667085]" />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-medium text-[#344054]">{detail?.dataset.name}</div>
              <div className="mt-0.5 text-[11px] text-[#98a2b3]">
                Dataset #{detail?.dataset.id} · {detail?.dataset.status === 'OFFLINE' ? '已下线' : '已上线'}
              </div>
            </div>
            <div className="text-[11px] text-[#667085]">
              {fieldSummary.total} 字段 · {fieldSummary.dimensions} 维度 · {fieldSummary.measures} 指标
            </div>
          </div>
        )}

        <div>
          <div className="mb-2 flex items-center justify-between">
            <div>
              <div className="text-[12px] font-medium text-[#344054]">输出字段</div>
              <div className="mt-0.5 text-[10px] text-[#98a2b3]">自动发现字段类型，可调整显示名称和维度/指标角色</div>
            </div>
            {!previewLoading && fields.length ? (
              <div className="text-[10px] text-[#98a2b3]">
                {fieldSummary.total} 字段 · {fieldSummary.dimensions} 维度 · {fieldSummary.measures} 指标
              </div>
            ) : null}
          </div>

          <div className="max-h-[300px] overflow-auto border border-[#e5e7eb]">
            <div className="sticky top-0 z-10 grid grid-cols-[1.25fr_.7fr_1.25fr_.8fr] gap-2 border-b border-[#e5e7eb] bg-[#fafafa] px-3 py-2 text-[10px] font-medium text-[#667085]">
              <span>物理字段</span>
              <span>类型</span>
              <span>显示名称</span>
              <span>角色</span>
            </div>
            {previewLoading ? (
              <div className="flex h-28 items-center justify-center gap-2 text-[11px] text-[#98a2b3]">
                <Spin size="small" /> 正在发现 SQL 输出字段
              </div>
            ) : previewError ? (
              <div className="px-4 py-8 text-center text-[11px] text-[#b42318]">{previewError}</div>
            ) : fields.length ? fields.map((field) => (
              <div
                key={field.physicalName}
                className="grid grid-cols-[1.25fr_.7fr_1.25fr_.8fr] items-center gap-2 border-b border-[#f0f1f3] px-3 py-2 last:border-b-0"
              >
                <div className="min-w-0 truncate text-[11px] font-medium text-[#344054]" title={field.physicalName}>
                  {field.physicalName}
                </div>
                <div className="text-[10px] text-[#667085]">{fieldTypeLabel[field.dataType]}</div>
                <Input
                  size="small"
                  value={field.displayName}
                  maxLength={200}
                  onChange={(event) => updateField(field.physicalName, { displayName: event.target.value })}
                />
                <Select
                  size="small"
                  value={field.defaultRole}
                  options={[
                    { label: '维度', value: 'DIMENSION' },
                    { label: '指标', value: 'MEASURE' },
                  ]}
                  onChange={(value) => updateField(field.physicalName, { defaultRole: value })}
                />
              </div>
            )) : (
              <div className="px-4 py-8 text-center text-[11px] text-[#98a2b3]">暂无可发布字段</div>
            )}
          </div>
        </div>

        <div className="flex gap-2 bg-[#f8f9fb] px-3 py-2.5 text-[11px] leading-5 text-[#667085]">
          <Info size={14} className="mt-0.5 shrink-0" />
          <div>
            数值字段默认识别为指标，其他字段默认识别为维度。后续 SQL 发布新版本时，只追加 DatasetVersion；同名物理字段会保持稳定 fieldId，避免已有 Analysis 因版本升级失去字段绑定。
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
