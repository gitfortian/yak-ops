import YakButton from '@/components/YakButton';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { linkupJobDefinitionApi } from '@/pages/batch-link-up/api';
import { generateDataSourceOptions } from '@/pages/batch-link-up/DataSourceSelect';
import { connectorIdForDataSourceType } from '@/pages/batch-link-up/detail/form-schema/valueAdapter';
import {
  buildCreatePayload,
  extractGeneratedId,
  extractSavedId,
  isApiSuccess,
  responseMessage,
  type CreateSyncEndpoint,
  type CreateSyncTaskValues,
  type SyncMode,
} from '@/pages/batch-link-up/detail/model';
import {
  createDevelopmentNode,
  listDevelopmentDirectories,
} from '@/pages/data-development/service';
import type {
  DevelopmentDirectory,
  DevelopmentNodeType,
} from '@/pages/data-development/types';
import { realtimeApi } from '@/pages/realtime-sync/api';
import type { ComputeEnvironmentOption } from '@/pages/realtime-sync/types';
import { API_SUCCESS_CODE } from '@/services/http/response';
import { createWorkflowDefinition } from '@/services/workflow/definitions';
import { BRAND_THEME } from '@/styles/brand';
import {
  ArrowRightOutlined,
  CodeOutlined,
  CompassOutlined,
  DatabaseOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  ConfigProvider,
  Form,
  Input,
  Radio,
  Select,
  Spin,
  message,
} from 'antd';
import {
  ArrowRightLeft,
  Braces,
  RadioTower,
  Workflow,
} from 'lucide-react';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

type CreateType = 'offline' | 'realtime' | 'development' | 'workflow';
type RealtimeEditorMode = 'wizard' | 'yaml';

interface CreateTab {
  key: CreateType;
  label: string;
  title: string;
  description: string;
  icon: ReactNode;
}

interface ConnectorOption {
  value: string;
  label: ReactNode;
  pluginName?: string;
}

const CREATE_TABS: CreateTab[] = [
  {
    key: 'offline',
    label: '离线同步',
    title: '新建离线同步任务',
    description: '先创建任务草稿，再进入单表或多表同步配置。',
    icon: <ArrowRightLeft size={18} strokeWidth={1.9} />,
  },
  {
    key: 'realtime',
    label: '实时同步',
    title: '新建实时同步任务',
    description: '先创建基础任务并绑定运行环境，再进入向导或 YAML 配置。',
    icon: <RadioTower size={18} strokeWidth={1.9} />,
  },
  {
    key: 'development',
    label: '数据开发',
    title: '新建数据开发节点',
    description: '创建开发树节点后，直接进入对应节点的编辑工作区。',
    icon: <Braces size={18} strokeWidth={1.9} />,
  },
  {
    key: 'workflow',
    label: '工作流',
    title: '新建工作流',
    description: '先创建工作流草稿，再进入 DAG 画布完成任务编排。',
    icon: <Workflow size={18} strokeWidth={1.9} />,
  },
];

const OFFLINE_DEFAULT_DB_TYPE = 'MYSQL';
const DEVELOPMENT_ROOT = '__root__';

const normalizeCreateType = (value?: string | null): CreateType =>
  CREATE_TABS.some((item) => item.key === value)
    ? (value as CreateType)
    : 'offline';

const initialCreateType = () => {
  if (typeof window === 'undefined') return 'offline' as CreateType;
  return normalizeCreateType(new URLSearchParams(window.location.search).get('type'));
};

const unwrapApiResponse = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const buildOfflineJobName = (sourceDbType: string, targetDbType: string) =>
  `${sourceDbType} → ${targetDbType} 离线同步`.slice(0, 64);

const resolveOfflineEndpoint = (
  dbType: string,
  options: ConnectorOption[],
): CreateSyncEndpoint => {
  const option = options.find((item) => item.value === dbType);
  return {
    dbType,
    connectorId: connectorIdForDataSourceType(dbType),
    pluginName: option?.pluginName || `JDBC-${dbType}`,
  };
};

