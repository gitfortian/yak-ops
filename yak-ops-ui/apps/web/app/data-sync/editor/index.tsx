import { Alert, Button, PageHeader, Modal, toast } from "@yak-ops/yak-ui";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { clearTaskDraft, taskDraftKey, writeTaskDraft } from "@/service/data-sync/draft";
import { offlineIncremental, solutionSyncType, supportsMultipleTables, validIncrementalConfig } from "@/service/data-sync/parameters";
import { EMPTY_INCREMENTAL } from "../incremental-fields";
import { findStepCombination } from "../solution-presentation";
import { useSolutions } from "../use-solutions";
import { validTableMappings } from "../table-mapping-editor";
import { EditorAnchorStepper, type EditorAnchorItem } from "../editor-anchor-stepper";
import { EMPTY_SCHEDULE, sameScheduleRule } from "@/app/schedule/schedule-presentation";
import { createSchedule, getTaskSchedule, previewSchedule, scheduleErrorMessage, updateSchedule, type ScheduleRecord, type ScheduleTimeRule } from "@/service/schedule";
import { listDataSources, type DataSourceRecord } from "@/service/datasource";
import { createDataSyncTask, publishDataSyncTask, updateDataSyncTask, type DataSyncTaskSavePayload, type DataSyncTableMapping } from "@/service/data-sync";
import { BasicSection } from "./basic-section";
import { DataSourcesSection } from "./data-sources-section";
import { SourceSection } from "./source-section";
import { TargetSection } from "./target-section";
import { EditorScheduleSection } from "./schedule-section";
import { useCatalogOptions } from "./use-catalog-options";
import { selectedTableKey } from "./table-section";
import { EMPTY_FORM, type TaskEditorInput, type EditorForm } from "./types";

