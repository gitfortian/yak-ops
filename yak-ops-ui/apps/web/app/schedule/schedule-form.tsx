import {
  Button,
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
import type { ScheduleTimeRule } from "@/service/schedule";
import { formatScheduleTime } from "./schedule-presentation";
import { useSchedulePreview } from "./use-schedule-preview";

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

function SchedulePreview({ rule }: { rule: ScheduleTimeRule }) {
  const preview = useSchedulePreview(rule.cronExpression, rule.timeZone);
  return (
    <div aria-live="polite">
      <div className="flex flex-wrap justify-between gap-2 text-xs text-[#667085]">
        <span className="font-medium">未来 5 次触发时间</span>
        <span>{rule.timeZone}</span>
      </div>
      {preview.status === "ready" && preview.times.length > 0 ? (
        <div className="mt-2 space-y-1.5 font-mono text-xs text-[#667085]">
          {preview.times.map((time) => (
            <div key={time}>{formatScheduleTime(time, rule.timeZone)}</div>
          ))}
        </div>
      ) : (
        <div className="mt-2 flex items-center gap-2 text-xs text-[#667085]">
          <span>
            {preview.status === "loading"
              ? "正在计算…"
              : preview.status === "invalid"
                ? "Cron 或时区无效"
                : preview.status === "error"
                  ? "预览请求失败"
                  : preview.status === "empty"
                    ? "请输入 Cron"
                    : "暂无未来触发时间"}
          </span>
          {preview.status === "error" ? (
            <Button size="small" variant="ghost" onClick={preview.retry}>
              重试
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}

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
  const zones = [...new Set([...TIME_ZONES, value.timeZone])].filter(Boolean);
  return (
    <div className="space-y-3">
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>Cron 表达式</FieldLabel>
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
      </Field>
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
