import YakButton from '@/components/YakButton';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { createDevelopmentNode } from '@/pages/data-development/service';
import type { DevelopmentNodeType } from '@/pages/data-development/types';
import {
  API_SUCCESS_CODE,
  extractErrorMessage,
  type ApiResponse,
} from '@/services/http/response';
import { createWorkflowDefinition } from '@/services/workflow/definitions';
import { BRAND_THEME } from '@/styles/brand';
import {
  CodeOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history, request, useLocation } from '@umijs/max';
import {
  ConfigProvider,
  Form,
  Input,
  message,
  type InputProps,
} from 'antd';
import type { TextAreaProps } from 'antd/es/input/TextArea';
import {
  useMemo,
  useState,
  type FocusEventHandler,
  type ReactNode,
} from 'react';

type CreateType =
  | 'offline'
  | 'realtime'
  | 'development'
  | 'workflow';

type OfflineMode = 'GUIDE_SINGLE' | 'GUIDE_MULTI';

type RealtimeEditorMode = 'wizard' | 'yaml';

interface QuickCreateFormValues {
  name: string;
}

interface CreateTab {
  key: CreateType;
  label: string;
  title: string;
  buttonText: string;
  maxLength: number;
}

interface ChoiceOption<T extends string> {
  value: T;
  label: string;
  description?: string;
  icon?: ReactNode;
}

type FloatingInputProps = Omit<
  InputProps,
  'onFocus' | 'onBlur' | 'placeholder'
> & {
  label: string;
  textarea?: boolean;
  rows?: number;
  autoSize?: TextAreaProps['autoSize'];
  onFocus?: FocusEventHandler<HTMLInputElement | HTMLTextAreaElement>;
  onBlur?: FocusEventHandler<HTMLInputElement | HTMLTextAreaElement>;
};

const CREATE_TABS: CreateTab[] = [
  {
    key: 'offline',
    label: '离线同步',
    title: '创建离线同步任务',
    buttonText: '创建并进入配置',
    maxLength: 64,
  },
  {
    key: 'realtime',
    label: '实时同步',
    title: '创建实时同步任务',
    buttonText: '创建并进入编辑器',
    maxLength: 200,
  },
  {
    key: 'development',
    label: '数据开发',
    title: '创建开发节点',
    buttonText: '创建并打开编辑器',
    maxLength: 128,
  },
  {
    key: 'workflow',
    label: '工作流',
    title: '创建工作流',
    buttonText: '创建并进入编排',
    maxLength: 100,
  },
];

const OFFLINE_MODES: ChoiceOption<OfflineMode>[] = [
  {
    value: 'GUIDE_SINGLE',
    label: '单表同步',
    description: '一张来源表同步到一张目标表',
    icon: <TableOutlined />,
  },
  {
    value: 'GUIDE_MULTI',
    label: '多表同步',
    description: '批量选择多张表完成同步',
    icon: <DatabaseOutlined />,
  },
];

const REALTIME_EDITOR_MODES: ChoiceOption<RealtimeEditorMode>[] = [
  {
    value: 'wizard',
    label: '向导模式',
    description: '通过可视化步骤完成同步配置',
  },
  {
    value: 'yaml',
    label: 'YAML 模式',
    description: '直接使用 YAML 编辑完整配置',
    icon: <CodeOutlined />,
  },
];

const DEVELOPMENT_TYPES: ChoiceOption<DevelopmentNodeType>[] = [
  {
    value: 'SQL',
    label: 'SQL',
  },
  {
    value: 'PYTHON',
    label: 'Python',
  },
  {
    value: 'SHELL',
    label: 'Shell',
  },
  {
    value: 'JAVA',
    label: 'Java',
  },
  {
    value: 'DATASET',
    label: '数据集',
  },
  {
    value: 'DATA_SERVICE',
    label: '数据服务',
  },
];

const PAGE_FONT =
  "'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif";

const FORM_ITEM_CLASS_NAME =
  '!mb-5 [&_.ant-form-item-explain]:!pt-1.5 [&_.ant-form-item-explain-error]:!text-[12px] [&_.ant-form-item-explain-error]:!leading-[18px] [&_.ant-form-item-explain-error]:!text-[#b42318]';

/**
 * Quick Create 只负责创建最小草稿。
 *
 * 离线同步和实时同步使用轻量接口，避免本页面依赖数据源、
 * 运行环境、Connector 等完整业务配置。
 */