export function TaskEditor({ draft, task: initialTask, connections = [] }: TaskEditorInput) {
  const batch = supportsMultipleTables(draft.solutionType);
  const [mappings, setMappings] = useState<DataSyncTableMapping[]>(
    initialTask?.tableMappings || draft.form?.tableMappings || [],
  );
  const [pendingConnection, setPendingConnection] = useState<{
    side: "source" | "target";
    value: string;
  }>();
  const localScroll = true;
  const basePath = "/data-sync";
  const { id } = useParams<{ id: string }>();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const draftKey = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const {
    choices,
    loading: solutionsLoading,
    error: solutionsError,
    refresh: reloadSolutions,
  } = useSolutions(draft.sourceType, draft.targetType);
  const solution = choices.find((value) => value.solutionType === draft.solutionType);
  const combination = findStepCombination(solution, draft.steps);
  const supportsScheduling = Boolean(combination?.supportsScheduling);
  const prepareSchema = draft.steps.includes("SCHEMA");
  const useIncremental = offlineIncremental(draft.solutionType, draft.steps);
  const initialLoad = draft.steps.includes("FULL");
  const usesChangelog =
    draft.steps.includes("INCREMENTAL") && solutionSyncType(draft.solutionType) === "REALTIME";

  const [form, setForm] = useState<EditorForm>(() => {
    const value = initialTask || draft.form;
    return {
      ...EMPTY_FORM,
      name: draft.name,
      remark: value?.remark || "",
      debug: value?.debug ?? false,
      schemaChangesEnabled: value?.schemaChangesEnabled ?? draft.targetType !== "ORACLE",
      allowDropColumns: value?.allowDropColumns ?? false,
      writeMode:
        value?.writeMode === "UPSERT" || usesChangelog || useIncremental ? "UPSERT" : "APPEND",
      incremental: useIncremental ? value?.incremental || EMPTY_INCREMENTAL : undefined,
      sourceDataSourceId: value?.sourceDataSourceId || "",
      sourceDatabase: value?.sourceDatabase || "",
      sourceSchema: value?.sourceSchema || "",
      sourceTable: value?.sourceTable || "",
      targetDataSourceId: value?.targetDataSourceId || "",
      targetDatabase: value?.targetDatabase || "",
      targetSchema: value?.targetSchema || "",
      targetTable: value?.targetTable || "",
    };
  });
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>(connections);
  const [dataSourcesLoading, setDataSourcesLoading] = useState(false);
  const [taskStatus] = useState<DataSyncTaskStatus>(
    initialTask?.status === "PUBLISHED" ? "PUBLISHED" : "UNPUBLISHED",
  );
  const [saving, setSaving] = useState(false);
  const [parametersDirty, setParametersDirty] = useState(false);
  const [taskDirty, setTaskDirty] = useState(!editing);
  const savedTask = useRef(initialTask);
  const saveSession = useRef<AbortController>();
  const pendingSchedule = location.state as {
    scheduleDraft?: ScheduleTimeRule;
    scheduleSaveError?: string;
  } | null;
  const [schedule, setSchedule] = useState<ScheduleRecord | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleTimeRule>(
    pendingSchedule?.scheduleDraft || { ...EMPTY_SCHEDULE },
  );
  const [scheduleLoading, setScheduleLoading] = useState(
    Boolean(initialTask && initialTask.syncType === "OFFLINE"),
  );
  const [scheduleError, setScheduleError] = useState(pendingSchedule?.scheduleSaveError || "");
  const [scheduleNeedsReload, setScheduleNeedsReload] = useState(
    Boolean(pendingSchedule?.scheduleSaveError),
  );
  const [scheduleReload, setScheduleReload] = useState(0);
  const scheduleDraft = useRef(pendingSchedule?.scheduleDraft);

  useEffect(() => {
    const controller = new AbortController();
    saveSession.current = controller;
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!supportsScheduling || !id) return;
    const controller = new AbortController();
    setScheduleLoading(true);
    void getTaskSchedule(id, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setSchedule(value);
        if (!scheduleDraft.current) setScheduleForm(value || { ...EMPTY_SCHEDULE });
        if (scheduleReload > 0) {
          setScheduleError("");
          setScheduleNeedsReload(false);
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setScheduleError(scheduleErrorMessage(failure));
          setScheduleNeedsReload(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setScheduleLoading(false);
      });
    return () => controller.abort();
  }, [id, supportsScheduling, scheduleReload]);
  const editorScrollRef = useRef<HTMLElement | null>(null);
  const anchorItems = useMemo<EditorAnchorItem[]>(
    () => [
      { id: "basic", label: "基本信息" },
      { id: "datasource", label: "数据源" },
      { id: "source", label: "数据来源" },
      { id: "target", label: "目标表映射" },
      ...(supportsScheduling ? [{ id: "schedule", label: "调度配置" }] : []),
    ],
    [supportsScheduling],
  );

  const sourceCatalog = useCatalogOptions(
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
  );
  const targetCatalog = useCatalogOptions(
    form.targetDataSourceId,
    form.targetDatabase,
    form.targetSchema,
  );

  const sourceDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.sourceType),
    [dataSources, draft.sourceType],
  );
  const targetDataSources = useMemo(
    () => dataSources.filter((item) => item.dbType === draft.targetType),
    [dataSources, draft.targetType],
  );
  const selectedSourceDataSource = dataSources.find((item) => item.id === form.sourceDataSourceId);
  const selectedTargetDataSource = dataSources.find((item) => item.id === form.targetDataSourceId);
  const datasourceRequest = useRef<AbortController>();
  const loadDataSources = useCallback(async () => {
    datasourceRequest.current?.abort();
    const controller = new AbortController();
    datasourceRequest.current = controller;
    setDataSourcesLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 }, controller.signal);
      if (controller.signal.aborted) return;
      setDataSources((current) => {
        const selected = current.filter(
          (value) =>
            value.id === savedTask.current?.sourceDataSourceId ||
            value.id === savedTask.current?.targetDataSourceId,
        );
        return [
          ...new Map([...selected, ...result.bizData].map((value) => [value.id, value])).values(),
        ];
      });
    } catch {
      // HTTP failures are reported by the shared transport; existing selections are retained.
    } finally {
      if (!controller.signal.aborted) setDataSourcesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
    return () => datasourceRequest.current?.abort();
  }, [loadDataSources]);

  useEffect(() => {
    if (id || savedTask.current) return;
    writeTaskDraft(draftKey, {
      sourceType: draft.sourceType,
      targetType: draft.targetType,
      solutionType: draft.solutionType,
      steps: draft.steps,
      name: form.name,
      form: {
        ...form,
        solutionType: draft.solutionType,
        steps: draft.steps,
        tableMappings: batch ? mappings : undefined,
        incremental: !batch && useIncremental ? form.incremental : undefined,
      },
    });
  }, [
    id,
    draftKey,
    draft.sourceType,
    draft.targetType,
    draft.solutionType,
    draft.steps,
    form,
    batch,
    mappings,
  ]);

  const patch = <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => {
    setTaskDirty(true);
    if (key !== "name" && key !== "remark" && key !== "debug") setParametersDirty(true);
    setForm((current) => ({ ...current, [key]: value }));
  };

  const updateMappings = (value: DataSyncTableMapping[]) => {
    setMappings(value);
    setTaskDirty(true);
    setParametersDirty(true);
  };
  const changeConnection = (side: "source" | "target", value: string) => {
    setMappings([]);
    if (side === "source") {
      const selected = dataSources.find((item) => item.id === value);
      setTaskDirty(true);
      setParametersDirty(true);
      setForm((current) => ({
        ...current,
        sourceDataSourceId: value,
        sourceDatabase: selected?.database || "",
        sourceSchema: selected?.schema || "",
        sourceTable: "",
      }));
    } else {
      const selected = dataSources.find((item) => item.id === value);
      setTaskDirty(true);
      setParametersDirty(true);
      setForm((current) => ({
        ...current,
        targetDataSourceId: value,
        targetDatabase: selected?.database || "",
        targetSchema: selected?.schema || "",
        targetTable: "",
      }));
    }
  };
  const requestConnectionChange = (side: "source" | "target", value: string) => {
    if (batch && mappings.length) setPendingConnection({ side, value });
    else changeConnection(side, value);
  };

  const payload = (): DataSyncTaskSavePayload => {
    const common = {
      name: form.name.trim(),
      debug: form.debug,
      sourceDataSourceId: form.sourceDataSourceId,
      sourceDatabase: form.sourceDatabase || undefined,
      sourceSchema: form.sourceSchema || undefined,
      sourceTable: form.sourceTable,
      targetDataSourceId: form.targetDataSourceId,
      targetDatabase: form.targetDatabase || undefined,
      targetSchema: form.targetSchema || undefined,
      targetTable: form.targetTable,
      remark: form.remark.trim() || undefined,
    };
    return {
      ...common,
      solutionType: draft.solutionType,
      steps: draft.steps,
      writeMode: form.writeMode,
      ...(usesChangelog
        ? {
            schemaChangesEnabled: form.schemaChangesEnabled,
            allowDropColumns: form.schemaChangesEnabled && form.allowDropColumns,
          }
        : {}),
      tableMappings: batch ? mappings : undefined,
    };
  };

  const published = editing && taskStatus === "PUBLISHED";
  const canSave =
    !published &&
    Boolean(combination) &&
    !solutionsLoading &&
    !solutionsError &&
    combination!.writeModes.includes(form.writeMode) &&
    (!useIncremental ||
      (batch
        ? mappings.every((mapping) => validIncrementalConfig(mapping.incremental, !initialLoad))
        : validIncrementalConfig(form.incremental, !initialLoad))) &&
    selectedSourceDataSource?.dbType === draft.sourceType &&
    selectedTargetDataSource?.dbType === draft.targetType &&
    (!supportsScheduling ||
      (!scheduleLoading &&
        !scheduleNeedsReload &&
        (!schedule?.enabled || sameScheduleRule(scheduleForm, schedule)))) &&
    form.name.trim() &&
    Boolean(form.sourceDataSourceId && form.targetDataSourceId) &&
    (!prepareSchema ||
      Boolean(form.targetDatabase && (draft.targetType !== "POSTGRE_SQL" || form.targetSchema))) &&
    (batch
      ? validTableMappings(
          mappings,
          targetCatalog.tables,
          combination?.writeModes || [],
          prepareSchema,
        )
      : Boolean(
          form.sourceTable &&
          (prepareSchema
            ? Boolean(form.targetTable.trim())
            : selectedTableKey(
                targetCatalog.tables,
                form.targetDatabase,
                form.targetSchema,
                form.targetTable,
              )),
        )) &&
    !sourceCatalog.loading &&
    !targetCatalog.loading &&
    !sourceCatalog.error &&
    !targetCatalog.error;

  const save = async (publishAfterSave = false) => {
    const signal = saveSession.current?.signal;
    if (!canSave || saving || !signal || signal.aborted) return;
    setSaving(true);
    try {
      const ruleChanged =
        supportsScheduling && !sameScheduleRule(scheduleForm, schedule || EMPTY_SCHEDULE);
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          await previewSchedule(scheduleForm, signal);
        } catch (failure) {
          if (!signal.aborted) setScheduleError(scheduleErrorMessage(failure));
          return;
        }
      }
      if (signal.aborted) return;
      const request = payload();
      const metadataRequest = { name: request.name, remark: request.remark, debug: request.debug };
      let saved = savedTask.current;
      // A completed Task write is retained across a later Schedule failure.
      if (!saved || taskDirty) {
        const savedId = id || saved?.id;
        saved = savedId
          ? await updateDataSyncTask(savedId, parametersDirty ? request : metadataRequest, signal)
          : await createDataSyncTask(request, signal);
        if (signal.aborted) return;
        savedTask.current = saved;
        clearTaskDraft(draftKey);
        setTaskDirty(false);
        setParametersDirty(false);
      }
      if (ruleChanged && scheduleForm.cronExpression.trim()) {
        try {
          const value = schedule
            ? await updateSchedule(schedule.id, schedule.revision, scheduleForm, signal)
            : await createSchedule(saved.id, scheduleForm, signal);
          if (signal.aborted) return;
          setSchedule(value);
          setScheduleForm(value);
          scheduleDraft.current = undefined;
          setScheduleError("");
        } catch (failure) {
          if (signal.aborted) return;
          const message = "任务已保存，调度未确认保存。" + scheduleErrorMessage(failure);
          setScheduleError(message);
          setScheduleNeedsReload(true);
          scheduleDraft.current = scheduleForm;
          if (!id)
            navigate(`${basePath}/${saved.id}/edit`, {
              replace: true,
              state: { scheduleDraft: scheduleForm, scheduleSaveError: message },
            });
          return;
        }
      }
      if (signal.aborted) return;
      if (publishAfterSave) {
        try {
          await publishDataSyncTask(saved.id, signal, true);
        } catch (failure) {
          if (!signal.aborted) {
            const reason = failure instanceof Error ? failure.message : "请检查同步配置";
            toast.error(`任务已保存，仍处于下线状态。上线失败：${reason}`);
            navigate(`${basePath}/${saved.id}/edit`, { replace: true });
          }
          return;
        }
        if (signal.aborted) return;
        toast.success("同步任务已保存并上线");
        navigate(basePath, { replace: true });
        return;
      }
      toast.success(editing ? "同步任务已保存，当前仍为已下线" : "同步任务已创建，当前为已下线");
      navigate(`${basePath}/${saved.id}/edit`, { replace: true, state: null });
    } catch {
      // Task request errors are reported by the existing transport; drafts remain editable.
    } finally {
      if (!signal.aborted) setSaving(false);
    }
  };

  const pageTitle = editing ? form.name || "编辑同步任务" : "新建同步任务";

  if (published) {
    return (
      <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
        <PageHeader
          title={pageTitle}
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button size="small" onClick={() => navigate(basePath)}>
              返回任务列表
            </Button>
          }
        />
        <div className="px-6 pt-5 max-md:px-4">
          <Alert>任务已上线，当前不可编辑。请先在任务列表下线，再修改任务定义。</Alert>
        </div>
      </div>
    );
  }

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-[#f6f6f6] text-[#242731]"
          : "min-h-full bg-[#f6f6f6] text-[#242731]"
      }
    >
      <PageHeader
        title={pageTitle}
        bordered
        className={localScroll ? "shrink-0 bg-white px-6 max-md:px-4" : "bg-white px-6 max-md:px-4"}
        extra={
          <>
            <Button size="small" disabled={saving} onClick={() => navigate(basePath)}>
              取消
            </Button>
            <Button size="small" loading={saving} disabled={!canSave} onClick={() => void save()}>
              保存
            </Button>
            <Button
              size="small"
              variant="primary"
              loading={saving}
              disabled={!canSave}
              onClick={() => void save(true)}
            >
              保存并上线
            </Button>
          </>
        }
      />

      <div
        className={
          localScroll
            ? "flex min-h-0 flex-1 gap-5 px-6 max-md:px-4"
            : "flex gap-5 px-6 pb-8 pt-5 max-md:px-4"
        }
      >
        <main
          ref={editorScrollRef}
          className={
            localScroll
              ? "min-h-0 min-w-0 flex-1 space-y-4 overflow-y-auto pb-8 pt-5"
              : "min-w-0 flex-1 space-y-4"
          }
        >
          {solutionsError ? (
            <Alert>
              {solutionsError}
              <Button size="small" onClick={reloadSolutions}>
                重试
              </Button>
            </Alert>
          ) : null}
          {!solutionsLoading && !solutionsError && !combination ? (
            <Alert>当前链路不再支持所选同步方案</Alert>
          ) : null}
          {usesChangelog ? (
            <Alert>重新提交会创建新的执行实例并重新全量初始化，不会续用上次实例的检查点。</Alert>
          ) : null}

