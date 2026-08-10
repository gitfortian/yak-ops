import { Input, Modal, Select, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import type { DevelopmentDirectory, DevelopmentTaskType } from '../types';

interface CreateTaskModalProps {
  open: boolean;
  type: DevelopmentTaskType;
  directories: DevelopmentDirectory[];
  defaultProjectId?: number;
  defaultDirectoryId?: number;
  onCancel: () => void;
  onNext: (
    type: DevelopmentTaskType,
    projectId: number | undefined,
    directoryId: number | undefined,
    name: string,
  ) => void;
}

export default function CreateTaskModal({
  open,
  type: initialType,
  directories,
  defaultProjectId,
  defaultDirectoryId,
  onCancel,
  onNext,
}: CreateTaskModalProps) {
  const [type, setType] = useState<DevelopmentTaskType>(initialType);
  const [projectId, setProjectId] = useState<number>();
  const [directoryId, setDirectoryId] = useState<number>();
  const [name, setName] = useState('');

  const typeOptions = useMemo(
    () => [
      { label: 'SQL', value: 'SQL' },
      { label: 'Shell', value: 'SHELL' },
    ],
    [],
  );

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
    setType(initialType);
    setProjectId(defaultProjectId);
    setDirectoryId(defaultDirectoryId);
    setName('');
  }, [defaultDirectoryId, defaultProjectId, initialType, open]);

  const normalizedName = name.trim();

  const submit = () => {
    if (!normalizedName) return;
    onNext(
      type,
      projectId,
      directoryId && directoryId > 0 ? directoryId : undefined,
      normalizedName,
    );
  };

  return (
    <Modal
      open={open}
      title="新建节点"
      width={600}
      okText="确认"
      cancelText="取消"
      okButtonProps={{ disabled: !normalizedName }}
      destroyOnClose
      onCancel={onCancel}
      onOk={submit}
    >
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-y-3 pt-2">
        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          类型：
        </Typography.Text>
        <Select
          value={type}
          options={typeOptions}
          className="w-full"
          onChange={(value) => setType(value as DevelopmentTaskType)}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          路径：
        </Typography.Text>
        <Select
          value={directoryId ?? 0}
          options={pathOptions}
          showSearch
          optionFilterProp="label"
          className="w-full"
          onChange={(value) => setDirectoryId(Number(value) || undefined)}
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
          onPressEnter={submit}
        />
      </div>
    </Modal>
  );
}