const preferredEnvironmentId = (environments: ComputeEnvironmentOption[]) =>
  environments.find((item) => item.defaultEnvironment && item.enabled)?.id ??
  environments.find((item) => item.enabled)?.id;

function PanelIntro({ tab }: { tab: CreateTab }) {
  return (
    <div className="mb-6 flex items-start gap-3">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#f2f4f7] text-[#475467]">
        {tab.icon}
      </div>
      <div className="min-w-0">
        <h2 className="m-0 text-[18px] font-semibold leading-7 text-[#101828]">
          {tab.title}
        </h2>
        <div className="mt-1 text-[12px] leading-5 text-[#98a2b3]">
          {tab.description}
        </div>
      </div>
    </div>
  );
}

function FormActions({
  loading,
  submitText = '创建并进入配置',
  onSubmit,
}: {
  loading: boolean;
  submitText?: string;
  onSubmit: () => void;
}) {
  return (
    <div className="mt-7 flex items-center justify-end gap-2 border-t border-[#eaecf0] pt-5">
      <YakButton disabled={loading} onClick={() => history.push('/home')}>
        取消
      </YakButton>
      <YakButton type="primary" loading={loading} onClick={onSubmit}>
        {submitText}
      </YakButton>
    </div>
  );
}

function OfflineCreatePanel() {
  const tab = CREATE_TABS[0];
  const [form] = Form.useForm<{
    sourceDbType: string;
    targetDbType: string;
    jobName: string;
    jobDesc?: string;
    mode: SyncMode;
  }>();
  const [submitting, setSubmitting] = useState(false);
  const autoJobNameRef = useRef('');
  const connectorOptions = useMemo(
    () => generateDataSourceOptions() as ConnectorOption[],
    [],
  );

  useEffect(() => {
    const defaultDbType =
      connectorOptions.find((item) => item.value === OFFLINE_DEFAULT_DB_TYPE)?.value ||
      connectorOptions[0]?.value ||
      '';
    const jobName = buildOfflineJobName(defaultDbType, defaultDbType);
    autoJobNameRef.current = jobName;
    form.setFieldsValue({
      sourceDbType: defaultDbType,
      targetDbType: defaultDbType,
      jobName,
      mode: 'GUIDE_SINGLE',
    });
  }, [connectorOptions, form]);

  const updateAutoName = (side: 'source' | 'target', value: string) => {
    const source = side === 'source' ? value : form.getFieldValue('sourceDbType');
    const target = side === 'target' ? value : form.getFieldValue('targetDbType');
    if (!source || !target) return;
    const nextName = buildOfflineJobName(source, target);
    const currentName = form.getFieldValue('jobName')?.trim() || '';
    if (!currentName || currentName === autoJobNameRef.current) {
      form.setFieldValue('jobName', nextName);
    }
    autoJobNameRef.current = nextName;
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const idResponse = await linkupJobDefinitionApi.getUniqueId();
      if (!isApiSuccess(idResponse)) {
        throw new Error(responseMessage(idResponse, '生成任务 ID 失败'));
      }
      const taskId = extractGeneratedId(idResponse);
      if (!taskId) throw new Error('生成任务 ID 失败');

      const createValues: CreateSyncTaskValues = {
        jobName: values.jobName.trim(),
        jobDesc: values.jobDesc?.trim(),
        mode: values.mode,
      };
      const payload = buildCreatePayload(
        taskId,
        createValues,
        resolveOfflineEndpoint(values.sourceDbType, connectorOptions),
        resolveOfflineEndpoint(values.targetDbType, connectorOptions),
      );
      const saveResponse = await linkupJobDefinitionApi.createDraft(payload);
      if (!isApiSuccess(saveResponse)) {
        throw new Error(responseMessage(saveResponse, '创建同步任务失败'));
      }
      const createdId = extractSavedId(saveResponse, taskId);
      message.success('任务草稿已创建，请继续完成同步配置');
      history.push(
        values.mode === 'GUIDE_MULTI'
          ? `/sync/batch-link-up/${createdId}/config/multi?scene=create`
          : `/sync/batch-link-up/${createdId}/config/single?scene=create`,
      );
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error instanceof Error ? error.message : '创建同步任务失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PanelIntro tab={tab} />
      <Form form={form} layout="vertical" requiredMark="optional">
        <div className="grid grid-cols-[minmax(0,1fr)_36px_minmax(0,1fr)] items-end gap-3">
          <Form.Item
            name="sourceDbType"
            label="来源类型"
            rules={[{ required: true, message: '请选择来源类型' }]}
          >
            <Select
              showSearch
              variant="filled"
              options={connectorOptions}
              optionFilterProp="value"
              placeholder="请选择来源类型"
              onChange={(value) => updateAutoName('source', value)}
            />
          </Form.Item>
          <div className="mb-7 flex h-8 items-center justify-center text-[#98a2b3]">
            <ArrowRightOutlined />
          </div>
          <Form.Item
            name="targetDbType"
            label="目标类型"
            rules={[{ required: true, message: '请选择目标类型' }]}
          >
            <Select
              showSearch
              variant="filled"
              options={connectorOptions}
              optionFilterProp="value"
              placeholder="请选择目标类型"
              onChange={(value) => updateAutoName('target', value)}
            />
          </Form.Item>
        </div>

        <Form.Item
          name="jobName"
          label="任务名称"
          rules={[
            { required: true, message: '请输入任务名称' },
            { max: 64, message: '任务名称不能超过 64 个字符' },
          ]}
        >
          <Input variant="filled" maxLength={64} showCount placeholder="例如：订单数据每日同步" />
        </Form.Item>
        <Form.Item
          name="jobDesc"
          label="任务描述"
          rules={[{ max: 200, message: '任务描述不能超过 200 个字符' }]}
        >
          <Input.TextArea
            variant="filled"
            rows={3}
            maxLength={200}
            showCount
            placeholder="请说明业务场景、同步范围和使用目的"
          />
        </Form.Item>
        <Form.Item
          name="mode"
          label="同步类型"
          rules={[{ required: true, message: '请选择同步类型' }]}
        >
          <Radio.Group className="grid w-full grid-cols-2 gap-3">
            <Radio.Button
              value="GUIDE_SINGLE"
              className="!h-auto !rounded-xl !border !border-[#e4e7ec] !px-4 !py-4 !shadow-none before:!hidden [&.ant-radio-button-wrapper-checked]:!border-[#fe2c55] [&.ant-radio-button-wrapper-checked]:!bg-[#fff5f7]"
            >
              <div className="flex items-start gap-3 whitespace-normal">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#667085]">
                  <TableOutlined />
                </span>
                <span className="text-left">
                  <b className="block text-[13px] text-[#344054]">单表同步</b>
                  <span className="mt-1 block text-[11px] leading-5 text-[#98a2b3]">配置一张来源表到一张目标表</span>
                </span>
              </div>
            </Radio.Button>
            <Radio.Button
              value="GUIDE_MULTI"
              className="!h-auto !rounded-xl !border !border-[#e4e7ec] !px-4 !py-4 !shadow-none before:!hidden [&.ant-radio-button-wrapper-checked]:!border-[#fe2c55] [&.ant-radio-button-wrapper-checked]:!bg-[#fff5f7]"
            >
              <div className="flex items-start gap-3 whitespace-normal">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#667085]">
                  <DatabaseOutlined />
                </span>
                <span className="text-left">
                  <b className="block text-[13px] text-[#344054]">多表同步</b>
                  <span className="mt-1 block text-[11px] leading-5 text-[#98a2b3]">批量选择多张来源表进行同步</span>
                </span>
              </div>
            </Radio.Button>
          </Radio.Group>
        </Form.Item>
      </Form>
      <FormActions loading={submitting} onSubmit={submit} />
    </div>
  );
}

