import {
  CronSchedulerPicker,
  Field,
  FieldDescription,
  FieldLabel,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
} from "@yak-ops/yak-ui";

import type { CrawlScheduleType } from "@/service/data-map/types";

import type { TaskDraft, TaskDraftErrors } from "./form";
import SchedulePreview from "./schedule-preview";

const MANUAL_POINTS = [
  "任务上线后，可在任务列表或运维中心手动触发一次采集。",
  "同一数据源同时只运行一个采集实例，重复触发会被拒绝。",
];

export default function ScheduleFields({
  draft,
  errors,
  onCronInvalidChange,
  onChange,
}: {
  draft: TaskDraft;
  errors: TaskDraftErrors;
  onCronInvalidChange: (invalid: boolean) => void;
  onChange: (patch: Partial<TaskDraft>) => void;
}) {
  return (
    <Tabs
      value={draft.scheduleType}
      onValueChange={(value) => onChange({ scheduleType: String(value) as CrawlScheduleType })}
    >
      <TabsList>
        <TabsTab value="MANUAL">手动执行</TabsTab>
        <TabsTab value="CRON">定时执行</TabsTab>
      </TabsList>

      <TabsPanel value="MANUAL" className="pt-4">
        <ul className="space-y-1.5 text-sm text-[var(--yak-components-muted-text)]">
          {MANUAL_POINTS.map((point) => (
            <li key={point} className="flex gap-2">
              <span aria-hidden="true" className="text-[var(--yak-color-primary)]">
                ·
              </span>
              {point}
            </li>
          ))}
        </ul>
      </TabsPanel>

      <TabsPanel value="CRON" className="pt-4">
        <Field invalid={Boolean(errors.cronExpression)}>
          <FieldLabel required>调度周期</FieldLabel>
          <CronSchedulerPicker
            value={draft.cronExpression}
            placeholder="点击配置 Cron"
            onValueChange={(cronExpression) => onChange({ cronExpression })}
            renderPanelExtra={(cronExpression) => (
              <SchedulePreview cronExpression={cronExpression} />
            )}
          />
          <FieldDescription>
            按服务器时区触发；Cron 使用六位格式（秒 分 时 日 月 周）。
          </FieldDescription>
          {errors.cronExpression && (
            <FieldDescription className="!text-[var(--yak-components-field-error)]">
              {errors.cronExpression}
            </FieldDescription>
          )}
        </Field>

        <div className="mt-4 rounded-[var(--yak-radius-control-small)] bg-[var(--yak-color-hover)] px-3 py-2.5">
          <SchedulePreview
            cronExpression={draft.cronExpression}
            onInvalidChange={onCronInvalidChange}
          />
        </div>
      </TabsPanel>
    </Tabs>
  );
}
