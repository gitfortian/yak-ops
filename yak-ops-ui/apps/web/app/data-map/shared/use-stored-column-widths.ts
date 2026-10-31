import { useCallback, useState } from "react";

const restore = (storageKey: string): (number | null)[] => {
  try {
    const parsed: unknown = JSON.parse(window.localStorage.getItem(storageKey) ?? "null");
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => (typeof item === "number" && Number.isFinite(item) ? item : null));
  } catch {
    return [];
  }
};

/**
 * Column width overrides of one table, persisted per browser so a adjusted
 * layout survives reloads. Storage failures (private mode, quota) degrade to
 * session-only state.
 */
export function useStoredColumnWidths(storageKey: string) {
  const [columnWidths, setColumnWidths] = useState<(number | null)[]>(() => restore(storageKey));

  const change = useCallback(
    (next: (number | null)[]) => {
      setColumnWidths(next);
      try {
        window.localStorage.setItem(storageKey, JSON.stringify(next));
      } catch {
        // 私有模式或配额受限时仅保留会话内状态。
      }
    },
    [storageKey],
  );

  return [columnWidths, change] as const;
}
