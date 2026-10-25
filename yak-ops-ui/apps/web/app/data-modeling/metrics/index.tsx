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
    <div className="flex h-full min-h-0 flex-col">
      <PageHeader
        title="数据指标"
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <Button size="small" onClick={changed}>
            刷新
          </Button>
        }
      />
      <div className="flex min-h-0 flex-1 flex-col gap-3 px-6 pb-4 pt-5 max-md:px-4">
        <div
          role="tablist"
          aria-label="指标种类"
          className="flex flex-wrap items-center gap-1 border-b border-[var(--yak-components-panel-border)]"
        >
          {METRIC_KIND_ORDER.map((item) => (
            <button
              key={item}
              type="button"
              role="tab"
              aria-selected={kind === item}
              className={
                "relative -mb-px border-b-2 px-3 py-2 text-sm transition-colors " +
                (kind === item
                  ? "border-[var(--yak-color-primary)] font-medium [color:var(--yak-color-primary)]"
                  : "border-transparent text-[#667085] hover:text-[#344054]")
              }
              onClick={() => setKind(item)}
            >
              {METRIC_KIND_LABEL[item]}
            </button>
          ))}
        </div>
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
