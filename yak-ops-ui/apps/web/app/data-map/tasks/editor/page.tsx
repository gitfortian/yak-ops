import { Alert, Badge, Button, Card, CardContent, CardHeader, toast } from "@yak-ops/yak-ui";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check } from "lucide-react";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";
import { dataMapFailureMessage } from "@/service/data-map/request";
import { createTask, getTask, updateTask } from "@/service/data-map/task";
import type { CrawlTaskStatus } from "@/service/data-map/types";

import { dataMapKeys } from "../../shared/keys";
import { DATA_MAP_BASE, TASK_STATUS_LABEL, TASK_STATUS_TONE } from "../../shared/labels";
import { useDataMapScope } from "../../shared/scope";
import BasicFields from "./basic-fields";
import ExclusionFields from "./exclusion-fields";
import { draftFromTask, draftSignature, newDraft, toSaveInput, validateDraft } from "./form";
import ScheduleFields from "./schedule-fields";

const tasksPath = `${DATA_MAP_BASE}/tasks`;

function StepSection({
  step,
  title,
  description,
  children,
}: {
  step: number;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <Card>
      <CardHeader
        title={
          <span className="flex items-center gap-3">
            <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-[var(--yak-color-primary-subtle)] text-xs font-semibold text-[var(--yak-color-primary)]">
              {step}
            </span>
            {title}
          </span>
        }
        description={description}
      />
      <CardContent>{children}</CardContent>
    </Card>
  );
}

