import { Badge } from "@yak-ops/yak-ui";
import { LoaderCircle } from "lucide-react";

import type { CrawlTaskStatus } from "@/service/data-map/types";

import { RUN_STATUS_LABEL, RUN_STATUS_TONE, TASK_STATUS_LABEL } from "../../shared/labels";
import type { RunDisplayStatus } from "../../shared/labels";

const LIFECYCLE_DOT: Record<CrawlTaskStatus, string> = {
  RUNNING: "bg-[var(--yak-components-badge-success-text)]",
  OFFLINE: "bg-[var(--yak-components-muted-text)]/45",
  DRAFT: "border border-dashed border-[var(--yak-components-muted-text)]",
};

/**
 * Task lifecycle is a configuration state, so it is drawn as a dot plus text.
 *
 * <p>The run column owns the only coloured badge of the row: two badges side by side made
 * "the schedule is armed" and "an instance is running" indistinguishable at a glance.
 */
export function TaskLifecycle({ status }: { status: CrawlTaskStatus }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-xs text-[var(--yak-components-panel-text)]">
      <span
        aria-hidden="true"
        className={`size-1.5 shrink-0 rounded-full ${LIFECYCLE_DOT[status]}`}
      />
      {TASK_STATUS_LABEL[status]}
    </span>
  );
}

export function RunBadge({ status }: { status: RunDisplayStatus }) {
  return (
    <Badge tone={RUN_STATUS_TONE[status]}>
      {status === "RUNNING" && (
        <LoaderCircle
          size={10}
          aria-hidden="true"
          className="mr-1 animate-spin motion-reduce:animate-none"
        />
      )}
      {RUN_STATUS_LABEL[status]}
    </Badge>
  );
}

/** A task that has never produced an instance — not an error, just an unarmed configuration. */
export function RunNotStarted() {
  return <span className="text-xs text-[var(--yak-components-muted-text)]">未运行</span>;
}
