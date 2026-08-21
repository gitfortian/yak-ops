import { ArrowRightOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Drawer, Form, Input, message, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { realtimeApi } from './api';

interface Values {
  sourceType: string;
  sinkType: string;
  name: string;
  description?: string;
}

export default function CreateRealtimeTaskDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<Values>();
  const [submitting, setSubmitting] = useState(false);
  const automaticName = useRef('');

  useEffect(() => {
    if (!open) return;
    automaticName.current = 'MYSQL → MYSQL 实时同步';
    form.setFieldsValue({ sourceType: 'MYSQL', sinkType: 'MYSQL', name: automaticName.current });
  }, [form, open]);

  const updateName = (source: string, sink: string) => {
    const next = `${source} → ${sink} 实时同步`;
    if (!form.getFieldValue('name') || form.getFieldValue('name') === automaticName.current)
      form.setFieldValue('name', next);
    automaticName.current = next;
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const response = await realtimeApi.createBasic({
        name: values.name.trim(),
        description: values.description?.trim(),
      });
      message.success('基础任务已创建，请继续完成同步配置');
      form.resetFields();
      history.push(`/sync/realtime/${response.data}/detail?scene=create`);
    } catch (error: any) {
      if (!error?.errorFields) message.error(error?.message || '创建实时同步任务失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title="新建实时同步任务"
      width={620}
      open={open}
      closable={false}
      maskClosable={false}
      onClose={onClose}
      extra={
        <div className="flex gap-2">
          <Button disabled={submitting} onClick={onClose}>
            取消
          </Button>
          <Button type="primary" danger loading={submitting} onClick={submit}>
            创建并配置
          </Button>
        </div>
      }
      styles={{ header: { padding: '18px 24px' }, body: { padding: 24 } }}
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <div className="grid grid-cols-[1fr_32px_1fr] items-end gap-3 mb-6">
          <Form.Item name="sourceType" label="来源类型" rules={[{ required: true }]} className="!mb-0">
            <Select
              variant="filled"
              options={[{ value: 'MYSQL', label: 'MYSQL' }]}
              onChange={(v) => updateName(v, form.getFieldValue('sinkType'))}
            />
          </Form.Item>
          <div className="flex h-8 items-center justify-center text-[#98a2b3]">
            <ArrowRightOutlined />
          </div>
          <Form.Item name="sinkType" label="目标类型" rules={[{ required: true }]} className="!mb-0">
            <Select
              variant="filled"
              options={[
                { value: 'MYSQL', label: 'MYSQL' },
                { value: 'POSTGRESQL', label: 'POSTGRESQL' },
              ]}
              onChange={(v) => updateName(form.getFieldValue('sourceType'), v)}
            />
          </Form.Item>
        </div>
        <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }, { max: 200 }]}>
          <Input autoFocus variant="filled" maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label="任务描述（可选）" rules={[{ max: 1000 }]}>
          <Input.TextArea
            variant="filled"
            rows={5}
            maxLength={1000}
            showCount
            placeholder="请说明业务场景、同步范围和使用目的"
          />
        </Form.Item>
        <div className="rounded-lg bg-[#f9fafb] p-4 text-sm text-[#667085]">
          数据源、同步表和运行参数将在任务详情页配置；实时同步无需配置调度。
        </div>
      </Form>
    </Drawer>
  );
}
