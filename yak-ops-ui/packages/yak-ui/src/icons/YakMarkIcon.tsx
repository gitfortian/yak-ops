import type { SVGProps } from "react";

export interface YakMarkIconProps extends SVGProps<SVGSVGElement> {
  /** Rendered width and height, overridable by SVG attributes or CSS. */
  size?: number | string;
}

/** A compact yak silhouette with swept horns, a wool tuft and a cut-out muzzle. */
export function YakMarkIcon({ size = 24, ...props }: YakMarkIconProps) {
  const hasAccessibleName = Boolean(props["aria-label"] || props["aria-labelledby"]);

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="currentColor"
      fillRule="evenodd"
      role={hasAccessibleName ? "img" : undefined}
      aria-hidden={hasAccessibleName ? undefined : true}
      focusable="false"
      {...props}
    >
      <path d="M19 26C9 25 3 18 5 8C5.2 7 6.4 6.8 6.8 7.9C8.9 14.8 13.3 17.1 24 19Z" />
      <path d="M45 26C55 25 61 18 59 8C58.8 7 57.6 6.8 57.2 7.9C55.1 14.8 50.7 17.1 40 19Z" />
      <path d="M17 29C13 26 8 27 7 31C8 35 12 37 17 35ZM47 29C51 26 56 27 57 31C56 35 52 37 47 35Z" />
      <path
        d="M16 29C16 23 19 19 24 18C23 16 25 14.5 28 15
          C30 15.3 31 16 32 16.7C33.5 13.6 37 12.4 40 13.5
          C38.3 14.7 37.3 16 37 17.5C44 17 48 22 48 29L49 38
          C49.5 44 45 50 40 52C37.5 54 35 55.5 32 56
          C29 55.5 26.5 54 24 52C19 50 14.5 44 15 38Z
          M24 37.5C20.5 38 19 41 20 44C21.5 48.5 26 50 32 50
          C38 50 42.5 48.5 44 44C45 41 43.5 38 40 37.5
          C35 36.5 29 36.5 24 37.5Z
          M23 29A1.7 1.7 0 1 0 23 32.4A1.7 1.7 0 1 0 23 29Z
          M41 29A1.7 1.7 0 1 0 41 32.4A1.7 1.7 0 1 0 41 29Z"
      />
      <path d="M26 40.5A1.7 2 0 1 0 26 44.5A1.7 2 0 1 0 26 40.5ZM38 40.5A1.7 2 0 1 0 38 44.5A1.7 2 0 1 0 38 40.5Z" />
    </svg>
  );
}