function RealtimeCreatePanel() {
  const tab = CREATE_TABS[1];
  const [form] = Form.useForm<{
    name: string;
    description?: string;
    runtimeEnvironmentId: number;
  }>();
  const [editorMode, setEditorMode] = useState<RealtimeEditorMode>('wizard');
  const [submitting, setSubmitting] = useState(false);
  const [environmentLoading, setEnvironmentLoading] = useState(false);
  const [environments, setEnvironments] = useState<ComputeEnvironmentOption[]>([]);

  useEffect(() => {
    let cancelled = false;
    setEnvironmentLoading(true);
    realtimeApi
      .environments()
      .then((response) => {
        if (cancelled) return;
        const rows = response.data || [];
        setEnvironments(rows);
        const defaultId = preferredEnvironmentId(rows);
        if (defaultId) form.setFieldValue('runtimeEnvironmentId', defaultId);
      })
      .catch((error: any) => {
        if (!cancelled) message.error(error?.message || '运行环境加载失败');
      })
      .finally(() => {
        if (!cancelled) setEnvironmentLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [form]);

  const environmentOptions = useMemo(
    () =>
      environments.map((item) => ({
        value: item.id,
        disabled: !item.enabled,
        label: `${item.name}${item.defaultEnvironment ? ' · 默认' : ''} · Flink ${item.config.flinkVersion} / CDC ${item.config.flinkCdcVersion}${item.enabled ? '' : ' · 已停用'}`,
      })),
    [environments],
  );

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const environment = environments.find(
        (item) => item.id === Number(values.runtimeEnvironmentId),
      );
      if (!environment?.enabled) {
        message.error('请选择已启用的运行环境');
        return;
      }
      setSubmitting(true);
      const response = await realtimeApi.createBasic({
        name: values.name.trim(),
        description: values.description?.trim(),
        runtimeEnvironmentId: Number(values.runtimeEnvironmentId),
      });
      const createdId = response.data;
      if (!createdId) throw new Error('实时同步任务创建成功但未返回任务 ID');
      message.success('基础任务已创建，请继续完成同步配置');
      history.push(
        `/sync/realtime/${createdId}/detail?scene=create&editor=${editorMode}`,
      );
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error instanceof Error ? error.message : '创建实时同步任务失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PanelIntro tab={tab} />
      <div className="mb-2 text-[13px] font-medium text-[#475467]">创建方式</div>
      <div className="mb-5 grid grid-cols-2 gap-3">
        {([
          {
            value: 'wizard' as const,
            title: '向导模式',
            description: '通过数据源、数据表和同步方式完成配置，适合大多数场景。',
            icon: <CompassOutlined />,
          },
          {
            value: 'yaml' as const,
            title: 'YAML 模式',
            description: '使用 YAML 描述同步任务，为高级配置保留完整扩展能力。',
            icon: <CodeOutlined />,
          },
        ]).map((mode) => {
          const selected = editorMode === mode.value;
          return (
            <button
              key={mode.value}
              type="button"
              onClick={() => setEditorMode(mode.value)}
              className={[
                'rounded-xl border p-4 text-left transition-all',
                selected
                  ? 'border-[#fe2c55] bg-[#fff7f9] shadow-[0_0_0_2px_rgba(254,44,85,.05)]'
                  : 'border-[#e4e7ec] bg-white hover:border-[#fda4b8]',
              ].join(' ')}
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#f2f4f7] text-[#667085]">
                {mode.icon}
              </div>
              <div className="mt-3 text-[14px] font-semibold text-[#344054]">{mode.title}</div>
              <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">{mode.description}</div>
            </button>
          );
        })}
      </div>

      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          name="runtimeEnvironmentId"
          label="运行环境"
          rules={[{ required: true, message: '请选择运行环境' }]}
        >
          <Select
            showSearch
            variant="filled"
            optionFilterProp="label"
            loading={environmentLoading}
            disabled={environmentLoading}
            options={environmentOptions}
            placeholder="请选择 Flink CDC 运行环境"
            notFoundContent={environmentLoading ? <Spin size="small" /> : '暂无已启用运行环境'}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="任务名称"
          rules={[
            { required: true, message: '请输入任务名称' },
            { max: 200, message: '任务名称不能超过 200 个字符' },
          ]}
        >
          <Input variant="filled" maxLength={200} showCount placeholder="例如：订单实时同步" />
        </Form.Item>
        <Form.Item
          name="description"
          label="任务描述"
          rules={[{ max: 1000, message: '任务描述不能超过 1000 个字符' }]}
        >
          <Input.TextArea
            variant="filled"
            rows={3}
            maxLength={1000}
            showCount
            placeholder="请说明业务场景、同步范围和使用目的"
          />
        </Form.Item>
      </Form>
      <FormActions loading={submitting} onSubmit={submit} />
    </div>
  );
}

function DevelopmentCreatePanel() {
  const tab = CREATE_TABS[2];
  const { currentProject } = useSecurityProject();
  const [form] = Form.useForm<{
    type: DevelopmentNodeType;
    directoryId: string;
    name: string;
  }>();
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [directoryLoading, setDirectoryLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    form.setFieldsValue({ type: 'SQL', directoryId: DEVELOPMENT_ROOT });
    let cancelled = false;
    setDirectoryLoading(true);
    listDevelopmentDirectories()
      .then((response) => {
        if (cancelled) return;
        setDirectories(unwrapApiResponse(response, '查询数据开发目录失败') || []);
      })
      .catch((error) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '查询数据开发目录失败');
        }
      })
      .finally(() => {
        if (!cancelled) setDirectoryLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [form]);

  const directoryOptions = useMemo(
    () => [
      { label: '/', value: DEVELOPMENT_ROOT },
      ...directories.map((item) => ({ label: item.path, value: item.id })),
    ],
    [directories],
  );

  const developmentTypes: Array<{ value: DevelopmentNodeType; label: string }> = [
    { value: 'SQL', label: 'SQL' },
    { value: 'PYTHON', label: 'Python' },
    { value: 'SHELL', label: 'Shell' },
    { value: 'JAVA', label: 'Java' },
    { value: 'DATASET', label: '数据集' },
    { value: 'DATA_SERVICE', label: '数据服务' },
  ];

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const created = unwrapApiResponse(
        await createDevelopmentNode({
          name: values.name.trim(),
          type: values.type,
          projectId: currentProject?.id ? String(currentProject.id) : undefined,
          directoryId:
            values.directoryId === DEVELOPMENT_ROOT ? undefined : values.directoryId,
        }),
        '创建数据开发节点失败',
      );
      message.success('开发节点已创建，正在打开编辑工作区');
      history.push(
        `/data-development?nodeId=${encodeURIComponent(String(created.id))}&scene=create`,
      );
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error instanceof Error ? error.message : '创建数据开发节点失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PanelIntro tab={tab} />
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          name="type"
          label="节点类型"
          rules={[{ required: true, message: '请选择节点类型' }]}
        >
          <Radio.Group className="grid w-full grid-cols-3 gap-2">
            {developmentTypes.map((item) => (
              <Radio.Button
                key={item.value}
                value={item.value}
                className="!h-10 !rounded-lg !border !border-[#e4e7ec] !px-3 !text-center !text-[12px] !shadow-none before:!hidden [&.ant-radio-button-wrapper-checked]:!border-[#fe2c55] [&.ant-radio-button-wrapper-checked]:!bg-[#fff5f7]"
              >
                {item.label}
              </Radio.Button>
            ))}
          </Radio.Group>
        </Form.Item>
        <Form.Item
          name="directoryId"
          label="保存位置"
          rules={[{ required: true, message: '请选择保存位置' }]}
        >
          <Select
            showSearch
            variant="filled"
            loading={directoryLoading}
            optionFilterProp="label"
            options={directoryOptions}
            placeholder="请选择开发目录"
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="节点名称"
          rules={[
            { required: true, whitespace: true, message: '请输入节点名称' },
            { max: 128, message: '节点名称不能超过 128 个字符' },
          ]}
        >
          <Input variant="filled" maxLength={128} showCount placeholder="例如：ods_order_detail" />
        </Form.Item>
      </Form>
      <FormActions
        loading={submitting}
        submitText="创建并打开编辑器"
        onSubmit={submit}
      />
    </div>
  );
}

