import { Input } from "@yak-ops/yak-ui";
import type { DataSyncIncrementalConfig } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "./searchable-select";

interface Props {
  value?: DataSyncIncrementalConfig;
  initialLoad: boolean;
  disabled?: boolean;
  onChange: (value: DataSyncIncrementalConfig) => void;
}

export const EMPTY_INCREMENTAL: DataSyncIncrementalConfig = {
  mode: "TIMESTAMP",
  column: "",
  delaySeconds: 120,
  lookbackSeconds: 600,
};

/** Edits one row's typed cursor definition; no user-supplied SQL is accepted. */
export function IncrementalFields({
  value = EMPTY_INCREMENTAL,
  initialLoad,
  disabled,
  onChange,
}: Props) {
  const patch = (next: Partial<DataSyncIncrementalConfig>) => onChange({ ...value, ...next });
  return (
    <div className="space-y-2">
      <DataSyncSearchableSelect
        placeholder="增量模式"
        value={value.mode}
        disabled={disabled}
        options={[
          { value: "TIMESTAMP", label: "更新时间" },
          { value: "INCREASING", label: "递增键" },
        ]}
        onValueChange={(mode) =>
          onChange({
            ...value,
            mode: mode as DataSyncIncrementalConfig["mode"],
            delaySeconds: mode === "INCREASING" ? 0 : 120,
            lookbackSeconds: mode === "INCREASING" ? 0 : 600,
            initialValue: undefined,
          })
        }
      />
      <Input
        size="small"
        variant="outlined"
        maxLength={128}
        aria-label="增量字段"
        placeholder="增量字段"
        disabled={disabled}
        value={value.column}
        onChange={(event) => patch({ column: event.target.value })}
      />
      {!initialLoad ? (
        <Input
          size="small"
          variant="outlined"
          aria-label="初始起点"
          placeholder={value.mode === "TIMESTAMP" ? "2026-10-01T00:00:00" : "0"}
          disabled={disabled}
          value={value.initialValue || ""}
          onChange={(event) => patch({ initialValue: event.target.value })}
        />
      ) : null}
      {value.mode === "TIMESTAMP" ? (
        <div className="flex gap-2">
          <Input
            size="small"
            variant="outlined"
            inputMode="numeric"
            aria-label="安全延迟秒数"
            placeholder="延迟(秒)"
            disabled={disabled}
            value={String(value.delaySeconds ?? 0)}
            onChange={(event) => patch({ delaySeconds: Number(event.target.value) })}
          />
          <Input
            size="small"
            variant="outlined"
            inputMode="numeric"
            aria-label="回看秒数"
            placeholder="回看(秒)"
            disabled={disabled}
            value={String(value.lookbackSeconds ?? 0)}
            onChange={(event) => patch({ lookbackSeconds: Number(event.target.value) })}
          />
        </div>
      ) : null}
    </div>
  );
}
