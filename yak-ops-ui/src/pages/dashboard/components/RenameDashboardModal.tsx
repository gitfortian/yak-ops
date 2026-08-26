import type { DashboardSummary } from '@/services/dashboard';
import { Input, Modal } from 'antd';

interface RenameDashboardModalProps {
  dashboard?: DashboardSummary;
  value: string;
  loading: boolean;
  onChange: (value: string) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

const RenameDashboardModal = ({
  dashboard,
  value,
  loading,
  onChange,
  onCancel,
  onSubmit,
}: RenameDashboardModalProps) => (
  <Modal
    title="重命名仪表盘"
    open={Boolean(dashboard)}
    okText="保存为草稿"
    cancelText="取消"
    confirmLoading={loading}
    maskClosable={!loading}
    closable={!loading}
    onOk={onSubmit}
    onCancel={onCancel}
  >
    <Input
      autoFocus
      maxLength={128}
      value={value}
      placeholder="请输入仪表盘名称"
      disabled={loading}
      onChange={(event) => onChange(event.target.value)}
      onPressEnter={onSubmit}
    />

    {dashboard?.publishedVersionNo ? (
      <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
        重命名会生成新的草稿版本，不会立即修改当前已发布版本。
      </div>
    ) : null}
  </Modal>
);

export default RenameDashboardModal;
