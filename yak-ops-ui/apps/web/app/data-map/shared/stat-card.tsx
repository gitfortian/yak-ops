import type { ReactNode } from "react";

/** Visual weight of the delta line under a KPI value. */
export type StatTone = "up" | "down" | "flat";

const TONE_CLASS: Record<StatTone, string> = {
  up: "text-[var(--yak-components-badge-success-text)]",
  down: "text-[var(--yak-components-badge-danger-text)]",
  flat: "text-[var(--yak-components-muted-text)]",
};

interface Props {
  icon: ReactNode;
  /** Tinted icon-square background class, e.g. the primary-subtle surface. */
  iconClassName: string;
  label: string;
  value: string;
  hint?: string;
  hintTone?: StatTone;
}

/**
 * KPI card of the explore home: a tinted icon square beside the metric, with a
 * single muted or toned hint line under the value.
 */
export default function StatCard({
  icon,
  iconClassName,
  label,
  value,
  hint,
  hintTone = "flat",
}: Props) {
  return (
    <div className="flex items-start gap-3 rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-3.5 shadow-[var(--yak-datamap-card-shadow)]">
      <span
        className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${iconClassName}`}
      >
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block text-xs text-[var(--yak-components-muted-text)]">{label}</span>
        <span className="mt-0.5 block text-[22px] font-bold leading-7 tracking-tight text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
          {value}
        </span>
        {hint && (
          <span className={`mt-0.5 block truncate text-[11px] ${TONE_CLASS[hintTone]}`}>
            {hint}
          </span>
        )}
      </span>
    </div>
  );
}
