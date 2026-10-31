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
}

/**
 * Accent badge of the data map palette, sitting beside the five semantic
 * Badge tones: gold ranks a core asset, purple marks a domain family, cyan
 * marks sensitive data.
 */
export default function AccentBadge({ tone, children }: Props) {
  return (
    <span
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-[4px] border px-[7px] py-0.5 text-[11px] font-medium ${TONE_CLASS[tone]}`}
    >
      {children}
    </span>
  );
}
