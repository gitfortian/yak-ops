import { ArrowRight } from "lucide-react";

import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import type { DataSourceRecord } from "@/service/datasource";

interface TaskRouteProps {
  source?: DataSourceRecord;
  target?: DataSourceRecord;
}

export function TaskRoute({ source, target }: TaskRouteProps) {
  const sourceName = source?.name || "未知数据源";
  const targetName = target?.name || "未知数据源";

  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)_20px_minmax(0,1fr)] items-center gap-2 text-[13px] text-[var(--yak-components-field-label)]">
      <div className="flex min-w-0 items-center justify-end gap-1.5">
        <span className="flex h-4 w-4 shrink-0 items-center justify-center">
          <DatabaseIcons dbType={source?.dbType} width="16px" height="16px" />
        </span>
        <span className="min-w-0 truncate" title={sourceName}>
          {sourceName}
        </span>
      </div>
      <span className="flex h-5 w-5 items-center justify-center">
        <ArrowRight size={14} className="text-[var(--yak-components-muted-text)]" strokeWidth={1.8} />
      </span>
      <div className="flex min-w-0 items-center gap-1.5">
        <span className="flex h-4 w-4 shrink-0 items-center justify-center">
          <DatabaseIcons dbType={target?.dbType} width="16px" height="16px" />
        </span>
        <span className="min-w-0 truncate" title={targetName}>
          {targetName}
        </span>
      </div>
    </div>
  );
}
