import {
  Alert,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Radio,
  Row,
  Select,
  Space,
  Steps,
  Switch,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { realtimeApi } from './api';
import type { CdcPipelineSpec, DataSourceOption, RealtimeJob } from './types';

interface RouteFormValue {
  sourceTable: string;
  sinkTable: string;
  matchMode: 'EXACT' | 'REGEX';
  keyColumnsText: string;
}

interface FormValue extends Omit<CdcPipelineSpec, 'tables'> {
  name: string;
  description?: string;
  tables: RouteFormValue[];
}

const defaults: FormValue = {
  name: '',
  description: '',
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

const toFormValue = (job?: RealtimeJob): FormValue =>
  job?.spec
    ? {
        name: job.name,
        description: job.description,
        ...job.spec,
        tables: job.spec.tables.map((route) => ({
          ...route,
          keyColumnsText: route.keyColumns.join(','),
        })),
      }
    : defaults;

export default function JobEditor({
  open,
  job,
  dataSources,
  onClose,
  onSaved,
}: {
  open: boolean;
  job?: RealtimeJob;
  dataSources: DataSourceOption[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<FormValue>();
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const sourceOptions = useMemo(() => dataSources.filter((item) => item.dbType === 'MYSQL'), [dataSources]);
  const sinkOptions = useMemo(
    () => dataSources.filter((item) => ['MYSQL', 'POSTGRE_SQL', 'POSTGRESQL'].includes(item.dbType)),
    [dataSources],
  );

  useEffect(() => {
    if (open) {
      form.setFieldsValue(toFormValue(job));
      setStep(0);
    }
  }, [form, job, open]);

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
      const payload = { name: values.name.trim(), description: values.description?.trim(), spec };
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

  return (
    <Drawer
      title={job ? `编辑：${job.name}` : '新建实时同步'}
      width={860}
      open={open}
      onClose={onClose}
      destroyOnClose
      extra={
        <Button type="primary" loading={saving} onClick={save}>
          保存草稿
        </Button>
      }
    >
      <Steps
        current={step}
        onChange={setStep}
        items={[{ title: 'Source / Sink' }, { title: '表规则' }, { title: '运行参数' }]}
        style={{ marginBottom: 24 }}
      />
      <Form form={form} layout="vertical" initialValues={defaults}>
        <div style={{ display: step === 0 ? 'block' : 'none' }}>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item name="name" label="任务名称" rules={[{ required: true }]}>
                <Input maxLength={200} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="startupMode" label="启动模式">
                <Select
                  options={[
                    { value: 'initial', label: '全量 + 增量' },
                    { value: 'latest-offset', label: '仅最新增量' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea maxLength={1000} rows={2} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="sourceDataSourceRef"
                label="MySQL Source"
                rules={[{ required: true, message: '请选择 Source 数据源' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={sourceOptions.map((item) => ({ label: item.label, value: Number(item.value) }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="sinkDataSourceRef"
                label="MySQL / PostgreSQL Sink"
                rules={[{ required: true, message: '请选择 Sink 数据源' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={sinkOptions.map((item) => ({
                    label: `${item.label} (${item.dbType})`,
                    value: Number(item.value),
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Alert
            type="info"
            showIcon
            message="任务定义只保存数据源引用；连接坐标在发布和启动时重新解析，密码不会进入任务定义、部署快照或接口响应。"
          />
        </div>

        <div style={{ display: step === 1 ? 'block' : 'none' }}>
          <Form.List name="tables">
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                {fields.map((field, index) => (
                  <div key={field.key} style={{ padding: 16, border: '1px solid #eee', borderRadius: 8 }}>
                    <Typography.Text strong>表规则 {index + 1}</Typography.Text>
                    <Row gutter={12} style={{ marginTop: 12 }}>
                      <Col span={8}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'sourceTable']}
                          label="Source 表 / 正则"
                          rules={[{ required: true }]}
                        >
                          <Input placeholder="orders（数据源库内）" />
                        </Form.Item>
                      </Col>
                      <Col span={5}>
                        <Form.Item {...field} name={[field.name, 'matchMode']} label="匹配方式">
                          <Radio.Group
                            options={[
                              { label: '精确', value: 'EXACT' },
                              { label: '正则', value: 'REGEX' },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={7}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'sinkTable']}
                          label="Sink 表"
                          rules={[{ required: true }]}
                        >
                          <Input placeholder="public.orders" />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
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
                    {fields.length > 1 && (
                      <Button danger size="small" onClick={() => remove(field.name)}>
                        删除规则
                      </Button>
                    )}
                  </div>
                ))}
                <Button
                  onClick={() => add({ sourceTable: '', sinkTable: '', matchMode: 'EXACT', keyColumnsText: 'id' })}
                >
                  添加表规则
                </Button>
              </Space>
            )}
          </Form.List>
        </div>

        <div style={{ display: step === 2 ? 'block' : 'none' }}>
          <Alert
            type="warning"
            showIcon
            message="一期交付语义固定为 At-least-once，并强制 strict Replay Safety 与主键声明。"
            style={{ marginBottom: 16 }}
          />
          <Alert
            type="info"
            showIcon
            message="当前固定 Runtime Gateway 尚不接受每任务 Checkpoint 与重启策略覆盖；以下两项作为逻辑 Spec 保留，但由 Runtime 固定配置接管。"
            style={{ marginBottom: 16 }}
          />
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="parallelism" label="并行度">
                <InputNumber min={1} max={256} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="checkpointIntervalMs" label="Checkpoint 间隔 (Runtime 固定)">
                <InputNumber disabled min={10000} step={10000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="schemaEvolution" label="Schema Evolution">
                <Select options={['EVOLVE', 'IGNORE', 'FAIL'].map((value) => ({ value, label: value }))} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name={['restart', 'strategy']} label="重启策略 (Runtime 固定)">
                <Select
                  disabled
                  options={['fixed-delay', 'failure-rate', 'none'].map((value) => ({ value, label: value }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['restart', 'attempts']} label="重试次数 (Runtime 固定)">
                <InputNumber disabled min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['restart', 'delayMs']} label="重试间隔 (Runtime 固定)">
                <InputNumber disabled min={0} step={1000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name={['sink', 'batchSize']} label="Sink Batch Size">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['sink', 'flushIntervalMs']} label="Flush 间隔 (ms)">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['sink', 'maxRetries']} label="Sink 重试次数">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['sink', 'maxBatchBytes']} label="最大批次字节">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['sink', 'statementCacheSize']} label="Statement Cache">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name={['sink', 'strictReplaySafety']} label="Strict Replay Safety" valuePropName="checked">
                <Switch disabled />
              </Form.Item>
            </Col>
          </Row>
        </div>
      </Form>
    </Drawer>
  );
}
