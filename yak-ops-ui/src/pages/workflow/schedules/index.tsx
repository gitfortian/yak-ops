import { getWorkflowDefinition, type WorkflowDefinition } from '@/services/workflow/definitions';
import {
  createWorkflowSchedule,
  deleteWorkflowSchedule,
  listWorkflowSchedules,
  updateWorkflowSchedule,
  type WorkflowBackfill,
  type WorkflowSchedule,
  type WorkflowScheduleExecutionStrategy,
  type WorkflowScheduleMisfireStrategy,
} from '@/services/workflow/schedules';
import { BRAND_THEME } from '@/styles/brand';
import { history, useLocation, useParams } from '@umijs/max';
import {
  Button,
  ConfigProvider,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Spin,
  Tooltip,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  DatabaseBackup,
  History,
  ListTree,
  Save,
  Trash2,
} from 'lucide-react';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import BackfillDrawer from './BackfillDrawer';
import BackfillHistoryDrawer from './BackfillHistoryDrawer';
import TriggerLedgerDrawer from './TriggerLedgerDrawer';

interface FormValues {
  name: string;
  cronExpression: string;
  timezone: string;
  effectiveRange?: [Dayjs, Dayjs];
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
  inputJson: string;
}

const SECTION_ITEMS = [
  { key: 'schedule-basic', label: '调度设置' },
  { key: 'schedule-strategy', label: '运行策略' },
  { key: 'schedule-advanced', label: '高级配置' },
] as const;

type SectionKey = (typeof SECTION_ITEMS)[number]['key'];

const LAST_SECTION_KEY = SECTION_ITEMS[SECTION_ITEMS.length - 1].key;
const SCROLL_BOTTOM_THRESHOLD = 12;
const SECTION_TOP_OFFSET = 24;
const LOCATE_LOCK_DURATION = 650;

const CRON_PRESETS = [
  { label: '每天 02:00', value: '0 0 2 * * ?' },
  { label: '每小时', value: '0 0 * * * ?' },
  { label: '每 10 分钟', value: '0 0/10 * * * ?' },
] as const;

const WORKFLOW_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ONLINE: '已上线',
  OFFLINE: '已下线',
};

interface SectionNavigatorProps {
  activeKey: SectionKey;
  onSelect: (key: SectionKey) => void;
}

