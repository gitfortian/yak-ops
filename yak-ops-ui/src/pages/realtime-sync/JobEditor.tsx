import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Button,
  Col,
  ConfigProvider,
  Form,
  Input,
  InputNumber,
  message,
  Radio,
  Row,
  Select,
  Space,
  Switch,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { BRAND_THEME } from '@/styles/brand';
import { realtimeApi } from './api';
import type {
  CdcPipelineSpec,
  ComputeEnvironmentOption,
  DataSourceOption,
  RealtimeJob,
} from './types';

interface RouteFormValue {
  sourceTable: string;
  sinkTable: string;
  matchMode: 'EXACT' | 'REGEX';
  keyColumnsText: string;
}
interface FormValue extends Omit<CdcPipelineSpec, 'tables'> {
  name: string;
  description?: string;
  runtimeEnvironmentId: number;
  tables: RouteFormValue[];
}

const defaults: FormValue = {
  name: '',
  description: '',
  runtimeEnvironmentId: undefined as unknown as number,
  sourceDataSourceRef: undefined as unknown as number,
  sinkDataSourceRef: undefined as unknown as number,
  tables: [{ sourceTable: '', sinkTable: '', matchMode: 'EXACT', keyColumnsText: 'id' }],
  startupMode: 'initial',
  schemaEvolution: 'EVOLVE',
  parallelism: 1,
  checkpointIntervalMs: 60_000,
  restart: { strategy: 'fixed-delay', attempts: 3, delayMs: 10_000 },
  sink: {
    maxRetries: 3,
    batchSize: 1_000,
    flushIntervalMs: 2_000,
    maxBatchBytes: 16_777_216,
    statementCacheSize: 128,
    strictReplaySafety: true,
  },
};

const sections = [
  { key: 'task-basic', label: '任务基础信息' },
  { key: 'source-sink', label: 'Source / Sink 配置' },
  { key: 'table-rules', label: '表规则' },
  { key: 'runtime-params', label: '运行参数' },
] as const;
type SectionKey = (typeof sections)[number]['key'];

const SECTION_SCROLL_OFFSET = 24;
const ACTIVE_SECTION_OFFSET = SECTION_SCROLL_OFFSET + 8;

const defaultEnvironmentId = (environments: ComputeEnvironmentOption[]) =>
  environments.find((item) => item.defaultEnvironment && item.enabled)?.id ??
  environments.find((item) => item.enabled)?.id;

const toFormValue = (
  job: RealtimeJob | undefined,
  environments: ComputeEnvironmentOption[],
): FormValue =>
  job?.spec
    ? {
        name: job.name,
        description: job.description,
        runtimeEnvironmentId:
          job.runtimeEnvironmentId ?? (defaultEnvironmentId(environments) as number),
        ...job.spec,
        tables: job.spec.tables.map((route) => ({
          ...route,
          keyColumnsText: route.keyColumns.join(','),
        })),
      }
    : {
        ...defaults,
        name: job?.name || '',
        description: job?.description || '',
        runtimeEnvironmentId:
          job?.runtimeEnvironmentId ?? (defaultEnvironmentId(environments) as number),
      };

const Card = ({ id, title, children }: { id: SectionKey; title: string; children: React.ReactNode }) => (
  <section id={id} className="scroll-mt-6 rounded-xl bg-white px-7 py-6">
    <h2 className="mb-6 mt-0 text-[17px] font-semibold text-[#101828]">{title}</h2>
    {children}
  </section>
);

