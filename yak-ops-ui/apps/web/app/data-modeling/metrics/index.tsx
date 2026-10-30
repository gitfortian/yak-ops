import { Button, PageHeader } from "@yak-ops/yak-ui";
import { useState } from "react";
import type { MetricDeleteTarget } from "@/service/data-modeling/metrics/types";
import { METRIC_KIND_LABEL, METRIC_KIND_ORDER } from "@/service/data-modeling/metrics/types";
import AtomicSection from "./atomic-section";
import CompositeSection from "./composite-section";
import DerivedSection from "./derived-section";
import MetricDeleteDialog from "./metric-delete-dialog";
import ModifierSection from "./modifier-section";
import TimePeriodSection from "./time-period-section";

export default function MetricsPage() {
  const [kind, setKind] = useState<(typeof METRIC_KIND_ORDER)[number]>("atomic");
  const [version, setVersion] = useState(0);
  const [deleting, setDeleting] = useState<MetricDeleteTarget | null>(null);
  const changed = () => setVersion((value) => value + 1);
  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-background)]">
      <PageHeader
        title="数据指标"
        description="定义业务指标及其计算口径与来源字段。"
        className="px-6 pb-5 pt-5 max-md:px-4"
        extra={
          <Button size="small" onClick={changed}>
            刷新
          </Button>
        }
      />
      <div className="flex min-h-0 flex-1 flex-col gap-4 px-6 pb-6 max-md:px-4">
        <div
          role="tablist"
          aria-label="指标种类"
          className="flex flex-wrap items-center gap-1 border-b border-[var(--yak-color-border)]"
        >
          {METRIC_KIND_ORDER.map((item) => (
            <button
              key={item}
              type="button"
              role="tab"
              aria-selected={kind === item}
              className={
                "relative -mb-px border-b-2 px-3 py-2 text-[13px] transition-colors " +
                (kind === item
                  ? "border-[var(--yak-color-primary)] font-medium [color:var(--yak-color-primary)]"
                  : "border-transparent text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-components-field-label)]")
              }
              onClick={() => setKind(item)}
            >
              {METRIC_KIND_LABEL[item]}
            </button>
          ))}
        </div>
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] shadow-[0_1px_2px_0_rgb(16_24_40/4%)]">
          {kind === "period" && (
            <TimePeriodSection
              unavailable={false}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {kind === "modifier" && (
            <ModifierSection
              unavailable={false}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {kind === "atomic" && (
            <AtomicSection
              unavailable={false}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {kind === "derived" && (
            <DerivedSection
              unavailable={false}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
          {kind === "composite" && (
            <CompositeSection
              unavailable={false}
              version={version}
              onChanged={changed}
              onDelete={setDeleting}
            />
          )}
        </div>
      </div>
      {deleting && (
        <MetricDeleteDialog
          target={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null);
            changed();
          }}
        />
      )}
    </div>
  );
}
