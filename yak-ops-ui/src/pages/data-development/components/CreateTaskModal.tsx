import { Input, Modal, Select, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
} from '../types';

interface CreateTaskModalProps {
  open: boolean;
  type: DevelopmentNodeType;
  directories: DevelopmentDirectory[];
  defaultProjectId?: DevelopmentId;
  defaultDirectoryId?: DevelopmentId;
  loading?: boolean;
  onCancel: () => void;
  onNext: (
    type: DevelopmentNodeType,
    projectId: DevelopmentId | undefined,
    directoryId: DevelopmentId | undefined,
    name: string,
  ) => void;
}

const ROOT_VALUE = '__root__';

export default function CreateTaskModal({
  open,
  type: initialType,
  directories,
  defaultProjectId,
  defaultDirectoryId,
  loading = false,
  onCancel,
  onNext,
}: CreateTaskModalProps) {
  const [type, setType] = useState<DevelopmentNodeType>(initialType);
  const [projectId, setProjectId] = useState<DevelopmentId>();
  const [directoryId, setDirectoryId] = useState<DevelopmentId>();
  const [name, setName] = useState('');

  const typeOptions = useMemo(
    () => [
      { label: 'SQL', value: 'SQL' },
      { label: 'Shell', value: 'SHELL' },
      { label: '数据集', value: 'DATASET' },
      { label: '数据服务', value: 'DATA_SERVICE' },
    ],
    [],
  );

  const pathOptions = useMemo(
    () => [
      { label: '/', value: ROOT_VALUE },
      ...directories.map((directory) => ({
        label: directory.path,
        value: directory.id,
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
    if (!normalizedName || loading) return;
    onNext(type, projectId, directoryId, normalizedName);
  };

  return (
    <Modal
      open={open}
      title="新建节点"
      width={600}
      okText="确认"
      cancelText="取消"
      confirmLoading={loading}
      okButtonProps={{ disabled: !normalizedName }}
      destroyOnClose
      maskClosable={!loading}
      closable={!loading}
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
          disabled={loading}
          onChange={(value) => setType(value as DevelopmentNodeType)}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          路径：
        </Typography.Text>
        <Select
          value={directoryId ?? ROOT_VALUE}
          options={pathOptions}
          showSearch
          optionFilterProp="label"
          className="w-full"
          disabled={loading}
          onChange={(value) => {
            const selected = String(value);
            setDirectoryId(selected === ROOT_VALUE ? undefined : selected);
          }}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          名称：
        </Typography.Text>
        <Input
          autoFocus
          value={name}
          maxLength={128}
          disabled={loading}
          placeholder="名称"
          onChange={(event) => setName(event.target.value)}
          onPressEnter={submit}
        />
      </div>
    </Modal>
  );
}
