import EmojiIconPicker, {
  DEFAULT_EMOJI_ICON,
  type EmojiIconValue,
} from '@/components/EmojiIconPicker';
import { YakButton } from '@/components/ui';
import { Drawer, Form, Input } from 'antd';
import { GitBranch } from 'lucide-react';
import { useEffect, useState } from 'react';

export interface WorkflowCreateValues {
  name: string;
  description?: string;
  icon: EmojiIconValue;
}

interface WorkflowCreateDrawerProps {
  open: boolean;
  creating: boolean;
  onClose: () => void;
  onSubmit: (values: WorkflowCreateValues) => Promise<void>;
}

const WorkflowCreateDrawer = ({
  open,
  creating,
  onClose,
  onSubmit,
}: WorkflowCreateDrawerProps) => {
  const [form] = Form.useForm<Omit<WorkflowCreateValues, 'icon'>>();
  const [icon, setIcon] = useState<EmojiIconValue>(DEFAULT_EMOJI_ICON);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
  }, [form, open]);

  const handleClose = () => {
    if (creating) return;
    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
    onClose();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await onSubmit({ ...values, icon });
      form.resetFields();
      setIcon(DEFAULT_EMOJI_ICON);
    } catch {
      // Form owns validation feedback; request failures are surfaced by the page action.
    }
  };

  return (
    <Drawer
      open={open}
      width={560}
      placement="right"
      closable={false}
      destroyOnClose
      maskClosable={!creating}
      keyboard={!creating}
      onClose={handleClose}
      title={
        <div>
          <div className="text-[18px] font-semibold leading-7 text-[#101828]">
            新建工作流
          </div>
          <div className="mt-1 text-[11px] font-normal text-[rgba(22,24,35,.42)]">
            第一步创建基础信息，下一步进入画布完成任务编排
          </div>
        </div>
      }
      extra={
        <div className="flex items-center gap-2">
          <YakButton
            disabled={creating}
            onClick={handleClose}
            className="!h-9 !rounded-lg !px-4"
          >
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={creating}
            onClick={() => void handleSubmit()}
            className="!h-9 !rounded-lg !px-5 !text-white"
          >
            创建并配置
          </YakButton>
        </div>
      }
      styles={{
        header: {
          padding: '18px 24px',
          borderBottom: '1px solid #eaecf0',
        },
        body: { padding: 24 },
      }}
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item label="工作流名称" required className="!mb-6">
          <div className="flex items-start gap-2.5">
            <EmojiIconPicker
              value={icon}
              disabled={creating}
              onChange={setIcon}
              className="mt-px"
            />
            <Form.Item
              name="name"
              noStyle
              rules={[
                { required: true, message: '请输入工作流名称' },
                { max: 100, message: '名称不能超过 100 个字符' },
              ]}
            >
              <Input
                variant="filled"
                placeholder="例如：每日订单同步工作流"
                className="!h-[44px] !rounded-[10px]"
              />
            </Form.Item>
          </div>
        </Form.Item>

        <Form.Item
          name="description"
          label="工作流描述"
          rules={[{ max: 500, message: '描述不能超过 500 个字符' }]}
        >
          <Input.TextArea
            variant="filled"
            rows={4}
            placeholder="简单说明这个工作流负责什么"
          />
        </Form.Item>

        <div className="mt-2 rounded-[9px] border border-[rgba(22,24,35,.055)] bg-[#f8f9fa] p-4">
          <div className="flex items-center gap-2 text-[12px] font-semibold text-[#161823]">
            <GitBranch
              size={15}
              strokeWidth={1.9}
              className="text-[#667085]"
            />
            创建后进入工作流配置
          </div>
          <div className="mt-2 text-[11px] leading-5 text-[rgba(22,24,35,.48)]">
            在下一步从左侧拖入已经配置好的任务，完成 DAG
            连线，并设置节点重试、超时、触发规则和失败策略。
          </div>
        </div>
      </Form>
    </Drawer>
  );
};

export default WorkflowCreateDrawer;