const quickCreateApi = {
  offline: (payload: {
    jobName: string;
    mode: OfflineMode;
  }) =>
    request<ApiResponse<string | number>>(
      '/api/v1/job/batch-definition/quick-create',
      {
        method: 'POST',
        data: payload,
      },
    ),

  realtime: (payload: { name: string }) =>
    request<ApiResponse<number>>(
      '/api/v1/realtime-sync/quick-create',
      {
        method: 'POST',
        data: payload,
      },
    ),
};

const normalizeCreateType = (
  value?: string | null,
): CreateType =>
  CREATE_TABS.some((item) => item.key === value)
    ? (value as CreateType)
    : 'offline';

const unwrapApiResponse = <T,>(
  response: ApiResponse<T>,
  fallback: string,
): T => {
  if (response.code !== API_SUCCESS_CODE) {
    throw new Error(extractErrorMessage(response, fallback));
  }

  if (response.data === undefined || response.data === null) {
    throw new Error(fallback);
  }

  return response.data;
};

/**
 * 同时支持普通 Input 和 TextArea 的浮动标签控件。
 *
 * 当前快速创建只使用名称 Input；后续页面新增备注、描述等 TextArea 时，
 * 直接传 textarea 即可保持和登录页一致的交互与视觉。
 */
function FloatingInput({
  label,
  textarea = false,
  rows = 4,
  autoSize,
  onBlur,
  onFocus,
  value,
  className,
  ...inputProps
}: FloatingInputProps) {
  const [focused, setFocused] = useState(false);
  const { status } = Form.Item.useStatus();

  const floating = focused || String(value ?? '').length > 0;
  const hasError = status === 'error';

  const handleFocus: FocusEventHandler<
    HTMLInputElement | HTMLTextAreaElement
  > = (event) => {
    setFocused(true);
    onFocus?.(event);
  };

  const handleBlur: FocusEventHandler<
    HTMLInputElement | HTMLTextAreaElement
  > = (event) => {
    setFocused(false);
    onBlur?.(event);
  };

  const borderClassName = hasError
    ? '!border-[#d92d20] hover:!border-[#d92d20] focus:!border-[#d92d20] focus-within:!border-[#d92d20]'
    : '!border-[#dededb] hover:!border-[#bdbdb8] focus:!border-[#171717] focus-within:!border-[#171717]';

  const controlClassName = textarea
    ? [
        '!min-h-[116px] !rounded-[22px] !bg-white !px-4 !pb-3 !pt-4',
        '!text-[15px] !leading-6 !shadow-none',
        borderClassName,
        className,
      ]
        .filter(Boolean)
        .join(' ')
    : [
        '!h-11 !rounded-full !bg-white !px-4 !text-[15px] !shadow-none',
        borderClassName,
        className,
      ]
        .filter(Boolean)
        .join(' ');

  const commonProps = {
    ...inputProps,
    value,
    className: controlClassName,
    placeholder: '',
    onFocus: handleFocus,
    onBlur: handleBlur,
  };

  return (
    <div className="relative">
      {textarea ? (
        <Input.TextArea
          {...(commonProps as TextAreaProps)}
          rows={rows}
          autoSize={autoSize}
        />
      ) : (
        <Input {...(commonProps as InputProps)} />
      )}

      <label
        htmlFor={inputProps.id}
        className={[
          'pointer-events-none absolute left-4 z-10 bg-white px-1',
          'transition-all duration-200 ease-out',
          floating
            ? 'top-0 -translate-y-1/2 text-[12px] font-medium text-[#333]'
            : textarea
              ? 'top-[22px] -translate-y-1/2 text-[15px] text-[#aaa]'
              : 'top-1/2 -translate-y-1/2 text-[15px] text-[#aaa]',
        ].join(' ')}
      >
        {label}
      </label>
    </div>
  );
}

function ValidationMessage({ children }: { children: string }) {
  return (
    <span
      className="inline-flex h-[18px] items-center gap-1.5 align-middle leading-[18px]"
      style={{ marginBottom: 8 }}
    >
      <ExclamationCircleOutlined className="flex shrink-0 items-center text-[12px] leading-none [&_svg]:block" />

      <span className="leading-[18px]">{children}</span>
    </span>
  );
}

