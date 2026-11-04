import type { ReactNode } from "react";

type AccentTone = "gold" | "purple" | "cyan";

const TONE_CLASS: Record<AccentTone, string> = {
  gold: "bg-[var(--yak-datamap-badge-gold-bg)] text-[var(--yak-datamap-badge-gold-text)] border-[var(--yak-datamap-badge-gold-border)]",
  purple:
    "bg-[var(--yak-datamap-badge-purple-bg)] text-[var(--yak-datamap-badge-purple-text)] border-[var(--yak-datamap-badge-purple-border)]",
  cyan: "bg-[var(--yak-datamap-badge-cyan-bg)] text-[var(--yak-datamap-badge-cyan-text)] border-[var(--yak-datamap-badge-cyan-border)]",
};

/** Stable hue assignment: one id always renders in the same accent colour. */
export function accentToneOf(id: string): AccentTone {
  let hash = 0;
  for (let index = 0; index < id.length; index += 1) {
    hash = (hash * 31 + id.charCodeAt(index)) >>> 0;
  }
  const tones: AccentTone[] = ["gold", "purple", "cyan"];
  return tones[hash % tones.length];
}

interface Props {
  tone: AccentTone;
  children: ReactNode;
  /** Renders an inline remove affordance, shown on hover; the tag picker uses it to unbind. */
  onRemove?: () => void;
}

/**
 * Accent badge of the data map palette, sitting beside the five semantic
 * Badge tones: gold ranks a core asset, purple marks a domain family, cyan
 * marks sensitive data.
 */
export default function AccentBadge({ tone, children, onRemove }: Props) {
  return (
    <span
      className={`group inline-flex items-center gap-0.5 whitespace-nowrap rounded-[4px] border px-[7px] py-0.5 text-[11px] font-medium ${TONE_CLASS[tone]}`}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          aria-label={`移除 ${typeof children === "string" ? children : "标签"}`}
          onClick={(event) => {
            event.stopPropagation();
            onRemove();
          }}
          className="hidden cursor-pointer rounded-[3px] px-0.5 leading-none opacity-80 hover:opacity-100 group-hover:inline-block"
        >
          ×
        </button>
      )}
    </span>
  );
}
