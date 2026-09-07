import type { ReactNode, SVGProps } from 'react';

export type HomePrimitiveIconName =
  | 'dataset'
  | 'service'
  | 'dashboard'
  | 'dataSource'
  | 'sync'
  | 'quality'
  | 'file'
  | 'folder'
  | 'storage';

export interface HomePrimitiveIconProps
  extends Omit<SVGProps<SVGSVGElement>, 'children' | 'height' | 'width'> {
  name: HomePrimitiveIconName;
  size?: number;
}

function iconPaths(name: HomePrimitiveIconName): ReactNode {
  switch (name) {
    case 'dataset':
    case 'dataSource':
      return (
        <>
          <ellipse cx="12" cy="5.5" rx="7" ry="2.5" />
          <path d="M5 5.5v5c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5v-5" />
          <path d="M5 10.5v5c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5v-5" />
        </>
      );
    case 'service':
      return (
        <>
          <path d="M8.2 5.2 4.7 8.7a2 2 0 0 0 0 2.8l3.5 3.5" />
          <path d="m15.8 5.2 3.5 3.5a2 2 0 0 1 0 2.8L15.8 15" />
          <path d="m13.7 4-3.4 16" />
        </>
      );
    case 'dashboard':
      return (
        <>
          <rect x="4" y="4" width="16" height="16" rx="2.5" />
          <path d="M4 10h16M10 10v10" />
          <path d="m13 16 2-2 2 1 2-3" />
        </>
      );
    case 'sync':
      return (
        <>
          <path d="M5 8h12.5" />
          <path d="m14.5 5 3 3-3 3" />
          <path d="M19 16H6.5" />
          <path d="m9.5 13-3 3 3 3" />
        </>
      );
    case 'quality':
      return (
        <>
          <circle cx="12" cy="12" r="8" />
          <path d="m8.5 12 2.3 2.4 4.9-5" />
        </>
      );
    case 'file':
      return (
        <>
          <path d="M7 3.8h6l4 4V20H7z" />
          <path d="M13 3.8V8h4M9.5 12h5M9.5 15.5h5" />
        </>
      );
    case 'folder':
      return (
        <path d="M3.8 7.5c0-1.1.9-2 2-2h4l1.8 2H18c1.2 0 2.2 1 2.2 2.2v7.1c0 1.2-1 2.2-2.2 2.2H6c-1.2 0-2.2-1-2.2-2.2z" />
      );
    case 'storage':
      return (
        <>
          <ellipse cx="12" cy="5.5" rx="7" ry="2.5" />
          <path d="M5 5.5v11c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5v-11" />
          <path d="M5 11c0 1.38 3.13 2.5 7 2.5s7-1.12 7-2.5" />
        </>
      );
    default:
      return null;
  }
}

/** Small homepage-only SVG glyphs. Keep these dependency-free. */
export default function HomePrimitiveIcon({
  name,
  size = 20,
  ...props
}: HomePrimitiveIconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {iconPaths(name)}
    </svg>
  );
}
