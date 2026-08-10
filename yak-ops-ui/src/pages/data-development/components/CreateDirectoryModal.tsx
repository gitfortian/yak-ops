import { Input, Modal, Select, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import type { DevelopmentDirectory } from '../types';

interface CreateDirectoryModalProps {
  open: boolean;
  projectName?: string;
  directories: DevelopmentDirectory[];
  defaultParentId?: number;
  loading?: boolean;
  onCancel: () => void;
  onSubmit: (parentId: number | undefined, name: string) => void;
}

const CreateDirectoryModal = ({
  open,
  projectName,
  directories,
  defaultParentId,
  loading = false,
  onCancel,
  onSubmit,
}: CreateDirectoryModalProps) => {
  const [parentId, setParentId] = useState<number>();
  const [name, setName] = useState('');

  const pathOptions = useMemo(
    () => [
      { label: '/', value: 0 },
      ...directories.map((directory) => ({
        label: directory.path,
        value: Number(directory.id),
      })),
    ],
    [directories],
  );

  useEffect(() => {
    if (!open) return;
    setParentId(defaultParentId);
    setName('');
  }, [defaultParentId, open]);

  const normalizedName = name.trim();

  return (
    <Modal
      open={open}
      title="新建目录"
      width={600}
      okText="确认"
      cancelText="取消"
      confirmLoading={loading}
      okButtonProps={{ disabled: !normalizedName }}
      destroyOnClose
      maskClosable={!loading}
      closable={!loading}
      onCancel={onCancel}
      onOk={() => {
        if (!normalizedName) return;
        onSubmit(parentId && parentId > 0 ? parentId : undefined, normalizedName);
      }}
    >
      <div className="pt-2">
        {projectName ? (
          <div className="mb-4 rounded-lg bg-[#fafafa] px-3 py-2 text-[12px] text-[rgba(22,24,35,.55)]">
            当前项目：
            <span className="font-medium text-[#344054]">{projectName}</span>
          </div>
        ) : null}

        <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-y-3">
          <Typography.Text className="text-[13px] text-[#344054]">
            <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
            路径：
          </Typography.Text>
          <Select
            value={parentId ?? 0}
            options={pathOptions}
            showSearch
            optionFilterProp="label"
            className="w-full"
            onChange={(value) => setParentId(Number(value) || undefined)}
          />

          <Typography.Text className="text-[13px] text-[#344054]">
            <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
            名称：
          </Typography.Text>
          <Input
            autoFocus
            value={name}
            maxLength={128}
            placeholder="名称"
            onChange={(event) => setName(event.target.value)}
            onPressEnter={() => {
              if (normalizedName && !loading) {
                onSubmit(
                  parentId && parentId > 0 ? parentId : undefined,
                  normalizedName,
                );
              }
            }}
          />
        </div>
      </div>
    </Modal>
  );
};

export default CreateDirectoryModal;
