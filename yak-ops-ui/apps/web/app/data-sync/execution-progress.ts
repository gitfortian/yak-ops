import type { DataSyncInstanceStatus } from "@/service/data-sync";

export interface ExecutionProgress {
  mode: "success" | "failure" | "running" | "progress" | "idle";
  value: number | null;
}

/** Uses measured bounded-source progress; unmeasured Jobs retain an indeterminate animation. */
export function getExecutionProgress(
  status: DataSyncInstanceStatus,
  percentage?: number | null,
  realtime = false,
): ExecutionProgress {
  const measured =
    percentage !== null &&
    percentage !== undefined &&
    Number.isInteger(percentage) &&
    percentage >= 0 &&
    percentage <= 99
      ? percentage
      : null;

  switch (status) {
    case "FINISHED":
      return { mode: "success", value: 100 };
    case "FAILED":
      return { mode: "failure", value: measured ?? 0 };
    case "RUNNING":
      return realtime || measured === null
        ? { mode: "running", value: null }
        : { mode: "progress", value: measured };
    case "SUBMITTED":
    case "LAUNCHING":
      return { mode: "idle", value: 0 };
    default:
      return { mode: "idle", value: measured };
  }
}
