import { Empty } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

/**
 * Illustration for an empty catalog: a dashed asset sheet waiting for a crawl, with a magnifier
 * resting on it. Drawn with `currentColor` so it follows the surrounding text colour in both
 * themes; the accent ring uses the single primary token the rest of the app already themes.
 */
function EmptyCatalogIllustration() {
  return (
    <svg
      width="132"
      height="104"
      viewBox="0 0 132 104"
      role="presentation"
      aria-hidden="true"
      fill="none"
    >
      <rect
        x="12.5"
        y="12.5"
        width="86"
        height="70"
        rx="6"
        stroke="currentColor"
        strokeOpacity="0.35"
        strokeDasharray="5 5"
      />
      <rect
        x="24.5"
        y="26.5"
        width="62"
        height="12"
        rx="3"
        stroke="currentColor"
        strokeOpacity="0.45"
      />
      <path
        d="M25 52h34M25 62h48M25 72h26"
        stroke="currentColor"
        strokeOpacity="0.28"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <circle
        cx="90"
        cy="66"
        r="16"
        fill="var(--yak-components-table-bg)"
        stroke="currentColor"
        strokeOpacity="0.55"
        strokeWidth="2.5"
      />
      <path
        d="M101.5 78.5L112 89"
        stroke="currentColor"
        strokeOpacity="0.55"
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path
        d="M84 66h12M90 60v12"
        stroke="var(--yak-color-primary)"
        strokeOpacity="0.7"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

interface Props {
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/**
 * Empty state for the catalog surfaces.
 *
 * <p>Callers render this instead of the table when a query returns zero rows: a table that keeps
 * its header while showing a placeholder leaves an empty grid behind, which reads as a rendering
 * failure rather than as "there is nothing here yet".
 */
export default function DataMapEmptyState({ title, description, action, className }: Props) {
  return (
    <Empty
      className={["h-full min-h-[360px]", className].filter(Boolean).join(" ")}
      icon={<EmptyCatalogIllustration />}
      title={title}
      description={description}
      action={action}
    />
  );
}
