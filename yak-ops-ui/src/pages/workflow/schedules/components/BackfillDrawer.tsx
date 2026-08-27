import {
  createWorkflowBackfill,
  previewWorkflowBackfill,
  type WorkflowBackfillPayload,
  type WorkflowBackfillPreview,
  type WorkflowSchedule,
} from '@/services/workflow/schedules';
import { Button, DatePicker, Drawer, Form, Input, Select, Table, message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { DatabaseBackup, Eye } from 'lucide-react';
import { useEffect, useState } from 'react';

interface BackfillFormValues {
  name?: string;
  businessDateRange: [Dayjs, Dayjs];
  executionStrategy: 'SERIAL_WAIT' | 'PARALLEL';
  inputJson: string;
}

interface BackfillDrawerProps {
  open: boolean;
  schedule?: WorkflowSchedule;
  onClose: () => void;
  onCreated: () => Promise<void> | void;
}

const BackfillDrawer = ({ open, schedule, onClose, onCreated }: BackfillDrawerProps) => {
  const [form] = Form.useForm<BackfillFormValues>();
  const [preview, setPreview] = useState<WorkflowBackfillPreview>();
  const [previewing, setPreviewing] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!open) return;
    setPreview(undefined);
    form.setFieldsValue({
      name: '',
      executionStrategy: 'SERIAL_WAIT',
      inputJson: '{}',
      businessDateRange: [dayjs().subtract(1, 'day'), dayjs().subtract(1, 'day')],
    });
  }, [form, open, schedule?.id]);

  const buildPayload = async (): Promise<WorkflowBackfillPayload | undefined> => {
    if (!schedule) return undefined;
    const values = await form.validateFields();
    let input: Record<string, unknown> = {};
    try {
      input = values.inputJson?.trim() ? JSON.parse(values.inputJson) : {};
    } catch {
      message.error('补数参数必须是合法 JSON');
      return undefined;
    }
    return {
      scheduleId: schedule.id,
      name: values.name?.trim() || undefined,
      startBusinessDate: values.businessDateRange[0].format('YYYY-MM-DD'),
      endBusinessDate: values.businessDateRange[1].format('YYYY-MM-DD'),
      executionStrategy: values.executionStrategy,
      input,
    };
  };

  const handlePreview = async () => {
    try {
      const payload = await buildPayload();
      if (!payload) return;
      setPreviewing(true);
      const data = await previewWorkflowBackfill(payload);
      setPreview(data);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error instanceof Error ? error.message : '补数计划预览失败');
    } finally {
      setPreviewing(false);
    }
  };

  const handleCreate = async () => {
    try {
      const payload = await buildPayload();
      if (!payload) return;
      setCreating(true);
      const result = await createWorkflowBackfill(payload);
      message.success(`补数批次已创建，共 ${result?.totalCount ?? preview?.totalCount ?? 0} 个逻辑实例`);
      await onCreated();
      onClose();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error instanceof Error ? error.message : '创建补数批次失败');
    } finally {
      setCreating(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={680}
      destroyOnClose
      title={
        <div>
          <div className="text-[14px] font-semibold text-[#344054]">历史补数 / Backfill</div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            {schedule?.name || '-'} · {schedule?.cronExpression || '-'} · {schedule?.timezone || '-'}
          </div>
        </div>
      }
      onClose={onClose}
      extra={
        <div className="flex gap-2">
          <Button icon={<Eye size={14} />} loading={previewing} onClick={() => void handlePreview()}>
            预览计划
          </Button>
          <Button type="primary" icon={<DatabaseBackup size={14} />} loading={creating} onClick={() => void handleCreate()}>
            创建补数
          </Button>
        </div>
      }
    >
      <div className="mb-4 rounded-md border border-[#eaecf0] bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
        按调度 Cron 与时区重新生成历史逻辑计划。每个实例自动注入 <code>businessDate</code>、
        <code> scheduleTime</code>、<code>scheduleTimezone</code>、<code>triggerType</code>、
        <code>backfillId</code>，完整系统参数同时位于 <code>__schedule</code> 命名空间。
      </div>

      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item name="name" label="补数批次名称" rules={[{ max: 120 }]}>
          <Input variant="filled" placeholder="不填则自动使用 调度名称 + 日期区间" />
        </Form.Item>

        <Form.Item
          name="businessDateRange"
          label="业务日期范围"
          rules={[{ required: true, message: '请选择需要补数的业务日期范围' }]}
          extra="业务日期按调度时区计算；计划时间不是当前点击创建的时间。"
        >
          <DatePicker.RangePicker className="w-full" allowClear={false} />
        </Form.Item>

        <Form.Item
          name="executionStrategy"
          label="补数执行策略"
          rules={[{ required: true }]}
        >
          <Select
            options={[
              { value: 'SERIAL_WAIT', label: '串行补数（推荐，按逻辑计划时间依次执行）' },
              { value: 'PARALLEL', label: '并行补数（允许多个历史实例同时运行）' },
            ]}
          />
        </Form.Item>

        <Form.Item
          name="inputJson"
          label="补数参数 JSON"
          extra="覆盖调度定义中的同名自定义参数；businessDate / scheduleTime 等系统保留参数始终由平台生成。"
        >
          <Input.TextArea rows={6} spellCheck={false} className="font-mono text-[12px]" />
        </Form.Item>
      </Form>

      {preview ? (
        <div className="mt-2">
          <div className="mb-2 flex items-center justify-between">
            <div className="text-[12px] font-medium text-[#344054]">计划预览</div>
            <div className="text-[11px] text-[#667085]">
              共 <b>{preview.totalCount}</b> 个实例{preview.truncated ? '，下表仅展示前 100 条' : ''}
            </div>
          </div>
          <Table
            rowKey={(record) => `${record.businessDate}-${record.scheduleInstant}`}
            size="small"
            bordered
            pagination={false}
            scroll={{ y: 280 }}
            dataSource={preview.occurrences}
            columns={[
              {
                title: 'businessDate',
                dataIndex: 'businessDate',
                width: 130,
                render: (value: string) => <code className="text-[12px] text-[#344054]">{value}</code>,
              },
              {
                title: 'scheduleTime',
                dataIndex: 'scheduleTime',
                render: (value: string) => <code className="text-[11px] text-[#667085]">{value}</code>,
              },
            ]}
            className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[11px]"
          />
        </div>
      ) : null}
    </Drawer>
  );
};

export default BackfillDrawer;
