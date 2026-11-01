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

      <TabsPanel value="MANUAL" className="pt-4" />

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
