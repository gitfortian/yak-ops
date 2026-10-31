import { relativeTime } from "@/app/data-map/shared/labels";

/** 扫描耗时：毫秒入、人类可读出；缺失显示 -。 */
export function formatScanDuration(durationMillis: number | null | undefined): string {
  if (durationMillis == null || durationMillis < 0) return "-";
  const seconds = durationMillis / 1000;
  if (seconds < 60) return `${seconds.toFixed(seconds < 10 ? 1 : 0)} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return rest === 0 ? `${minutes} 分` : `${minutes} 分 ${rest} 秒`;
}

export { relativeTime };
