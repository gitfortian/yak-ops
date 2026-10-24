import {
  CronSchedulerPicker,
  Field,
  FieldLabel,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useId } from "react";
import type { ScheduleTimeRule } from "@/service/schedule";
import { SchedulePreview } from "./schedule-preview";


const TIME_ZONES = [
  "Asia/Shanghai",
  "Asia/Hong_Kong",
  "Asia/Taipei",
  "Asia/Tokyo",
  "Asia/Seoul",
  "Asia/Singapore",
  "UTC",
  "Europe/London",
  "America/New_York",
  "America/Los_Angeles",
];

export function ScheduleForm({
  value,
  onChange,
  disabled = false,
  allowClear = true,
}: {
  value: ScheduleTimeRule;
  onChange: (rule: ScheduleTimeRule) => void;
  disabled?: boolean;
  allowClear?: boolean;
}) {
  const cronLabelId = useId();
  const zones = [...new Set([...TIME_ZONES, value.timeZone])].filter(Boolean);
  return (
    <div className="space-y-3">
      <div
        role="group"
        aria-labelledby={cronLabelId}
        className="grid grid-cols-[104px_minmax(0,1fr)] items-center gap-3"
      >
        <span
          id={cronLabelId}
          className="text-[13px] font-medium leading-5 text-[var(--yak-components-field-label)]"
        >
          Cron 表达式
        </span>
        <CronSchedulerPicker
          value={value.cronExpression}
          disabled={disabled}
          allowClear={allowClear}
          placeholder="点击配置 Cron"
          size="small"
          variant="outlined"
          onValueChange={(cronExpression) => onChange({ ...value, cronExpression })}
          renderPanelExtra={(cronExpression) => (
            <SchedulePreview rule={{ ...value, cronExpression }} />
          )}
        />
      </div>
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>时区</FieldLabel>
        <Select
          size="small"
          value={value.timeZone}
          disabled={disabled}
          items={Object.fromEntries(zones.map((zone) => [zone, zone]))}
          onValueChange={(zone) => {
            if (zone) onChange({ ...value, timeZone: String(zone) });
          }}
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {zones.map((zone) => (
              <SelectItem key={zone} value={zone}>
                <SelectItemText>{zone}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>
    </div>
  );
}