function ChoiceGrid<T extends string>({
  value,
  options,
  columns = 2,
  compact = false,
  onChange,
}: {
  value: T;
  options: ChoiceOption<T>[];
  columns?: 2 | 3;
  compact?: boolean;
  onChange: (value: T) => void;
}) {
  return (
    <div
      className={[
        'grid gap-2.5',
        columns === 3 ? 'grid-cols-3' : 'grid-cols-2',
      ].join(' ')}
    >
      {options.map((item) => {
        const selected = value === item.value;

        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            className={[
              'group relative border bg-white text-left transition-all duration-150',
              compact
                ? 'h-11 rounded-full px-4'
                : 'min-h-[74px] rounded-[18px] px-4 py-3.5',
              selected
                ? 'border-[#171717] shadow-[0_0_0_1px_#171717]'
                : 'border-[#dededb] hover:border-[#bdbdb8]',
            ].join(' ')}
          >
            <div
              className={[
                'flex h-full items-center',
                compact ? 'justify-center' : 'gap-3',
              ].join(' ')}
            >
              {!compact && item.icon ? (
                <span
                  className={[
                    'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-[15px]',
                    selected
                      ? 'bg-[#171717] text-white'
                      : 'bg-[#f1f1ee] text-[#74746f]',
                  ].join(' ')}
                >
                  {item.icon}
                </span>
              ) : null}

              <span
                className={
                  compact
                    ? 'text-center'
                    : 'min-w-0 pr-5'
                }
              >
                <span
                  className={[
                    'block text-[13px] font-medium',
                    selected
                      ? 'text-[#171717]'
                      : 'text-[#3f3f3b]',
                  ].join(' ')}
                >
                  {item.label}
                </span>

                {!compact && item.description ? (
                  <span className="mt-1 block text-[11px] leading-[17px] text-[#999994]">
                    {item.description}
                  </span>
                ) : null}
              </span>
            </div>

            {!compact ? (
              <span
                className={[
                  'absolute right-3 top-3 flex h-4 w-4 items-center justify-center rounded-full border',
                  selected
                    ? 'border-[#171717]'
                    : 'border-[#cfcfca]',
                ].join(' ')}
              >
                {selected ? (
                  <span className="h-2 w-2 rounded-full bg-[#171717]" />
                ) : null}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

function QuickCreateGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <path
        d="M4.5 11.5C4.5 8.46243 6.96243 6 10 6H19.0503C20.4296 6 21.7523 6.54797 22.7277 7.52332L25.2044 10H39.5C42.5376 10 45 12.4624 45 15.5V18H4.5V11.5Z"
        fill="#D5E5FC"
      />

      <path
        d="M6.19006 16.6202C6.70786 14.7216 8.4316 13.4048 10.3995 13.4048H42.2453C45.1495 13.4048 47.247 16.1834 46.4538 18.9772L40.8764 38.6224C40.3439 40.4979 38.6311 41.7917 36.6815 41.7917H4.68009C1.7732 41.7917 -0.32492 39.0082 0.473174 36.2129L6.19006 16.6202Z"
        fill="#BDD5F8"
      />

      <path
        d="M7.79688 18.2041H43.9268L38.8545 36.0956C38.5684 37.1047 37.6478 37.8021 36.599 37.8021H4.6875L7.79688 18.2041Z"
        fill="#C9DDFC"
      />
    </svg>
  );
}

function EditorGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <rect
        x="4"
        y="7"
        width="38"
        height="35"
        rx="7"
        fill="#D4E4FD"
      />

      <path
        d="M15.5 20.5H30.5M30.5 20.5L26.75 16.75M30.5 20.5L26.75 24.25"
        stroke="#86A8D9"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M30.5 29H15.5M15.5 29L19.25 25.25M15.5 29L19.25 32.75"
        stroke="#86A8D9"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M42.5 2.5C42.9581 5.67343 45.3266 8.04189 48.5 8.5C45.3266 8.95811 42.9581 11.3266 42.5 14.5C42.0419 11.3266 39.6734 8.95811 36.5 8.5C39.6734 8.04189 42.0419 5.67343 42.5 2.5Z"
        fill="#AFC8F2"
      />
    </svg>
  );
}

function DraftGuideIcon() {
  return (
    <svg
      width="50"
      height="46"
      viewBox="0 0 50 46"
      fill="none"
      aria-hidden="true"
    >
      <rect
        x="13"
        y="4"
        width="31"
        height="31"
        rx="7"
        fill="#D5E5FC"
      />

      <rect
        x="5"
        y="17"
        width="23"
        height="25"
        rx="5.5"
        fill="#ABC7F0"
      />

      <path
        d="M11.5 29.5L15.25 33.25L22 25.75"
        stroke="white"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

const CREATE_GUIDE_ITEMS = [
  {
    key: 'quick',
    title: '快速创建',
    description: '只填写最核心的信息',
    icon: <QuickCreateGuideIcon />,
  },
  {
    key: 'editor',
    title: '进入编辑器',
    description: '完整配置留到业务页面',
    icon: <EditorGuideIcon />,
  },
  {
    key: 'draft',
    title: '草稿状态',
    description: '不会自动发布或启动任务',
    icon: <DraftGuideIcon />,
  },
] as const;

function CreateGuide() {
  return (
    <div className="bg-white">
      <div
        className="grid h-[108px] grid-cols-3 px-8"
        style={{
          fontFamily: PAGE_FONT,
        }}
      >
        {CREATE_GUIDE_ITEMS.map((item, index) => (
          <div
            key={item.key}
            className="relative flex h-full items-center pl-[72px] 2xl:pl-[96px]"
          >
            <div className="flex min-w-0 items-center gap-[14px]">
              <span className="flex h-[46px] w-[50px] shrink-0 items-center justify-center">
                {item.icon}
              </span>

              <div className="min-w-0 pt-px">
                <div className="text-[13px] font-semibold leading-[18px] text-[#161823]">
                  {item.title}
                </div>

                <div className="mt-1 max-w-[220px] text-[11px] font-normal leading-[17px] text-[#8a9099]">
                  {item.description}
                </div>
              </div>
            </div>

            {index < CREATE_GUIDE_ITEMS.length - 1 ? (
              <span className="absolute right-0 top-1/2 h-[56px] w-px -translate-y-1/2 bg-[#e8e9ec]" />
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function UnifiedCreatePage() {
  const location = useLocation();
  const { currentProject } = useSecurityProject();
  const [form] = Form.useForm<QuickCreateFormValues>();

  const [submitting, setSubmitting] = useState(false);

  const [offlineMode, setOfflineMode] =
    useState<OfflineMode>('GUIDE_SINGLE');

  const [realtimeEditorMode, setRealtimeEditorMode] =
    useState<RealtimeEditorMode>('wizard');

  const [developmentType, setDevelopmentType] =
    useState<DevelopmentNodeType>('SQL');

  const activeType = useMemo(
    () =>
      normalizeCreateType(
        new URLSearchParams(location.search).get('type'),
      ),
    [location.search],
  );

  const activeTab = useMemo(
    () =>
      CREATE_TABS.find((item) => item.key === activeType) ||
      CREATE_TABS[0],
    [activeType],
  );

  const fieldLabel =
    activeType === 'development'
      ? '节点名称'
      : activeType === 'workflow'
        ? '工作流名称'
        : '任务名称';

  const switchType = (type: CreateType) => {
    if (type === activeType) return;

    form.resetFields();
    history.replace(`/create?type=${type}`);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const name = values.name.trim();

      setSubmitting(true);

      if (activeType === 'offline') {
        const response = await quickCreateApi.offline({
          jobName: name,
          mode: offlineMode,
        });

        const id = unwrapApiResponse(
          response,
          '创建离线同步任务失败',
        );

        message.success('任务草稿已创建，请继续完成同步配置');

        history.push(
          offlineMode === 'GUIDE_MULTI'
            ? `/sync/batch-link-up/${encodeURIComponent(
                String(id),
              )}/config/multi?scene=create`
            : `/sync/batch-link-up/${encodeURIComponent(
                String(id),
              )}/config/single?scene=create`,
        );

        return;
      }

      if (activeType === 'realtime') {
        const response = await quickCreateApi.realtime({
          name,
        });

        const id = unwrapApiResponse(
          response,
          '创建实时同步任务失败',
        );

        message.success(
          '任务草稿已创建，请继续完成实时同步配置',
        );

        history.push(
          `/sync/realtime/${encodeURIComponent(
            String(id),
          )}/detail?scene=create&editor=${realtimeEditorMode}`,
        );

        return;
      }

      if (activeType === 'development') {
        const response = await createDevelopmentNode({
          name,
          type: developmentType,
          projectId: currentProject?.id
            ? String(currentProject.id)
            : undefined,
        });

        const created = unwrapApiResponse(
          response,
          '创建数据开发节点失败',
        );

        message.success('开发节点已创建，正在打开编辑器');

        history.push(
          `/data-development?nodeId=${encodeURIComponent(
            String(created.id),
          )}&scene=create`,
        );

        return;
      }

      const created = await createWorkflowDefinition({
        name,
      });

      if (!created?.id) {
        throw new Error('工作流创建成功但未返回 ID');
      }

      message.success(
        '工作流草稿已创建，请继续完成任务编排',
      );

      history.push(
        `/workflow/definition/${encodeURIComponent(
          String(created.id),
        )}?scene=create`,
      );
    } catch (error: unknown) {
      const formError = error as {
        errorFields?: unknown[];
      };

      if (formError?.errorFields) return;

      message.error(
        error instanceof Error
          ? error.message
          : '创建失败，请稍后重试',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const renderExtraOptions = () => {
    if (activeType === 'offline') {
      return (
        <div className="mt-1">
          <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
            同步方式
          </div>

          <ChoiceGrid
            value={offlineMode}
            options={OFFLINE_MODES}
            onChange={setOfflineMode}
          />
        </div>
      );
    }

    if (activeType === 'realtime') {
      return (
        <div className="mt-1">
          <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
            编辑方式
          </div>

          <ChoiceGrid
            value={realtimeEditorMode}
            options={REALTIME_EDITOR_MODES}
            onChange={setRealtimeEditorMode}
          />
        </div>
      );
    }

    if (activeType === 'development') {
      return (
        <div className="mt-1">
          <div className="mb-2.5 text-[12px] font-medium text-[#555550]">
            节点类型
          </div>

          <ChoiceGrid
            compact
            columns={3}
            value={developmentType}
            options={DEVELOPMENT_TYPES}
            onChange={setDevelopmentType}
          />
        </div>
      );
    }

    return null;
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div
        className="min-h-[calc(100vh-64px)] bg-white text-[#161823]"
        style={{
          fontFamily: PAGE_FONT,
        }}
      >
        {/* 顶部 Tab */}
        <div
          className="border-b border-[#f1f1f2] bg-white pr-8"
          style={{
            marginLeft: 24,
          }}
        >
          <div className="flex h-[66px] items-end gap-8">
            {CREATE_TABS.map((item) => {
              const active = item.key === activeType;

              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => switchType(item.key)}
                  className={[
                    'relative flex h-[66px] items-end border-0 bg-transparent px-0 pb-[9px]',
                    'text-[18px] leading-6 tracking-0 text-[#161823]',
                    'transition-colors duration-150',
                    active
                      ? 'font-semibold'
                      : 'font-normal hover:text-black',
                  ].join(' ')}
                >
                  {item.label}

                  {active ? (
                    <span className="absolute inset-x-0 bottom-[-1px] h-[3px] rounded-[1px] bg-[#161823]" />
                  ) : null}
                </button>
              );
            })}
          </div>
        </div>

        {/* 顶部说明区 */}
        <CreateGuide />

        {/* 白色背景中的圆角表单区域 */}
        <main className="flex min-h-[600px] justify-center bg-white px-6 py-12">
          <div className="h-fit w-full max-w-[680px] rounded-[28px] bg-[#f7f7f4] px-7 py-8 sm:px-10 sm:py-10">
            <h1 className="mb-8 mt-0 text-center text-[22px] font-semibold leading-8 tracking-[-0.2px] text-[#171717]">
              {activeTab.title}
            </h1>

            <Form
              form={form}
              layout="vertical"
              requiredMark={false}
              onFinish={submit}
            >
              <Form.Item
                className={FORM_ITEM_CLASS_NAME}
                name="name"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    message: (
                      <ValidationMessage>
                        {`请输入${fieldLabel}`}
                      </ValidationMessage>
                    ),
                  },
                  {
                    max: activeTab.maxLength,
                    message: (
                      <ValidationMessage>
                        {`${fieldLabel}不能超过 ${activeTab.maxLength} 个字符`}
                      </ValidationMessage>
                    ),
                  },
                ]}
              >
                <FloatingInput
                  id="quick-create-name"
                  label={fieldLabel}
                  autoFocus
                  autoComplete="off"
                  maxLength={activeTab.maxLength}
                />
              </Form.Item>

              {renderExtraOptions()}

              <YakButton
                block
                effect="glass"
                type="primary"
                htmlType="submit"
                loading={submitting}
                className="!mt-8 !h-11 !rounded-full !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
              >
                {activeTab.buttonText}
              </YakButton>
            </Form>
          </div>
        </main>
      </div>
    </ConfigProvider>
  );
}