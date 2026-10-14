import type { DataSyncInstanceStatus } from "@/service/data-sync";

export interface ExecutionProgress {
  mode: "success" | "failure" | "running" | "idle";
  value: number | null;
}

/** The failure value is a display fallback, not a measured data-processing percentage. */
export function getExecutionProgress(status: DataSyncInstanceStatus): ExecutionProgress {
  switch (status) {
    case "FINISHED":
      return { mode: "success", value: 100 };
    case "FAILED":
      return { mode: "failure", value: 0 };
    case "RUNNING":
      return { mode: "running", value: null };
    default:
      return { mode: "idle", value: null };
  }
}