function WorkflowCreatePanel() {
  const tab = CREATE_TABS[3];
  const [form] = Form.useForm<{ name: string; description?: string }>();
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const created = await createWorkflowDefinition({
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
      });
      message.success('工作流草稿已创建，请继续配置任务节点');
      history.push(
        `/workflow/definition/${encodeURIComponent(created.id)}?scene=create`,
      );
    } catch (error: any) {
      if (!error?.errorFields) {
        message.error(error instanceof Error ? error.message : '创建工作流失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PanelIntro tab={tab} />
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          name="name"
          label="工作流名称"
          rules={[
            { required: true, whitespace: true, message: '请输入工作流名称' },
            { max: 100, message: '工作流名称不能超过 100 个字符' },
          ]}
        >
          <Input variant="filled" maxLength={100} showCount placeholder="例如：每日订单加工工作流" />
        </Form.Item>
        <Form.Item
          name="description"
          label="工作流描述"
          rules={[{ max: 500, message: '工作流描述不能超过 500 个字符' }]}
        >
          <Input.TextArea
            variant="filled"
            rows={4}
            maxLength={500}
            showCount
            placeholder="请说明工作流用途、执行范围和关键依赖"
          />
        </Form.Item>
      </Form>
      <FormActions
        loading={submitting}
        submitText="创建并进入编排"
        onSubmit={submit}
      />
    </div>
  );
}

const panelForType = (type: CreateType) => {
  if (type === 'realtime') return <RealtimeCreatePanel />;
  if (type === 'development') return <DevelopmentCreatePanel />;
  if (type === 'workflow') return <WorkflowCreatePanel />;
  return <OfflineCreatePanel />;
};

export default function UnifiedCreatePage() {
  const [activeType, setActiveType] = useState<CreateType>(initialCreateType);
  const activeTab = CREATE_TABS.find((item) => item.key === activeType) || CREATE_TABS[0];

  const switchType = (type: CreateType) => {
    setActiveType(type);
    history.replace(`/create?type=${type}`);
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] p-4 text-[#161823]">
        <section className="min-h-[calc(100vh-96px)] rounded-[18px] border border-[#f0f0f0] bg-white px-6 pb-7 pt-5 shadow-[0_1px_2px_rgba(16,24,40,.02)]">
          <div className="flex h-12 items-end gap-8 border-b border-[#eaecf0]">
            {CREATE_TABS.map((item) => {
              const active = item.key === activeType;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => switchType(item.key)}
                  className={[
                    'relative h-12 border-0 bg-transparent px-0 text-[15px] font-medium transition-colors',
                    active ? 'text-[#161823]' : 'text-[#667085] hover:text-[#344054]',
                  ].join(' ')}
                >
                  {item.label}
                  {active ? (
                    <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-[#161823]" />
                  ) : null}
                </button>
              );
            })}
          </div>

          <div className="grid grid-cols-1 border-b border-[#f2f4f7] py-4 md:grid-cols-3">
            <div className="px-5 py-2 md:border-r md:border-[#eaecf0]">
              <div className="text-[12px] font-semibold text-[#344054]">创建策略</div>
              <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">先创建最小可用草稿，不在首页填写完整业务配置</div>
            </div>
            <div className="px-5 py-2 md:border-r md:border-[#eaecf0]">
              <div className="text-[12px] font-semibold text-[#344054]">下一步</div>
              <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">创建成功后自动跳转到 {activeTab.label} 对应的编辑页面</div>
            </div>
            <div className="px-5 py-2">
              <div className="text-[12px] font-semibold text-[#344054]">生命周期</div>
              <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">这里只创建草稿，不会自动发布、上线或启动任务</div>
            </div>
          </div>

          <div className="mt-5 flex min-h-[540px] items-start justify-center rounded-[16px] bg-[#f5f5f7] px-6 py-8">
            <div className="w-full max-w-[820px] rounded-[16px] border border-[#e8eaed] bg-white p-7 shadow-[0_8px_24px_rgba(16,24,40,.05)]">
              {panelForType(activeType)}
            </div>
          </div>
        </section>
      </div>
    </ConfigProvider>
  );
}