<BasicSection draft={draft} form={form} patch={patch} />

<DataSourcesSection
              id={id}
              draft={draft}
              form={form}
              sourceDataSources={sourceDataSources}
              targetDataSources={targetDataSources}
              dataSourcesLoading={dataSourcesLoading}
              loadDataSources={loadDataSources}
              requestConnectionChange={requestConnectionChange}
            />

<SourceSection
              batch={batch}
              prepareSchema={prepareSchema}
              form={form}
              sourceCatalog={sourceCatalog}
              targetCatalog={targetCatalog}
              selectedSourceDataSource={selectedSourceDataSource}
              mappings={mappings}
              updateMappings={updateMappings}
              writeMode={combination?.writeModes[0]}
              usesChangelog={usesChangelog}
              useIncremental={useIncremental}
              initialLoad={initialLoad}
              patch={patch}
              setTaskDirty={setTaskDirty}
              setParametersDirty={setParametersDirty}
              setForm={setForm}
            />

<TargetSection
              batch={batch}
              prepareSchema={prepareSchema}
              form={form}
              targetCatalog={targetCatalog}
              selectedTargetDataSource={selectedTargetDataSource}
              mappings={mappings}
              updateMappings={updateMappings}
              writeModes={combination?.writeModes || []}
              canSave={Boolean(canSave)}
              payload={payload}
              usesChangelog={usesChangelog}
              useIncremental={useIncremental}
              initialLoad={initialLoad}
              published={published}
              saving={saving}
              patch={patch}
              setTaskDirty={setTaskDirty}
              setParametersDirty={setParametersDirty}
              setForm={setForm}
            />
{supportsScheduling ? (
            <EditorScheduleSection
              schedule={schedule}
              scheduleForm={scheduleForm}
              scheduleError={scheduleError}
              scheduleNeedsReload={scheduleNeedsReload}
              scheduleLoading={scheduleLoading}
              saving={saving}
              onReload={() => {
                scheduleDraft.current = scheduleForm;
                setScheduleReload((value) => value + 1);
              }}
              onChange={(value) => {
                setScheduleForm(value);
                scheduleDraft.current = value;
                setScheduleError("");
              }}
            />
          ) : null}
        </main>

        <aside
          className={
            localScroll
              ? "hidden h-fit w-44 shrink-0 self-start pt-5 lg:block"
              : "sticky top-4 hidden h-fit w-44 shrink-0 self-start lg:block"
          }
        >
          <EditorAnchorStepper
            items={anchorItems}
            scrollRootRef={localScroll ? editorScrollRef : undefined}
          />
        </aside>
      </div>
      {pendingConnection ? (
        <Modal
          open
          centered
          width={420}
          title="更换数据源"
          onClose={() => setPendingConnection(undefined)}
          footer={
            <>
              <Button size="small" onClick={() => setPendingConnection(undefined)}>
                取消
              </Button>
              <Button
                size="small"
                variant="primary"
                onClick={() => {
                  changeConnection(pendingConnection.side, pendingConnection.value);
                  setPendingConnection(undefined);
                }}
              >
                确认
              </Button>
            </>
          }
        >
          <p className="text-sm">更换数据源将清除已选择的表映射。</p>
        </Modal>
      ) : null}
    </div>
  );
}
