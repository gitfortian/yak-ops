import {
  Alert,
  Button,
  Modal,
  Spinner,
  Switch,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  createSchedule,
  deleteSchedule,
  getTaskSchedule,
  previewSchedule,
  scheduleErrorMessage,
  setScheduleEnabled,
  updateSchedule,
  type ScheduleRecord,
  type ScheduleTimeRule,
} from "@/service/schedule";
import { ScheduleForm } from "./schedule-form";
import { ScheduleFireHistory } from "./schedule-fire-history";
import {
  EMPTY_SCHEDULE,
  formatScheduleTime,
  registrationText,
  sameScheduleRule,
  scheduleReasonText,
} from "./schedule-presentation";

interface Props {
  targetId: string;
  published: boolean;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
  onViewInstance: (id: string) => void;
  historyScheduleId?: string;
}

export function ScheduleDialog(props: Props) {
  const { currentWorkspace } = useWorkspace();
  // A new target, Workspace or historical ID owns a separate editing session.
  return props.open ? (
    <ScheduleDialogSession
      key={`${currentWorkspace?.id}/${props.targetId}/${props.historyScheduleId || "current"}`}
      {...props}
    />
  ) : null;
}

function ScheduleDialogSession({
  targetId,
  published,
  onClose,
  onChanged,
  onViewInstance,
  historyScheduleId,
}: Props) {
  const historyOnly = Boolean(historyScheduleId);
  const [tab, setTab] = useState(historyOnly ? "fires" : "config");
  const [schedule, setSchedule] = useState<ScheduleRecord | null>();
  const [form, setForm] = useState<ScheduleTimeRule>({ ...EMPTY_SCHEDULE });
  const [loading, setLoading] = useState(!historyOnly);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [needsReload, setNeedsReload] = useState(false);
  const [reload, setReload] = useState(0);
  const [confirmation, setConfirmation] = useState<"delete" | "discard" | "reload">();
  const session = useRef<AbortController>();
  const current = useRef({ schedule, form });
  current.current = { schedule, form };
  const dirty = !sameScheduleRule(form, schedule || EMPTY_SCHEDULE);
  useEffect(() => {
    const controller = new AbortController();
    session.current = controller;
    if (!historyOnly) {
      setLoading(true);
      void getTaskSchedule(targetId, controller.signal)
        .then((value) => {
          if (controller.signal.aborted) return;
          setSchedule(value);
          setForm(
            value
              ? { cronExpression: value.cronExpression, timeZone: value.timeZone }
              : { ...EMPTY_SCHEDULE },
          );
          setError("");
          setNeedsReload(false);
        })
        .catch((failure) => {
          if (!controller.signal.aborted) {
            setError(scheduleErrorMessage(failure));
            setNeedsReload(true);
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }
    return () => controller.abort();
  }, [targetId, historyOnly, reload]);

  useEffect(() => {
    if (historyOnly || loading || pending || needsReload || dirty) return;
    const controller = new AbortController();
    let fetching = false;
    const timer = window.setInterval(async () => {
      if (fetching || document.visibilityState !== "visible") return;
      fetching = true;
      try {
        const value = await getTaskSchedule(targetId, controller.signal);
        if (controller.signal.aborted) return;
        // Do not silently rebase an edit started while this observation was in flight.
        if (!sameScheduleRule(current.current.form, current.current.schedule || EMPTY_SCHEDULE))
          return;
        setSchedule(value);
        setForm(value || { ...EMPTY_SCHEDULE });
        setError("");
      } catch (failure) {
        if (!controller.signal.aborted) setError(scheduleErrorMessage(failure));
      } finally {
        fetching = false;
      }
    }, 5000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [historyOnly, targetId, loading, pending, needsReload, dirty]);

  const recover = async (failure: unknown, signal: AbortSignal) => {
    if (signal.aborted) return;
    setError(scheduleErrorMessage(failure));
    setNeedsReload(true);
    // A timeout may follow a committed write. Observe, but require an explicit reload before retry.
    try {
      const observed = await getTaskSchedule(targetId, signal);
      if (!signal.aborted) setSchedule(observed);
    } catch {
      /* The last observation remains unknown; writes stay blocked. */
    }
    if (!signal.aborted) onChanged();
  };

  const save = async () => {
    const signal = session.current?.signal;
    if (
      !signal ||
      signal.aborted ||
      pending ||
      needsReload ||
      loading ||
      schedule?.enabled ||
      !form.cronExpression.trim()
    )
      return;
    setPending(true);
    setError("");
    try {
      try {
        await previewSchedule(form, signal);
      } catch (failure) {
        if (!signal.aborted) setError(scheduleErrorMessage(failure));
        return;
      }
      if (signal.aborted) return;
      const saved = schedule
        ? await updateSchedule(schedule.id, schedule.revision, form, signal)
        : await createSchedule(targetId, form, signal);
      if (signal.aborted) return;
      setSchedule(saved);
      setForm({ cronExpression: saved.cronExpression, timeZone: saved.timeZone });
      toast.success("调度已保存，未启用");
      onChanged();
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };

  const toggle = async (enabled: boolean) => {
    const signal = session.current?.signal;
    if (!signal || !schedule || pending || needsReload || dirty || signal.aborted) return;
    setPending(true);
    setError("");
    try {
      const saved = await setScheduleEnabled(schedule.id, schedule.revision, enabled, signal);
      if (!signal.aborted) {
        setSchedule(saved);
        toast.success(enabled ? "调度已启用" : "调度已停止");
        onChanged();
      }
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };

  const remove = async () => {
    const signal = session.current?.signal;
    if (!signal || signal.aborted || !schedule || pending || needsReload) return;
    setPending(true);
    setConfirmation(undefined);
    setError("");
    try {
      await deleteSchedule(schedule.id, schedule.revision, signal);
      if (!signal.aborted) {
        setSchedule(null);
        setForm({ ...EMPTY_SCHEDULE });
        toast.success("调度已删除");
        onChanged();
      }
    } catch (failure) {
      await recover(failure, signal);
    } finally {
      if (!signal.aborted) setPending(false);
    }
  };
  const close = () => {
    if (pending) return;
    if (dirty && !historyOnly) setConfirmation("discard");
    else onClose();
  };
  const observation = schedule ? registrationText(schedule) : null;
  const blocked = schedule && !schedule.enabled && (!published || !schedule.schedulable);
  const fireId = historyScheduleId || schedule?.id;
  return (
    <>
      <Modal
        open
        onClose={close}
        title={historyOnly ? "触发记录" : "调度"}
        width={tab === "fires" ? 980 : 680}
        closeLabel="关闭调度"
        footer={
          tab === "config" ? (
            <>
              <div className="mr-auto">
                {schedule ? (
                  <Button
                    size="small"
                    variant="danger"
                    disabled={pending || loading || needsReload}
                    onClick={() => setConfirmation("delete")}
                  >
                    删除调度
                  </Button>
                ) : null}
              </div>
              <Button size="small" disabled={pending} onClick={close}>
                关闭
              </Button>
              <Button
                size="small"
                variant="primary"
                loading={pending}
                disabled={
                  loading ||
                  needsReload ||
                  Boolean(schedule?.enabled) ||
                  !form.cronExpression.trim() ||
                  !dirty
                }
                onClick={() => void save()}
              >
                保存
              </Button>
            </>
          ) : (
            <Button size="small" onClick={close}>
              关闭
            </Button>
          )
        }
      >
        <Tabs value={tab} onValueChange={setTab}>
          {!historyOnly ? (
            <TabsList>
              <TabsTab value="config">配置</TabsTab>
              <TabsTab value="fires" disabled={!schedule}>
                触发记录
              </TabsTab>
            </TabsList>
          ) : null}
          <TabsPanel value="config" className="space-y-4 pt-4">
            {loading ? (
              <div className="flex justify-center py-8">
                <Spinner label="加载调度" />
              </div>
            ) : (
              <>
                {error ? (
                  <Alert>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span>{error}</span>
                      <Button
                        size="small"
                        onClick={() => {
                          if (dirty) setConfirmation("reload");
                          else setReload((value) => value + 1);
                        }}
                        disabled={pending}
                      >
                        重新加载
                      </Button>
                    </div>
                  </Alert>
                ) : null}
                {schedule ? (
                  <div className="flex flex-wrap items-center gap-3 text-xs text-[#667085]">
                    <label className="flex items-center gap-2">
                      启用调度
                      <Switch
                        size="small"
                        checked={schedule.enabled}
                        disabled={pending || needsReload || dirty || Boolean(blocked)}
                        onCheckedChange={(enabled) => void toggle(enabled)}
                      />
                    </label>
                    {observation ? (
                      <span
                        role="status"
                        className={schedule.registrationStatus === "ERROR" ? "text-[#d92d20]" : ""}
                      >
                        {observation}
                      </span>
                    ) : null}
                    {blocked ? (
                      <span>
                        {published
                          ? scheduleReasonText(schedule.eligibilityReason)
                          : "请先上线任务"}
                      </span>
                    ) : null}
                  </div>
                ) : null}
                {schedule?.enabled ? (
                  <div className="text-xs text-[#667085]">请先停止调度再修改配置</div>
                ) : null}
                <ScheduleForm
                  value={form}
                  onChange={(value) => {
                    setForm(value);
                    setError("");
                  }}
                  disabled={pending || needsReload || Boolean(schedule?.enabled)}
                  allowClear={!schedule}
                />
                {schedule?.enabled && schedule.nextFireTime ? (
                  <div className="text-xs text-[#667085]">
                    下次触发：{formatScheduleTime(schedule.nextFireTime, schedule.timeZone)}
                  </div>
                ) : null}
              </>
            )}
          </TabsPanel>
          <TabsPanel value="fires" className="pt-4">
            {tab === "fires" && fireId ? (
              <ScheduleFireHistory
                key={fireId}
                scheduleId={fireId}
                onViewInstance={(id) => {
                  onClose();
                  onViewInstance(id);
                }}
              />
            ) : null}
          </TabsPanel>
        </Tabs>
      </Modal>
      <Modal
        open={Boolean(confirmation)}
        title={
          confirmation === "delete"
            ? "删除调度"
            : confirmation === "reload"
              ? "重新加载配置"
              : "放弃修改"
        }
        onClose={() => setConfirmation(undefined)}
        width={420}
        closeLabel="关闭确认"
        footer={
          <>
            <Button size="small" onClick={() => setConfirmation(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              onClick={() => {
                if (confirmation === "delete") void remove();
                else if (confirmation === "reload") {
                  setConfirmation(undefined);
                  setReload((value) => value + 1);
                } else onClose();
              }}
            >
              确认
            </Button>
          </>
        }
      >
        <p className="text-sm">
          {confirmation === "delete"
            ? "删除调度？已提交的实例不受影响。"
            : "未保存的调度修改将被丢弃。"}
        </p>
      </Modal>
    </>
  );
}
