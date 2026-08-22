import { CodeOutlined, CompassOutlined } from '@ant-design/icons';
import { history } from '@umijs/max';
import { Button, Drawer, Form, Input, message, Select, Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { realtimeApi } from './api';
import type { ComputeEnvironmentOption } from './types';

type EditorMode = 'wizard' | 'yaml';

interface Values {
  name: string;
  description?: string;
  runtimeEnvironmentId: number;
}

const preferredEnvironmentId = (environments: ComputeEnvironmentOption[]) =>
  environments.find((item) => item.defaultEnvironment && item.enabled)?.id ??
  environments.find((item) => item.enabled)?.id;

const editorModes: Array<{
  value: EditorMode;
  title: string;
  description: string;
  badge?: string;
  icon: React.ReactNode;
}> = [
  {
    value: 'wizard',
    title: '向导模式',
    description: '通过选择数据源、数据表和同步方式快速创建任务，适合大多数同步场景。',
    badge: '推荐',
    icon: <CompassOutlined />,
  },
  {
    value: 'yaml',
    title: 'YAML 模式',
    description: '使用 YAML 描述同步任务，为高级配置和复杂同步规则保留完整扩展能力。',
    icon: <CodeOutlined />,
  },
];

export default function CreateRealtimeTaskDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<Values>();
  const [editorMode, setEditorMode] = useState<EditorMode>('wizard');
  const [submitting, setSubmitting] = useState(false);
  const [environmentLoading, setEnvironmentLoading] = useState(false);
  const [environments, setEnvironments] = useState<ComputeEnvironmentOption[]>([]);

  const environmentOptions = useMemo(
    () =>
      environments.map((environment) => ({
        value: environment.id,
        disabled: !environment.enabled,
        label: `${environment.name}${environment.defaultEnvironment ? ' · 默认' : ''} · Flink ${environment.config.flinkVersion} / CDC ${environment.config.flinkCdcVersion}${environment.enabled ? '' : ' · 已停用'}`,
      })),
    [environments],
  );

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setEditorMode('wizard');

    let cancelled = false;
    const loadEnvironments = async () => {
      setEnvironmentLoading(true);
      try {
        const response = await realtimeApi.environments();
        if (cancelled) return;
        const rows = response.data || [];
        setEnvironments(rows);
        const defaultId = preferredEnvironmentId(rows);
        if (defaultId) form.setFieldValue('runtimeEnvironmentId', defaultId);
      } catch (error: any) {
        if (!cancelled) {
          setEnvironments([]);
          message.error(error?.message || '运行环境加载失败');
        }
      } finally {
        if (!cancelled) setEnvironmentLoading(false);
      }
    };
    void loadEnvironments();
    return () => {
      cancelled = true;
    };
  }, [form, open]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const environment = environments.find(
        (item) => item.id === Number(values.runtimeEnvironmentId),
      );
      if (!environment || !environment.enabled) {
        message.error('请选择已启用的运行环境');
        return;
      }
      setSubmitting(true);
      const response = await realtimeApi.createBasic({
        name: values.name.trim(),
        description: values.description?.trim(),
        runtimeEnvironmentId: Number(values.runtimeEnvironmentId),
      });
      message.success('基础任务已创建，请继续完成同步配置');
      form.resetFields();
      history.push(
        `/sync/realtime/${response.data}/detail?scene=create&editor=${editorMode}`,
      );
    } catch (error: any) {
      if (!error?.errorFields) message.error(error?.message || '创建实时同步任务失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title="新建实时同步任务"
      width={680}
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
        <div className="mb-2 text-[14px] font-medium text-[#344054]">创建方式</div>
        <div className="mb-6 grid grid-cols-2 gap-3 max-sm:grid-cols-1">
          {editorModes.map((mode) => {
            const selected = editorMode === mode.value;
            return (
              <button
                key={mode.value}
                type="button"
                className={[
                  'relative min-h-[142px] rounded-xl border bg-white p-5 text-left transition-all',
                  selected
                    ? 'border-[#ff4d4f] shadow-[0_0_0_2px_rgba(255,77,79,0.08)]'
                    : 'border-[#e4e7ec] hover:border-[#fda29b] hover:bg-[#fffbfa]',
                ].join(' ')}
                onClick={() => setEditorMode(mode.value)}
              >
                <div className="flex items-start justify-between gap-3">
                  <span
                    className={[
                      'flex h-9 w-9 items-center justify-center rounded-lg text-[18px]',
                      selected
                        ? 'bg-[#fff1f0] text-[#ff4d4f]'
                        : 'bg-[#f2f4f7] text-[#667085]',
                    ].join(' ')}
                  >
                    {mode.icon}
                  </span>
                  {mode.badge && (
                    <span className="rounded-full bg-[#fff1f0] px-2 py-0.5 text-[11px] font-medium text-[#ff4d4f]">
                      {mode.badge}
                    </span>
                  )}
                </div>
                <div className="mt-4 text-[15px] font-semibold text-[#101828]">{mode.title}</div>
                <div className="mt-1.5 text-[12px] leading-5 text-[#667085]">{mode.description}</div>
              </button>
            );
          })}
        </div>

        <Form.Item
          name="runtimeEnvironmentId"
          label="运行环境"
          rules={[{ required: true, message: '请选择运行环境' }]}
          extra="任务会绑定该环境；每次启动都会保存环境快照，后续切换默认环境不会影响已运行任务。"
        >
          <Select
            showSearch
            optionFilterProp="label"
            variant="filled"
            loading={environmentLoading}
            disabled={environmentLoading}
            placeholder="请选择 Flink CDC 运行环境"
            options={environmentOptions}
            notFoundContent={
              environmentLoading ? <Spin size="small" /> : '请先到 设置 → 计算引擎 创建运行环境'
            }
          />
        </Form.Item>

        <Form.Item
          name="name"
          label="任务名称"
          rules={[
            { required: true, message: '请输入任务名称' },
            { max: 200 },
          ]}
        >
          <Input autoFocus variant="filled" maxLength={200} showCount placeholder="例如：订单实时同步" />
        </Form.Item>
        <Form.Item name="description" label="任务描述（可选）" rules={[{ max: 1000 }]}>
          <Input.TextArea
            variant="filled"
            rows={4}
            maxLength={1000}
            showCount
            placeholder="请说明业务场景、同步范围和使用目的"
          />
        </Form.Item>

        <div className="rounded-lg bg-[#f9fafb] p-4 text-sm leading-6 text-[#667085]">
          {editorMode === 'wizard'
            ? '创建后进入向导配置页。数据源、同步表和同步方式将在后续步骤中完成配置。'
            : '创建后进入 YAML 配置页。YAML 与任务 Spec 的互转能力将在后续流程中接入。'}
        </div>
      </Form>
    </Drawer>
  );
}
