import { YakMarkIcon } from "@yak-ops/yak-ui";
import { useId } from "react";

import "./app-loading.css";

/** Full-viewport loading state while the application resolves authentication. */
export default function AppLoading() {
  const id = useId();
  const maskId = `${id}-yak-mask`;
  const gradientId = `${id}-yak-highlight`;

  return (
    <div
      role="status"
      aria-label="正在加载"
      className="flex h-dvh w-full items-center justify-center bg-white"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width="48"
        height="48"
        viewBox="0 0 64 64"
        aria-hidden="true"
        focusable="false"
        className="shrink-0 text-[#b7bac0]"
      >
        <defs>
          <mask
            id={maskId}
            x="0"
            y="0"
            width="64"
            height="64"
            maskUnits="userSpaceOnUse"
            maskContentUnits="userSpaceOnUse"
            style={{ maskType: "alpha" }}
          >
            <YakMarkIcon size={64} />
          </mask>
          <linearGradient
            id={gradientId}
            x1="-28"
            y1="0"
            x2="0"
            y2="0"
            gradientUnits="userSpaceOnUse"
          >
            <stop offset="0" stopColor="#ffffff" stopOpacity="0" />
            <stop offset="0.25" stopColor="#ffffff" stopOpacity="0.25" />
            <stop offset="0.5" stopColor="#ffffff" stopOpacity="0.65" />
            <stop offset="0.75" stopColor="#ffffff" stopOpacity="0.25" />
            <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
          </linearGradient>
        </defs>
        <YakMarkIcon size={64} />
        <g mask={`url(#${maskId})`}>
          <rect
            x="-28"
            width="28"
            height="64"
            fill={`url(#${gradientId})`}
            className="motion-safe:animate-[yak-loading-shimmer_2s_linear_infinite] motion-reduce:hidden"
          />
        </g>
      </svg>
      <span className="sr-only">正在加载</span>
    </div>
  );
}
