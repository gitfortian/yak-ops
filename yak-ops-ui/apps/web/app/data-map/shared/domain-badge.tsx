import AccentBadge, { accentToneOf } from "./accent-badge";

interface Props {
  id: string | null;
  name?: string;
}

/**
 * Domain chip of list rows: unclassified renders as an explicit muted state,
 * classified domains take a stable accent colour derived from their id.
 */
export default function DomainBadge({ id, name }: Props) {
  if (!id) {
    return <span className="text-xs text-[var(--yak-components-muted-text)]">未分类</span>;
  }
  return <AccentBadge tone={accentToneOf(id)}>{name ?? "未命名数据域"}</AccentBadge>;
}
