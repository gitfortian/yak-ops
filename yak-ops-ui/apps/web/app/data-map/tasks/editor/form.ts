import type {
  CrawlScheduleType,
  CrawlTask,
  CrawlTaskSave,
  CrawlTaskStatus,
} from "@/service/data-map/types";

export type ExcludeMode = "tables" | "pattern";

export interface TaskDraft {
  name: string;
  datasourceId: string;
  database: string;
  schema: string;
  ownerId: string;
  scheduleType: CrawlScheduleType;
  cronExpression: string;
  excludeMode: ExcludeMode;
  excludeTables: string[];
  excludePattern: string;
}

export interface TaskDraftErrors {
  name?: string;
  datasourceId?: string;
  database?: string;
  ownerId?: string;
  cronExpression?: string;
  excludeTables?: string;
  excludePattern?: string;
}

export const newDraft = (ownerId: string): TaskDraft => ({
  name: "",
  datasourceId: "",
  database: "",
  schema: "",
  ownerId,
  scheduleType: "MANUAL",
  cronExpression: "",
  excludeMode: "tables",
  excludeTables: [],
  excludePattern: "",
});

export const draftFromTask = (task: CrawlTask): TaskDraft => ({
  name: task.name,
  datasourceId: task.datasourceId,
  database: task.database,
  schema: task.schema ?? "",
  ownerId: task.ownerId,
  scheduleType: task.scheduleType,
  cronExpression: task.cronExpression ?? "",
  excludeMode: task.excludeTables.length > 0 || !task.excludePattern ? "tables" : "pattern",
  excludeTables: task.excludeTables,
  excludePattern: task.excludePattern ?? "",
});

const uniqueTrimmed = (names: string[]) => [
  ...new Set(names.map((name) => name.trim()).filter((name) => name.length > 0)),
];

export const toSaveInput = (draft: TaskDraft, status: CrawlTaskStatus): CrawlTaskSave => {
  const excludeTables = draft.excludeMode === "tables" ? uniqueTrimmed(draft.excludeTables) : [];
  const excludePattern =
    draft.excludeMode === "pattern" && draft.excludePattern.trim()
      ? draft.excludePattern.trim()
      : undefined;
  return {
    name: draft.name.trim(),
    datasourceId: draft.datasourceId,
    database: draft.database.trim(),
    schema: draft.schema.trim() || undefined,
    ownerId: draft.ownerId,
    scheduleType: draft.scheduleType,
    cronExpression:
      draft.scheduleType === "CRON" ? draft.cronExpression.trim() || undefined : undefined,
    excludeTables: excludeTables.length > 0 ? excludeTables : undefined,
    excludePattern,
    status,
  };
};

/** Configuration identity for dirty tracking; the save target status is deliberately excluded. */
export const draftSignature = (draft: TaskDraft) => JSON.stringify(toSaveInput(draft, "DRAFT"));

export function validateDraft(draft: TaskDraft): TaskDraftErrors {
  const errors: TaskDraftErrors = {};
  if (!draft.name.trim()) errors.name = "请输入任务名称";
  if (!draft.datasourceId) errors.datasourceId = "请选择数据源";
  if (!draft.database.trim()) errors.database = "请选择或输入数据库";
  if (!draft.ownerId) errors.ownerId = "请选择负责人";
  if (draft.scheduleType === "CRON" && !draft.cronExpression.trim()) {
    errors.cronExpression = "请配置调度周期";
  }
  if (draft.excludeMode === "tables") {
    const names = uniqueTrimmed(draft.excludeTables);
    if (names.length > 500) errors.excludeTables = "最多排除 500 张表";
  }
  if (draft.excludeMode === "pattern" && draft.excludePattern.trim()) {
    try {
      new RegExp(draft.excludePattern.trim());
    } catch {
      errors.excludePattern = "正则表达式无法编译，请检查语法";
    }
  }
  return errors;
}
