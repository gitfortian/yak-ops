import type { CSSProperties } from "react";

import { cn } from "../cn";

export interface SkeletonProps {
  width?: CSSProperties["width"];
  height?: CSSProperties["height"];
  className?: string;
}

/** Occupies the same space as the content that will replace it. */
export function Skeleton({ width = "100%", height = 16, className }: SkeletonProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "block shrink-0 animate-pulse rounded bg-[var(--yak-components-skeleton-bg)] motion-reduce:animate-none",
        className,
      )}
      style={{ width, height }}
    />
  );
}
