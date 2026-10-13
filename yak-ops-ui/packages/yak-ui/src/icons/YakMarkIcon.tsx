import type { SVGProps } from "react";

export interface YakMarkIconProps extends SVGProps<SVGSVGElement> {
  /** Rendered width and height, overridable by SVG attributes or CSS. */
  size?: number | string;
}

/** A monochrome yak mark with swept horns, a wool fringe and a tapered beard. */
export function YakMarkIcon({ size = 24, ...props }: YakMarkIconProps) {
  const hasAccessibleName = Boolean(props["aria-label"] || props["aria-labelledby"]);

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      stroke="currentColor"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
      role={hasAccessibleName ? "img" : undefined}
      aria-hidden={hasAccessibleName ? undefined : true}
      focusable="false"
      {...props}
    >
      <path d="M20 25C10 25 5 18 7 8C9 16 14 19 22 18" />
      <path d="M44 25C54 25 59 18 57 8C55 16 50 19 42 18" />
      <path d="M22 18L26 20L29 17L32 21L35 17L38 20L42 18C46 25 46 34 42 42L38 46L32 55L26 46L22 42C18 34 18 25 22 18Z" />
      <path d="M25 31H26M38 31H39" />
      <path d="M27 39L29 40M37 39L35 40" />
    </svg>
  );
}
