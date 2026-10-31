interface Props {
  value: number;
  /** Largest value of the compared set; the bar renders relative to it. */
  max: number;
}

/**
 * Heat indicator of the hot list: the visit count over a mini bar proportional
 * to the set maximum, so the ranking reads at a glance without a chart.
 */
export default function HeatBar({ value, max }: Props) {
  const percent = max > 0 ? Math.max(6, Math.round((value / max) * 100)) : 0;
  return (
    <span className="inline-flex items-center gap-2">
      <span className="h-1.5 w-14 overflow-hidden rounded-full bg-[var(--yak-datamap-progress-track)]">
        <span
          className="block h-1.5 rounded-full bg-[var(--yak-color-primary)]"
          style={{ width: `${percent}%` }}
        />
      </span>
      <b className="text-xs font-bold text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
        {value}
      </b>
    </span>
  );
}
