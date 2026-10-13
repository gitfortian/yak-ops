import { YakMarkIcon } from "@yak-ops/yak-ui";

/** Full-viewport loading state while the application resolves authentication. */
export default function AppLoading() {
  return (
    <div
      role="status"
      aria-label="正在加载"
      className="flex h-dvh w-full items-center justify-center bg-white"
    >
      <YakMarkIcon size={48} className="shrink-0 text-black/25 motion-safe:animate-pulse" />
      <span className="sr-only">正在加载</span>
    </div>
  );
}
