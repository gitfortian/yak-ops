import { Modal } from 'antd';

import type { DevelopmentTreeNode } from '../types';

interface DeleteDevelopmentResourceModalProps {
  target?: DevelopmentTreeNode;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

const DeleteDevelopmentResourceModal = ({
  target,
  loading,
  onCancel,
  onConfirm,
}: DeleteDevelopmentResourceModalProps) => (
  <Modal
    open={Boolean(target)}
    title={`删除${target?.nodeType === 'directory' ? '目录' : '节点'}`}
    okText="删除"
    cancelText="取消"
    okButtonProps={{ danger: true }}
    confirmLoading={loading}
    maskClosable={!loading}
    closable={!loading}
    onCancel={onCancel}
    onOk={onConfirm}
  >
    <div className="pt-2 text-[13px] leading-6 text-[#475467]">
      确认删除“{target?.title}”吗？
      {target?.nodeType === 'directory' ? (
        <div className="mt-1 text-[#98a2b3]">
          仅空目录可以删除；存在子目录或节点时后端会拒绝本次操作。
        </div>
      ) : null}
    </div>
  </Modal>
);

export default DeleteDevelopmentResourceModal;