export default function JobEditor({
  open,
  job,
  dataSources,
  environments,
  onClose,
  onSaved,
}: {
  open: boolean;
  job?: RealtimeJob;
  dataSources: DataSourceOption[];
  environments: ComputeEnvironmentOption[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<FormValue>();
  const [saving, setSaving] = useState(false);
  const [active, setActive] = useState<SectionKey>('task-basic');
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const scrollingToRef = useRef<SectionKey | null>(null);
  const sourceOptions = useMemo(
    () => dataSources.filter((item) => item.dbType === 'MYSQL'),
    [dataSources],
  );
  const sinkOptions = useMemo(
    () => dataSources.filter((item) => ['MYSQL', 'POSTGRE_SQL', 'POSTGRESQL'].includes(item.dbType)),
    [dataSources],
  );
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
    if (open) form.setFieldsValue(toFormValue(job, environments));
  }, [environments, form, job, open]);
  useEffect(() => {
    if (!open) return;
    const root = scrollContainerRef.current;
    if (!root) return;

    let frame = 0;
    let releaseTimer: number | undefined;

    const updateActiveFromScroll = () => {
      if (scrollingToRef.current) return;

      if (root.scrollTop + root.clientHeight >= root.scrollHeight - 1) {
        const lastSection = sections[sections.length - 1].key;
        setActive((current) => (current === lastSection ? current : lastSection));
        return;
      }

      const rootTop = root.getBoundingClientRect().top;
      let next: SectionKey = sections[0].key;
      for (const { key } of sections) {
        const node = root.querySelector<HTMLElement>(`#${key}`);
        if (!node) continue;
        if (node.getBoundingClientRect().top - rootTop <= ACTIVE_SECTION_OFFSET) next = key;
        else break;
      }
      setActive((current) => (current === next ? current : next));
    };

    const handleScroll = () => {
      if (scrollingToRef.current) {
        if (releaseTimer !== undefined) window.clearTimeout(releaseTimer);
        releaseTimer = window.setTimeout(() => {
          scrollingToRef.current = null;
          updateActiveFromScroll();
        }, 120);
        return;
      }

      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(updateActiveFromScroll);
    };

    updateActiveFromScroll();
    root.addEventListener('scroll', handleScroll, { passive: true });
    return () => {
      root.removeEventListener('scroll', handleScroll);
      window.cancelAnimationFrame(frame);
      if (releaseTimer !== undefined) window.clearTimeout(releaseTimer);
    };
  }, [open]);

  const save = async () => {
    try {
      const values = await form.validateFields();
      const spec: CdcPipelineSpec = {
        sourceDataSourceRef: Number(values.sourceDataSourceRef),
        sinkDataSourceRef: Number(values.sinkDataSourceRef),
        tables: values.tables.map((route) => ({
          sourceTable: route.sourceTable.trim(),
          sinkTable: route.sinkTable.trim(),
          matchMode: route.matchMode,
          keyColumns: route.keyColumnsText
            .split(',')
            .map((item) => item.trim())
            .filter(Boolean),
        })),
        startupMode: values.startupMode,
        schemaEvolution: values.schemaEvolution,
        parallelism: values.parallelism,
        checkpointIntervalMs: values.checkpointIntervalMs,
        restart: values.restart,
        sink: { ...values.sink, strictReplaySafety: true },
      };
      setSaving(true);
      const payload = {
        name: values.name.trim(),
        description: values.description?.trim(),
        runtimeEnvironmentId: Number(values.runtimeEnvironmentId),
        spec,
      };
      if (job) await realtimeApi.update(job.id, payload);
      else await realtimeApi.create(payload);
      message.success('实时同步草稿已保存');
      onSaved();
    } catch (error: any) {
      if (!error?.errorFields) message.error(error?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (!open) return null;
  const option = (item: DataSourceOption) => ({
    label: `${item.label} (${item.dbType})`,
    value: Number(item.value),
  });
  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div ref={scrollContainerRef} className="h-[calc(100vh-64px)] overflow-y-auto bg-[#f7f8fa] text-[#161823]">
        <Form form={form} layout="vertical" initialValues={defaults}>
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-[minmax(0,1fr)_160px] gap-6 px-6 pb-6 pt-6 max-xl:grid-cols-1">
            <main className="min-w-0 space-y-5">
              <Card id="task-basic" title="任务基础信息">
                <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
                  <Input maxLength={200} showCount />
                </Form.Item>
                <Form.Item name="description" label="任务描述">
                  <Input.TextArea maxLength={1000} showCount rows={4} />
                </Form.Item>
              </Card>

              <Card id="source-sink" title="Source / Sink 配置">
                <Row gutter={20}>
                  <Col xs={24} lg={12}>
                    <div className="rounded-xl border border-[#e4e7ec] bg-[#fcfcfd] p-5">
                      <h3 className="mb-5 mt-0 text-[14px] font-semibold">Source 来源配置</h3>
                      <Form.Item
                        name="sourceDataSourceRef"
                        label="MySQL Source"
                        rules={[{ required: true, message: '请选择 Source 数据源' }]}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="请选择来源数据源"
                          options={sourceOptions.map(option)}
                        />
                      </Form.Item>
                    </div>
                  </Col>
                  <Col xs={24} lg={12}>
                    <div className="rounded-xl border border-[#e4e7ec] bg-[#fcfcfd] p-5">
                      <h3 className="mb-5 mt-0 text-[14px] font-semibold">Sink 目标配置</h3>
                      <Form.Item
                        name="sinkDataSourceRef"
                        label="MySQL / PostgreSQL Sink"
                        rules={[{ required: true, message: '请选择 Sink 数据源' }]}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="请选择目标数据源"
                          options={sinkOptions.map(option)}
                        />
                      </Form.Item>
                    </div>
                  </Col>
                </Row>
                <div className="mt-4 rounded-lg border border-[#ffccc7] bg-[#fff2f0] px-4 py-3 text-[12px] text-[#475467]">
                  任务定义只保存数据源引用；连接信息会在发布和启动时重新解析，密码不会进入任务定义、部署快照或接口响应。
                </div>
              </Card>

              <Card id="table-rules" title="表规则">
                <Form.List name="tables">
                  {(fields, { add, remove }) => (
                    <Space direction="vertical" className="w-full" size={12}>
                      {fields.map((field, index) => (
                        <div key={field.key} className="rounded-xl border border-[#e4e7ec] bg-[#fcfcfd] p-5">
                          <div className="mb-3 flex items-center justify-between">
                            <strong>表规则 {index + 1}</strong>
                            {fields.length > 1 && (
                              <Button type="text" danger icon={<DeleteOutlined />} onClick={() => remove(field.name)}>
                                删除
                              </Button>
                            )}
                          </div>
                          <Row gutter={12}>
                            <Col xs={24} lg={7}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'sourceTable']}
                                label="Source 表 / 正则"
                                rules={[{ required: true }]}
                              >
                                <Input placeholder="orders" />
                              </Form.Item>
                            </Col>
                            <Col xs={24} lg={6}>
                              <Form.Item {...field} name={[field.name, 'matchMode']} label="匹配方式">
                                <Radio.Group
                                  options={[
                                    { label: '精确', value: 'EXACT' },
                                    { label: '正则', value: 'REGEX' },
                                  ]}
                                />
                              </Form.Item>
                            </Col>
                            <Col xs={24} lg={6}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'sinkTable']}
                                label="Sink 表"
                                rules={[{ required: true }]}
                              >
                                <Input placeholder="public.orders" />
                              </Form.Item>
                            </Col>
                            <Col xs={24} lg={5}>
                              <Form.Item
                                {...field}
                                name={[field.name, 'keyColumnsText']}
                                label="主键字段"
                                rules={[{ required: true }]}
                              >
                                <Input placeholder="id,tenant_id" />
                              </Form.Item>
                            </Col>
                          </Row>
                        </div>
                      ))}
                      <Button
                        icon={<PlusOutlined />}
                        onClick={() =>
                          add({ sourceTable: '', sinkTable: '', matchMode: 'EXACT', keyColumnsText: 'id' })
                        }
                      >
                        添加表规则
                      </Button>
                    </Space>
                  )}
                </Form.List>
              </Card>

              <Card id="runtime-params" title="运行参数">
                <div className="mb-5 rounded-xl border border-[#e4e7ec] bg-[#fcfcfd] p-5">
                  <Form.Item
                    name="runtimeEnvironmentId"
                    label="运行环境"
                    rules={[
                      { required: true, message: '请选择运行环境' },
                      {
                        validator: async (_, value) => {
                          const environment = environments.find((item) => item.id === Number(value));
                          if (!environment) throw new Error('运行环境不存在，请刷新后重试');
                          if (!environment.enabled) throw new Error('当前运行环境已停用，请切换运行环境');
                        },
                      },
                    ]}
                    className="!mb-2"
                  >
                    <Select
                      showSearch
                      optionFilterProp="label"
                      placeholder="请选择 Flink CDC 运行环境"
                      options={environmentOptions}
                      notFoundContent="请先到 设置 → 计算引擎 创建运行环境"
                    />
                  </Form.Item>
                  <div className="text-[12px] leading-5 text-[#667085]">
                    任务会显式绑定运行环境；每次启动都会固化一份环境快照。之后修改默认环境或环境配置，不会把已经运行的 Flink Job 重定向到其他集群。
                  </div>
                </div>

                <Row gutter={16}>
                  <Col xs={24} md={8}>
                    <Form.Item name="startupMode" label="启动模式">
                      <Select
                        options={[
                          { value: 'initial', label: '全量 + 增量' },
                          { value: 'latest-offset', label: '仅最新增量' },
                        ]}
                      />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name="parallelism" label="并行度">
                      <InputNumber min={1} max={256} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name="schemaEvolution" label="Schema Evolution">
                      <Select options={['EVOLVE', 'IGNORE', 'FAIL'].map((value) => ({ value, label: value }))} />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name={['sink', 'batchSize']} label="Sink Batch Size">
                      <InputNumber min={1} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name={['sink', 'flushIntervalMs']} label="Flush 间隔 (ms)">
                      <InputNumber min={1} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name={['sink', 'maxRetries']} label="Sink 重试次数">
                      <InputNumber min={0} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name={['sink', 'maxBatchBytes']} label="最大批次字节">
                      <InputNumber min={1} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item name={['sink', 'statementCacheSize']} label="Statement Cache">
                      <InputNumber min={1} className="w-full" />
                    </Form.Item>
                  </Col>
                  <Col xs={24} md={8}>
                    <Form.Item
                      name={['sink', 'strictReplaySafety']}
                      label="Strict Replay Safety"
                      valuePropName="checked"
                    >
                      <Switch disabled />
                    </Form.Item>
                  </Col>
                </Row>
                <Form.Item name="checkpointIntervalMs" hidden>
                  <InputNumber />
                </Form.Item>
                <Form.Item name={['restart', 'strategy']} hidden>
                  <Input />
                </Form.Item>
                <Form.Item name={['restart', 'attempts']} hidden>
                  <InputNumber />
                </Form.Item>
                <Form.Item name={['restart', 'delayMs']} hidden>
                  <InputNumber />
                </Form.Item>
              </Card>

              <footer className="sticky bottom-0 z-20 flex min-h-[80px] items-center gap-3 rounded-t-lg border border-b-0 border-[#eaecf0] bg-white px-8 py-4 shadow-[0_-8px_16px_rgba(0,0,0,0.06)]">
                <Button type="primary" loading={saving} className="!h-9 !min-w-[120px] !rounded-lg" onClick={save}>
                  保存配置
                </Button>
                <Button disabled={saving} className="!h-9 !min-w-[120px] !border-0 !bg-[#f2f3f5]" onClick={onClose}>
                  取消
                </Button>
              </footer>
            </main>
            <aside className="max-xl:hidden">
              <nav className="sticky top-6 rounded-xl bg-white px-3 py-4">
                <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">快速定位</div>
                <div className="relative space-y-1 before:absolute before:bottom-4 before:left-[13px] before:top-4 before:w-px before:bg-[#e4e7ec]">
                  {sections.map((item) => (
                    <button
                      key={item.key}
                      type="button"
                      className={`relative flex w-full items-center gap-3 rounded-lg border-0 px-2 py-2 text-left text-[12px] ${active === item.key ? 'bg-[rgba(254,44,85,0.08)] font-semibold text-[var(--yak-brand-color)]' : 'bg-transparent text-[#667085]'}`}
                      onClick={() => {
                        const root = scrollContainerRef.current;
                        const target = root?.querySelector<HTMLElement>(`#${item.key}`);
                        if (!root || !target) return;

                        const targetTop = Math.min(
                          Math.max(
                            root.scrollTop +
                              target.getBoundingClientRect().top -
                              root.getBoundingClientRect().top -
                              SECTION_SCROLL_OFFSET,
                            0,
                          ),
                          root.scrollHeight - root.clientHeight,
                        );
                        scrollingToRef.current = item.key;
                        setActive(item.key);
                        if (Math.abs(root.scrollTop - targetTop) <= 1) {
                          scrollingToRef.current = null;
                          return;
                        }
                        root.scrollTo({ top: targetTop, behavior: 'smooth' });
                      }}
                    >
                      <span
                        className={`relative z-10 h-[11px] w-[11px] rounded-full border ${active === item.key ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color)]' : 'border-[#d0d5dd] bg-[#98a2b3]'}`}
                      />
                      {item.label}
                    </button>
                  ))}
                </div>
              </nav>
            </aside>
          </div>
        </Form>
      </div>
    </ConfigProvider>
  );
}
