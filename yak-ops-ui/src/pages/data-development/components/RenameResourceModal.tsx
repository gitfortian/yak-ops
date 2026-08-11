import { Input, Modal, Typography } from 'antd';
import { useEffect, useState } from 'react';

interface RenameResourceModalProps {
  open: boolean;
  resourceLabel: string;
  initialName: string;
  loading?: boolean;
  onCancel: () => void;
  onSubmit: (name: string) => void;
}

const RenameResourceModal = ({
  open,
  resourceLabel,
  initialName,
  loading = false,
  onCancel,
  onSubmit,
}: RenameResourceModalProps) => {
  const [name, setName] = useState(initialName);

  useEffect(() => {
    if (open) setName(initialName);
  }, [initialName, open]);

  const normalizedName = name.trim();
  const submit = () => {
    if (!normalizedName || loading) return;
    onSubmit(normalizedName);
  };

  return (
    <Modal
      open={open}
      title={`重命名${resourceLabel}`}
      width={520}
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
      <div className="grid grid-cols-[72px_minmax(0,1fr)] items-center gap-y-3 pt-2">
        <Typography.Text className="text-[13px] text-[#344054]">
          名称：
        </Typography.Text>
        <Input
          autoFocus
          value={name}
          maxLength={200}
          placeholder="名称"
          disabled={loading}
          onChange={(event) => setName(event.target.value)}
          onPressEnter={submit}
        />
      </div>
    </Modal>
  );
};

export default RenameResourceModal;
