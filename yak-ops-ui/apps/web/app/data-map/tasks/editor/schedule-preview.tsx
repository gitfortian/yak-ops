import { Button } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { dataMapFailureMessage, isCronExpressionInvalid } from "@/service/data-map/request";
import { previewTaskSchedule } from "@/service/data-map/task";

import { dataMapKeys } from "../../shared/keys";
import { useDataMapScope } from "../../shared/scope";

const PREVIEW_DEBOUNCE_MS = 400;

/** Shows the future occurrences the scheduler would actually submit for this expression. */
export default function SchedulePreview({
  cronExpression,
  onInvalidChange,
}: {
  cronExpression: string;
  onInvalidChange?: (invalid: boolean) => void;
}) {
  const { scope, ready } = useDataMapScope();
  const [settled, setSettled] = useState(cronExpression);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(cronExpression), PREVIEW_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [cronExpression]);

  const normalized = settled.trim();
  const query = useQuery({
    queryKey: dataMapKeys.taskSchedulePreview(scope, normalized),
    queryFn: ({ signal }) => previewTaskSchedule(normalized, signal),
    enabled: ready && normalized.length > 0 && settled === cronExpression,
    retry: false,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });

  const invalid = query.isError && isCronExpressionInvalid(query.error);
  useEffect(() => {
    onInvalidChange?.(invalid);
  }, [invalid, onInvalidChange]);

  if (!cronExpression.trim()) {
    return (
      <div className="text-xs text-[var(--yak-components-muted-text)]">
        配置 Cron 后，这里会显示未来 3 次执行时间。
      </div>
    );
  }

  if (invalid) {
    return (
      <div className="text-xs text-[var(--yak-components-field-error)]">
        调度表达式无效，请检查执行周期。
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
        {dataMapFailureMessage(query.error, "执行时间预览失败")}
        <Button size="small" variant="ghost" onClick={() => void query.refetch()}>
          重试
        </Button>
      </div>
    );
  }

  if (settled !== cronExpression || query.isPending) {
    return <div className="text-xs text-[var(--yak-components-muted-text)]">正在计算…</div>;
  }

  const times = query.data?.nextFireTimes ?? [];
  return (
    <div aria-live="polite">
      <div className="text-xs font-medium text-[var(--yak-components-muted-text)]">
        未来 3 次执行时间
      </div>
      {times.length > 0 ? (
        <div className="mt-1.5 space-y-1 font-mono text-xs text-[var(--yak-components-panel-text)]">
          {times.map((time) => (
            <div key={time}>{time}</div>
          ))}
        </div>
      ) : (
        <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          该表达式没有后续执行时间。
        </div>
      )}
    </div>
  );
}