function SectionNavigator({ activeKey, onSelect }: SectionNavigatorProps) {
  return (
    <nav aria-label="调度配置区块定位" className="rounded-xl bg-white px-3 py-4">
      <div className="mb-2 px-2 text-[12px] font-semibold text-[#344054]">
        快速定位
      </div>
      <div className="space-y-1">
        {SECTION_ITEMS.map((item) => {
          const active = activeKey === item.key;
          return (
            <button
              key={item.key}
              type="button"
              aria-current={active ? 'location' : undefined}
              className={[
                'flex w-full cursor-pointer items-center gap-2.5 rounded-lg border-0 px-2 py-2 text-left transition-colors',
                active
                  ? 'bg-[rgba(254,44,85,0.08)] text-[var(--yak-brand-color)]'
                  : 'bg-transparent text-[#667085] hover:bg-[#f7f8fa] hover:text-[#344054]',
              ].join(' ')}
              onClick={() => onSelect(item.key)}
            >
              <span
                aria-hidden
                className={[
                  'h-2 w-2 shrink-0 rounded-full',
                  active ? 'bg-[var(--yak-brand-color)]' : 'bg-[#c5c9d0]',
                ].join(' ')}
              />
              <span className={active ? 'text-[12px] font-semibold' : 'text-[12px]'}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}

function SectionCard({
  id,
  title,
  children,
}: {
  id: SectionKey;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="rounded-xl bg-white px-6 py-5">
      <div className="mb-5 text-[14px] font-semibold text-[#161823]">{title}</div>
      {children}
    </section>
  );
}

export default function WorkflowScheduleConfigPage() {
  const location = useLocation();
  const routeParams = useParams<{ id?: string }>();
  const pageRootRef = useRef<HTMLDivElement>(null);
  const locatingSectionRef = useRef<SectionKey | null>(null);
  const locateTimerRef = useRef<number>();
  const [form] = Form.useForm<FormValues>();

  const workflowId = useMemo(
    () =>
      routeParams.id ||
      new URLSearchParams(location.search).get('workflowId') ||
      '',
    [location.search, routeParams.id],
  );

  const [workflow, setWorkflow] = useState<WorkflowDefinition>();
  const [schedules, setSchedules] = useState<WorkflowSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeSection, setActiveSection] = useState<SectionKey>('schedule-basic');
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerBackfill, setLedgerBackfill] = useState<WorkflowBackfill>();
  const [backfillOpen, setBackfillOpen] = useState(false);
  const [backfillHistoryOpen, setBackfillHistoryOpen] = useState(false);

  const primarySchedule = useMemo(
    () =>
      [...schedules].sort((left, right) =>
        String(left.createTime || '').localeCompare(String(right.createTime || '')),
      )[0],
    [schedules],
  );

  const canEdit = Boolean(
    workflow && workflow.status !== 'ONLINE' && primarySchedule?.status !== 'ONLINE',
  );

  const load = useCallback(async (silent = false) => {
    if (!workflowId) return;
    if (!silent) setLoading(true);
    try {
      const [workflowData, scheduleData] = await Promise.all([
        getWorkflowDefinition(workflowId),
        listWorkflowSchedules({ workflowId }),
      ]);
      setWorkflow(workflowData);
      setSchedules(scheduleData || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '调度配置加载失败');
    } finally {
      if (!silent) setLoading(false);
    }
  }, [workflowId]);

  useEffect(() => {
    if (!workflowId) {
      history.replace('/workflow/definitions');
      return;
    }
    void load();
  }, [load, workflowId]);

  useEffect(() => {
    if (!workflow) return;
    form.setFieldsValue({
      name: primarySchedule?.name || `${workflow.name} 调度`,
      cronExpression: primarySchedule?.cronExpression || '0 0 2 * * ?',
      timezone: primarySchedule?.timezone || 'Asia/Shanghai',
      effectiveRange:
        primarySchedule?.startTime && primarySchedule?.endTime
          ? [dayjs(primarySchedule.startTime), dayjs(primarySchedule.endTime)]
          : undefined,
      executionStrategy: primarySchedule?.executionStrategy || 'SERIAL_WAIT',
      misfireStrategy: primarySchedule?.misfireStrategy || 'FIRE_ONCE',
      inputJson: JSON.stringify(primarySchedule?.input || {}, null, 2),
    });
  }, [form, primarySchedule, workflow]);

  const updateActiveSection = useCallback(() => {
    const container = pageRootRef.current;
    if (!container || locatingSectionRef.current) return;

    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    if (maxScrollTop - container.scrollTop <= SCROLL_BOTTOM_THRESHOLD) {
      setActiveSection(LAST_SECTION_KEY);
      return;
    }

    const threshold = container.getBoundingClientRect().top + 140;
    let nextActive: SectionKey = SECTION_ITEMS[0].key;
    SECTION_ITEMS.forEach((item) => {
      const element = document.getElementById(item.key);
      if (element && element.getBoundingClientRect().top <= threshold) {
        nextActive = item.key;
      }
    });
    setActiveSection(nextActive);
  }, []);

  useEffect(() => {
    const container = pageRootRef.current;
    if (!container || !workflow) return undefined;

    let animationFrameId = 0;
    const handleViewportChange = () => {
      window.cancelAnimationFrame(animationFrameId);
      animationFrameId = window.requestAnimationFrame(updateActiveSection);
    };

    container.addEventListener('scroll', handleViewportChange, { passive: true });
    window.addEventListener('resize', handleViewportChange);
    updateActiveSection();

    return () => {
      window.cancelAnimationFrame(animationFrameId);
      container.removeEventListener('scroll', handleViewportChange);
      window.removeEventListener('resize', handleViewportChange);
    };
  }, [updateActiveSection, workflow]);

  useEffect(
    () => () => {
      if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
    },
    [],
  );

  const handleSectionLocate = (key: SectionKey) => {
    const container = pageRootRef.current;
    const element = document.getElementById(key);
    if (!container || !element) return;

    if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
    locatingSectionRef.current = key;
    setActiveSection(key);

    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    const expectedTop =
      container.scrollTop + elementRect.top - containerRect.top - SECTION_TOP_OFFSET;
    const nextScrollTop =
      key === LAST_SECTION_KEY
        ? maxScrollTop
        : Math.min(Math.max(expectedTop, 0), maxScrollTop);

    container.scrollTo({ top: nextScrollTop, behavior: 'smooth' });
    locateTimerRef.current = window.setTimeout(() => {
      locatingSectionRef.current = null;
      updateActiveSection();
    }, LOCATE_LOCK_DURATION);
  };

  const handleSave = async () => {
    if (!workflow || !canEdit) return;
    try {
      const values = await form.validateFields();
      let input: Record<string, unknown> = {};
      try {
        input = values.inputJson.trim() ? JSON.parse(values.inputJson) : {};
      } catch {
        message.error('调度运行参数必须是合法 JSON');
        return;
      }

      const payload = {
        name: values.name.trim(),
        cronExpression: values.cronExpression.trim(),
        timezone: values.timezone,
        startTime: values.effectiveRange?.[0]?.toISOString(),
        endTime: values.effectiveRange?.[1]?.toISOString(),
        executionStrategy: values.executionStrategy,
        misfireStrategy: values.misfireStrategy,
        input,
      };

      setSaving(true);
      if (primarySchedule) await updateWorkflowSchedule(primarySchedule.id, payload);
      else await createWorkflowSchedule(workflow.id, payload);
      message.success('调度配置已保存，上线工作流后将自动启用');
      await load(true);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error instanceof Error ? error.message : '保存调度配置失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = () => {
    if (!primarySchedule || !canEdit) return;
    Modal.confirm({
      centered: true,
      title: '删除调度配置',
      content: '删除后不影响工作流和历史运行记录。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      async onOk() {
        try {
          await deleteWorkflowSchedule(primarySchedule.id);
          message.success('调度配置已删除');
          await load(true);
        } catch (error) {
          message.error(error instanceof Error ? error.message : '删除调度配置失败');
        }
      },
    });
  };

  const openBackfillLedger = (backfill: WorkflowBackfill) => {
    setLedgerBackfill(backfill);
    setBackfillHistoryOpen(false);
    setLedgerOpen(true);
  };

  if (!workflowId) return null;

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Spin size="large" />
      </div>
    );
  }

  if (!workflow) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Empty description="未找到工作流" image={Empty.PRESENTED_IMAGE_SIMPLE}>
          <Button onClick={() => history.push('/workflow/definitions')}>返回工作流定义</Button>
        </Empty>
      </div>
    );
  }

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div ref={pageRootRef} className="h-full overflow-y-auto overscroll-contain scroll-smooth">
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-1 gap-6 px-6 pb-6 pt-6 max-xl:max-w-[1040px] xl:grid-cols-[minmax(0,1fr)_160px]">
            <div className="min-w-0">
              <main className="space-y-4">
                <div className="rounded-xl bg-white px-6 py-4">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex min-w-0 items-center gap-3">
                      <Button
                        type="text"
                        size="small"
                        icon={<ArrowLeft size={16} />}
                        className="!h-8 !w-8 !shrink-0 !px-0"
                        onClick={() => history.push('/workflow/definitions')}
                      />
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h1 className="m-0 truncate text-[18px] font-semibold leading-8 text-[#161823]">
                            调度配置
                          </h1>
                          <span className="rounded-md bg-[#f2f4f7] px-2 py-1 text-[11px] font-medium text-[#667085]">
                            {WORKFLOW_STATUS_LABEL[workflow.status] || workflow.status}
                          </span>
                        </div>
                        <div className="truncate text-[12px] text-[#667085]">
                          {workflow.name}
                        </div>
                      </div>
                    </div>

                    {primarySchedule ? (
                      <div className="flex shrink-0 items-center gap-1.5">
                        <Tooltip title={workflow.status !== 'ONLINE' ? '工作流上线后才能执行历史补数' : undefined}>
                          <span>
                            <Button
                              size="small"
                              disabled={workflow.status !== 'ONLINE'}
                              icon={<DatabaseBackup size={14} />}
                              onClick={() => setBackfillOpen(true)}
                            >
                              补数
                            </Button>
                          </span>
                        </Tooltip>
                        <Button
                          size="small"
                          icon={<ListTree size={14} />}
                          onClick={() => {
                            setLedgerBackfill(undefined);
                            setLedgerOpen(true);
                          }}
                        >
                          触发记录
                        </Button>
                        <Button
                          size="small"
                          icon={<History size={14} />}
                          onClick={() => setBackfillHistoryOpen(true)}
                        >
                          补数记录
                        </Button>
                      </div>
                    ) : null}
                  </div>
                </div>

                {schedules.length > 1 ? (
                  <div className="rounded-lg bg-[#fffaeb] px-4 py-2.5 text-[12px] text-[#7a2e0e]">
                    存在 {schedules.length} 个历史调度，本页编辑主调度。
                  </div>
                ) : null}

                {!canEdit ? (
                  <div className="rounded-lg bg-[#f2f4f7] px-4 py-2.5 text-[12px] text-[#667085]">
                    当前工作流已启用，请下线后修改调度。
                  </div>
                ) : null}

                <Form form={form} layout="vertical" requiredMark="optional" disabled={!canEdit}>
                  <div className="space-y-4">
                    <SectionCard id="schedule-basic" title="调度设置">
                      <Form.Item
                        name="name"
                        label="调度名称"
                        rules={[
                          { required: true, message: '请输入调度名称' },
                          { max: 100, message: '名称不能超过 100 个字符' },
                        ]}
                      >
                        <Input variant="filled" placeholder="例如：每日凌晨订单同步" />
                      </Form.Item>

                      <Form.Item
                        name="cronExpression"
                        label="Cron 表达式"
                        rules={[{ required: true, message: '请输入 Cron 表达式' }]}
                      >
                        <Input variant="filled" placeholder="0 0 2 * * ?" className="font-mono" />
                      </Form.Item>

                      <div className="-mt-3 mb-5 flex flex-wrap items-center gap-1.5">
                        <span className="mr-1 text-[11px] text-[#98a2b3]">快捷设置</span>
                        {CRON_PRESETS.map((preset) => (
                          <Button
                            key={preset.value}
                            size="small"
                            type="text"
                            className="!h-7 !bg-[#f7f8fa] !px-2.5 !text-[11px] !text-[#667085] hover:!bg-[#eef0f3]"
                            onClick={() => form.setFieldValue('cronExpression', preset.value)}
                          >
                            {preset.label}
                          </Button>
                        ))}
                      </div>

                      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                        <Form.Item name="timezone" label="时区" rules={[{ required: true }]}>
                          <Select
                            options={[
                              { value: 'Asia/Shanghai', label: 'Asia/Shanghai' },
                              { value: 'Asia/Tokyo', label: 'Asia/Tokyo' },
                              { value: 'UTC', label: 'UTC' },
                            ]}
                          />
                        </Form.Item>
                        <Form.Item name="effectiveRange" label="生效区间（可选）">
                          <DatePicker.RangePicker showTime className="w-full" />
                        </Form.Item>
                      </div>
                    </SectionCard>

                    <SectionCard id="schedule-strategy" title="运行策略">
                      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                        <Form.Item
                          name="executionStrategy"
                          label="实例并发策略"
                          rules={[{ required: true }]}
                        >
                          <Select
                            options={[
                              { value: 'SERIAL_WAIT', label: '等待上一次完成（推荐）' },
                              { value: 'SERIAL_DISCARD', label: '上一次未完成则跳过' },
                              { value: 'PARALLEL', label: '允许并行运行' },
                            ]}
                          />
                        </Form.Item>
                        <Form.Item
                          name="misfireStrategy"
                          label="错过调度策略"
                          rules={[{ required: true }]}
                        >
                          <Select
                            options={[
                              { value: 'FIRE_ONCE', label: '恢复后补跑一次（推荐）' },
                              { value: 'SKIP', label: '直接跳过' },
                            ]}
                          />
                        </Form.Item>
                      </div>
                    </SectionCard>

                    <SectionCard id="schedule-advanced" title="高级配置">
                      <Form.Item name="inputJson" label="调度运行参数 JSON" className="mb-4">
                        <Input.TextArea
                          rows={7}
                          spellCheck={false}
                          className="font-mono text-[12px]"
                        />
                      </Form.Item>

                      {primarySchedule && canEdit ? (
                        <div className="flex justify-end">
                          <Button
                            danger
                            type="text"
                            icon={<Trash2 size={14} />}
                            onClick={handleDelete}
                          >
                            删除调度
                          </Button>
                        </div>
                      ) : null}
                    </SectionCard>
                  </div>
                </Form>
              </main>

              <footer className="sticky bottom-0 z-50 mt-4 rounded-xl bg-white px-6 py-3">
                <div className="flex items-center gap-3">
                  <Button
                    type="primary"
                    loading={saving}
                    disabled={!canEdit}
                    icon={<Save size={15} />}
                    className="!h-9 !min-w-[112px] !rounded-lg !px-5 !font-medium !text-white"
                    onClick={() => void handleSave()}
                  >
                    保存配置
                  </Button>
                  <Button
                    disabled={saving}
                    className="!h-9 !min-w-[96px] !rounded-lg !border-0 !bg-[#f2f3f5] !px-5 !font-medium !text-[#344054] hover:!bg-[#e9eaec]"
                    onClick={() => history.push('/workflow/definitions')}
                  >
                    返回
                  </Button>
                </div>
              </footer>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator activeKey={activeSection} onSelect={handleSectionLocate} />
              </div>
            </aside>
          </div>
        </div>
      </div>

      <BackfillDrawer
        open={backfillOpen}
        schedule={primarySchedule}
        onClose={() => setBackfillOpen(false)}
        onCreated={() => load(true)}
      />
      <BackfillHistoryDrawer
        open={backfillHistoryOpen}
        workflowId={workflow.id}
        scheduleId={primarySchedule?.id}
        onClose={() => setBackfillHistoryOpen(false)}
        onOpenTriggers={openBackfillLedger}
      />
      <TriggerLedgerDrawer
        open={ledgerOpen}
        schedule={primarySchedule}
        backfillId={ledgerBackfill?.id}
        backfillName={ledgerBackfill?.name}
        onClose={() => {
          setLedgerOpen(false);
          setLedgerBackfill(undefined);
        }}
      />
    </ConfigProvider>
  );
}
