import { Card, CardContent } from "@yak-ops/yak-ui";
import type { DataSyncType } from "@/service/data-sync";
import type { DataSyncOperationsSummary } from "@/service/data-sync/operations";
import { operationsSummaryCards } from "./presentation";

const numberText = (value: number) => value.toLocaleString("zh-CN");

export function OperationsSummaryCards({
  summary,
  syncType,
}: {
  summary: DataSyncOperationsSummary;
  syncType: DataSyncType;
}) {
  return (
    <div className="grid grid-cols-4 gap-4 max-xl:grid-cols-2 max-md:grid-cols-1">
      {operationsSummaryCards(summary, syncType).map((metric) => (
        <Card key={metric.label}>
          <CardContent className="p-5">
            <div className="text-xs text-[var(--yak-components-muted-text)]">{metric.label}</div>
            <div className="mt-3 text-2xl font-semibold text-[var(--yak-components-panel-text)]">
              {numberText(metric.value)}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
