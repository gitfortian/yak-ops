import {
  DIMENSION_SCOPES,
  DIMENSION_SCOPE_LABEL,
  type DimensionScope,
} from "@/service/data-modeling/dimension-modeling/types";

/** Radios are used instead of a select because the choice rewrites the whole ownership block. */
export function DimensionScopeRadio(props: {
  value: DimensionScope;
  disabled?: boolean;
  onChange: (value: DimensionScope) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label="维度归属"
      className="flex min-h-7 items-center gap-5 text-xs"
    >
      {DIMENSION_SCOPES.map((scope) => (
        <label key={scope} className="flex items-center gap-2">
          <input
            type="radio"
            name="dimension-scope"
            disabled={props.disabled}
            checked={props.value === scope}
            className="size-3.5 accent-[var(--yak-color-primary)]"
            onChange={() => props.onChange(scope)}
          />
          {DIMENSION_SCOPE_LABEL[scope]}
          <span className="text-[11px] text-[#98a2b3]">
            {scope === "PUBLIC" ? "挂数据域" : "挂数据集市"}
          </span>
        </label>
      ))}
    </div>
  );
}
