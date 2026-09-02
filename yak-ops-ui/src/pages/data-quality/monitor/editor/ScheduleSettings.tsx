import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import { Select, Switch } from 'antd';
import type { ReactNode } from 'react';

import { EditorSection } from './EditorLayout';
import type { ScheduleSettingsState } from './model';

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <div className="mb-2 text-[12px] font-medium text-[#475467]">
        {label}
      </div>
      {children}
      {hint ? (
        <div className="mt-1.5 text-[11px] leading-5 text-[#98a2b3]">
          {hint}
        </div>
      ) : null}
    </div>
  );
}

export const ScheduleSettings = ({
  value,
  onChange,
  nextRunTime,
}: {
  value: ScheduleSettingsState;
  onChange: (value: ScheduleSettingsState) => void;
  nextRunTime?: string;
}) => {
  const update = (patch: Partial<ScheduleSettingsState>) =>
    onChange({ ...value, ...patch });

  return (
    <EditorSection
      id="schedule-settings"
      title="调度配置"
      extra={
        nextRunTime && value.scheduleEnabled ? (
          <span className="text-[11px] text-[#8a8f99]">
            下次运行：{nextRunTime}
          </span>
        ) : undefined
      }
    >
      <div className="grid grid-cols-[minmax(0,1.8fr)_minmax(150px,.55fr)_minmax(220px,.8fr)] gap-4 max-lg:grid-cols-2 max-sm:grid-cols-1">
        <Field
          label="Cron 表达式"
          hint="采用 Quartz Cron；点击输入框可打开可视化调度配置，也支持直接输入。"
        >
          <CronSchedulerInput
            value={value.cronExpression}
            placeholder="0 0 9 * * ?"
            status={
              value.scheduleEnabled && !value.cronExpression.trim()
                ? 'error'
                : undefined
            }
            onChange={(cronExpression) => update({ cronExpression })}
          />
        </Field>

        <Field
          label="启用调度"
          hint="关闭后保留 Cron，仍可在详情页手动运行"
        >
          <div className="flex h-8 items-center">
            <Switch
              size="small"
              checked={value.scheduleEnabled}
              onChange={(scheduleEnabled) => update({ scheduleEnabled })}
            />
          </div>
        </Field>

        <Field
          label="规则失败处理"
          hint="控制单条规则失败后是否继续本次检查"
        >
          <Select
            size="small"
            variant="filled"
            value={value.ruleFailureAction}
            options={[
              { value: 'CONTINUE', label: '继续执行剩余规则' },
              { value: 'STOP', label: '立即终止本次检查' },
            ]}
            onChange={(ruleFailureAction) => update({ ruleFailureAction })}
            className="w-full"
          />
        </Field>
      </div>
    </EditorSection>
  );
};