export default function CrawlTaskEditorPage() {
  const { taskId } = useParams<{ taskId?: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const { scope, ready } = useDataMapScope();

  const [draft, setDraft] = useState(() => newDraft(currentUser?.id ?? ""));
  const [baseline, setBaseline] = useState(() => draftSignature(newDraft(currentUser?.id ?? "")));
  const [loaded, setLoaded] = useState(!taskId);
  const [cronInvalid, setCronInvalid] = useState(false);
  const [scopeBlocked, setScopeBlocked] = useState(false);
  const [showErrors, setShowErrors] = useState(false);

  const taskQuery = useQuery({
    queryKey: dataMapKeys.task(scope, taskId ?? ""),
    queryFn: ({ signal }) => getTask(taskId ?? "", signal),
    enabled: ready && Boolean(taskId),
  });
  const task = taskQuery.data;

  useEffect(() => {
    if (!taskId || loaded || !taskQuery.data) return;
    const next = draftFromTask(taskQuery.data);
    setDraft(next);
    setBaseline(draftSignature(next));
    setLoaded(true);
  }, [taskId, loaded, taskQuery.data]);

  const errors = useMemo(() => validateDraft(draft), [draft]);
  const dirty = draftSignature(draft) !== baseline;
  const cronBlocked = draft.scheduleType === "CRON" && cronInvalid;
  const canSave = Object.keys(errors).length === 0 && !cronBlocked && !scopeBlocked;

  const save = useMutation({
    mutationFn: (status: CrawlTaskStatus) => {
      const input = toSaveInput(draft, status);
      return taskId ? updateTask(taskId, input) : createTask(input);
    },
    onSuccess: (_saved, status) => {
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tasks"] });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "task"] });
      toast.success(
        status === "RUNNING"
          ? "采集任务已上线"
          : status === "DRAFT"
            ? "草稿已保存"
            : "采集任务已保存",
      );
      navigate(tasksPath, { replace: true });
    },
  });

  const change = (patch: Partial<typeof draft>) => {
    setDraft((current) => ({ ...current, ...patch }));
  };

  const back = () => navigate(tasksPath);

  /**
   * Saving keeps the lifecycle the task already had. A paused task therefore stays offline unless
   * the user picks the explicit "save and bring online" action, and a draft is only scheduled when
   * the primary action says so.
   */
  const currentStatus = task?.status;
  const primary: { status: CrawlTaskStatus; label: string } =
    currentStatus === "OFFLINE"
      ? { status: "OFFLINE", label: "保存" }
      : { status: "RUNNING", label: currentStatus === "RUNNING" ? "保存" : "保存并上线" };
  const secondary: { status: CrawlTaskStatus; label: string } | null =
    currentStatus === "OFFLINE"
      ? { status: "RUNNING", label: "保存并上线" }
      : currentStatus === "RUNNING"
        ? null
        : { status: "DRAFT", label: "保存为草稿" };

  // Cached task data may predate the latest save; render only after the mount fetch settles.
  if (taskId && (taskQuery.isPending || !taskQuery.isFetchedAfterMount)) {
    return (
      <div className="fixed inset-x-0 bottom-0 top-10 z-40 flex flex-col bg-[var(--yak-color-hover)]">
        <header className="flex h-14 shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-5">
          <Button size="small" variant="ghost" aria-label="返回任务列表" onClick={back}>
            <ArrowLeft size={16} aria-hidden="true" />
          </Button>
          <span className="ml-2 text-sm font-medium">编辑采集任务</span>
        </header>
        <div className="flex flex-1 items-center justify-center text-sm text-[var(--yak-components-muted-text)]">
          正在加载任务配置…
        </div>
      </div>
    );
  }

  if (taskId && taskQuery.isError) {
    return (
      <div className="fixed inset-x-0 bottom-0 top-10 z-40 flex flex-col bg-[var(--yak-color-hover)]">
        <header className="flex h-14 shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-5">
          <Button size="small" variant="ghost" aria-label="返回任务列表" onClick={back}>
            <ArrowLeft size={16} aria-hidden="true" />
          </Button>
          <span className="ml-2 text-sm font-medium">编辑采集任务</span>
        </header>
        <div className="mx-auto mt-16 w-full max-w-xl px-5">
          <Alert>
            {dataMapFailureMessage(taskQuery.error, "任务配置加载失败，请稍后重试。")}
            <Button size="small" className="ml-3" onClick={() => void taskQuery.refetch()}>
              重试
            </Button>
          </Alert>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-x-0 bottom-0 top-10 z-40 flex flex-col bg-[var(--yak-color-hover)]">
      <header className="flex h-14 shrink-0 items-center gap-3 border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-5 max-md:px-3">
        <Button size="small" variant="ghost" aria-label="返回任务列表" onClick={back}>
          <ArrowLeft size={16} aria-hidden="true" />
        </Button>
        <div className="flex min-w-0 items-center gap-2">
          <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">
            {draft.name || (taskId ? "编辑采集任务" : "新建采集任务")}
          </span>
          {task && (
            <Badge tone={TASK_STATUS_TONE[task.status]}>{TASK_STATUS_LABEL[task.status]}</Badge>
          )}
          {dirty && (
            <span className="hidden text-xs text-[var(--yak-components-muted-text)] sm:inline">
              有未保存的修改
            </span>
          )}
        </div>

        <div className="ml-auto flex shrink-0 items-center gap-2">
          {scopeBlocked && (
            <span className="hidden text-xs text-[var(--yak-components-badge-danger-text)] sm:inline">
              采集范围超过上限
            </span>
          )}
          <Button size="small" disabled={save.isPending} onClick={back}>
            取消
          </Button>
          {secondary && (
            <Button
              size="small"
              loading={save.isPending && save.variables === secondary.status}
              disabled={!canSave}
              onClick={() => save.mutate(secondary.status)}
            >
              {secondary.label}
            </Button>
          )}
          <Button
            size="small"
            variant="primary"
            loading={save.isPending && save.variables === primary.status}
            disabled={!canSave}
            onClick={() => save.mutate(primary.status)}
          >
            <Check size={14} aria-hidden="true" />
            {primary.label}
          </Button>
        </div>
      </header>

      <main className="min-h-0 flex-1 overflow-auto" onBlurCapture={() => setShowErrors(true)}>
        <div className="mx-auto w-full max-w-4xl px-6 py-6 max-md:px-4">
          {save.isError && (
            <Alert className="mb-4">
              {dataMapFailureMessage(save.error, "保存失败，请稍后重试。")}
              <Button size="small" className="ml-3" onClick={() => save.reset()}>
                关闭
              </Button>
            </Alert>
          )}

          <div className="space-y-4">
            <StepSection step={1} title="基本信息" description="任务身份、采集范围与负责人。">
              <BasicFields
                draft={draft}
                errors={showErrors ? errors : {}}
                onChange={change}
                onScopeBlockedChange={setScopeBlocked}
              />
            </StepSection>

            <StepSection step={2} title="调度设置" description="手动触发，或按 Cron 周期自动执行。">
              <ScheduleFields
                draft={draft}
                errors={showErrors ? errors : {}}
                onCronInvalidChange={setCronInvalid}
                onChange={change}
              />
            </StepSection>

            <StepSection
              step={3}
              title="高级设置 · 排除规则"
              description="排除不需要采集的表；被排除的表不会被采集，也不会被标记为缺失。"
            >
              <ExclusionFields draft={draft} errors={showErrors ? errors : {}} onChange={change} />
            </StepSection>
          </div>
        </div>
      </main>
    </div>
  );
}
