const SIZES = {
  sm: "size-[18px] text-[9px]",
  md: "size-[22px] text-[10px]",
  lg: "size-[26px] text-[11px]",
} as const;

interface Props {
  name: string | null | undefined;
  size?: keyof typeof SIZES;
  /** Plain renders the muted grey variant used for secondary mentions. */
  plain?: boolean;
}

function initialOf(name: string) {
  const trimmed = name.trim();
  return trimmed.length > 0 ? trimmed[0] : "?";
}

/**
 * Initial avatar of a Workspace member: the branded gradient for accountable
 * owners, the muted plain variant for secondary mentions such as feed authors.
 */
export default function UserAvatar({ name, size = "sm", plain = false }: Props) {
  if (!name) return null;
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold leading-none text-white ${SIZES[size]} ${
        plain
          ? "bg-[var(--yak-components-collapse-section-border)] text-[var(--yak-components-panel-text)]"
          : "bg-[image:var(--yak-datamap-avatar-gradient)]"
      }`}
    >
      {initialOf(name)}
    </span>
  );
}
