import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState } from "react";
import {
  MODEL_TYPE_LABEL,
  MODEL_TYPES,
  type ModelType,
} from "@/service/data-modeling/dimension-modeling/types";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type { OwnerOption, PlanningTree } from "@/service/data-modeling/warehouse-planning/types";

/** Sentinel used by the selects; an empty filter field means "no restriction". */
const ALL = "ALL";

/**
 * Filters accepted by the model list.
 *
 * <p>An empty string means "all" so the request builder can omit the parameter, while the selects
 * use {@code ALL} because a {@code Select} treats an empty value as "nothing selected".
 */
export interface ModelSearchFilters {
  keyword: string;
  type: ModelType | "ALL";
  layerId: string;
  ownerId: string;
  domainId: string;
  datamartId: string;
}

export const EMPTY_MODEL_SEARCH: ModelSearchFilters = {
  keyword: "",
  type: ALL,
  layerId: "",
  ownerId: "",
  domainId: "",
  datamartId: "",
};

/** True when at least one condition is set, which switches the panel to server-side results. */
export function isModelSearching(filters: ModelSearchFilters) {
  return Boolean(
    filters.keyword.trim() ||
    filters.type !== ALL ||
    filters.layerId ||
    filters.ownerId ||
    filters.domainId ||
    filters.datamartId,
  );
}

interface Props {
  filters: ModelSearchFilters;
  layers: Layer[];
  owners: OwnerOption[];
  tree: PlanningTree;
  /** Set when layers or owners could not be read, so the bar can say so instead of hiding it. */
  optionsFailed?: boolean;
  onChange: (next: ModelSearchFilters) => void;
}

function FilterSelect(props: {
  label: string;
  value: string;
  items: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <Select
      value={props.value}
      size="small"
      items={props.items}
      onValueChange={(value) => props.onChange(value ?? ALL)}
    >
      <SelectTrigger variant="outlined" aria-label={props.label} className="w-36">
        <SelectValue placeholder={props.label} />
      </SelectTrigger>
      <SelectContent>
        {props.items.map((item) => (
          <SelectItem key={item.value} value={item.value}>
            <SelectItemText>{item.label}</SelectItemText>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/**
 * Keyword and attribute filters for the model list.
 *
 * <p>The keyword is debounced so typing does not fire one request per character; the attribute
 * filters apply immediately because a select change is a single deliberate action.
 */
export default function ModelSearchBar(props: Props) {
  const { filters, onChange } = props;
  const [keyword, setKeyword] = useState(filters.keyword);
  const latest = useRef(filters);
  latest.current = filters;
  const change = useRef(onChange);
  change.current = onChange;
  // A parent rename or a programme-level reset must not leave the input holding a stale keyword.
  useEffect(() => {
    setKeyword(filters.keyword);
  }, [filters.keyword]);
  useEffect(() => {
    if (keyword === latest.current.keyword) return;
    const timer = setTimeout(() => change.current({ ...latest.current, keyword }), 400);
    return () => clearTimeout(timer);
  }, [keyword]);

  const update = (patch: Partial<ModelSearchFilters>) => onChange({ ...filters, ...patch });
  const reset = () => {
    setKeyword("");
    onChange(EMPTY_MODEL_SEARCH);
  };
  const domains = props.tree.categories.flatMap((category) =>
    category.children.map((domain) => ({
      value: domain.id,
      label: `${category.name} / ${domain.name}`,
    })),
  );

  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-[var(--yak-components-panel-border)] bg-white px-3 py-2">
      <Input
        size="small"
        variant="outlined"
        className="w-56"
        placeholder="搜索表名或中文名"
        aria-label="搜索表名或中文名"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      <FilterSelect
        label="全部类型"
        value={filters.type}
        items={[
          { value: ALL, label: "全部类型" },
          ...MODEL_TYPES.map((type) => ({ value: type, label: MODEL_TYPE_LABEL[type] })),
        ]}
        onChange={(value) => update({ type: value as ModelType | "ALL" })}
      />
      <FilterSelect
        label="全部分层"
        value={filters.layerId || ALL}
        items={[
          { value: ALL, label: "全部分层" },
          ...props.layers.map((layer) => ({ value: layer.id, label: layer.cnName })),
        ]}
        onChange={(value) => update({ layerId: value === ALL ? "" : value })}
      />
      <FilterSelect
        label="全部负责人"
        value={filters.ownerId || ALL}
        items={[
          { value: ALL, label: "全部负责人" },
          ...props.owners.map((owner) => ({ value: owner.userId, label: owner.displayName })),
        ]}
        onChange={(value) => update({ ownerId: value === ALL ? "" : value })}
      />
      <FilterSelect
        label="全部数据域"
        value={filters.domainId || ALL}
        items={[{ value: ALL, label: "全部数据域" }, ...domains]}
        onChange={(value) => update({ domainId: value === ALL ? "" : value })}
      />
      <FilterSelect
        label="全部数据集市"
        value={filters.datamartId || ALL}
        items={[
          { value: ALL, label: "全部数据集市" },
          ...props.tree.datamarts.map((datamart) => ({
            value: datamart.id,
            label: datamart.name,
          })),
        ]}
        onChange={(value) => update({ datamartId: value === ALL ? "" : value })}
      />
      <Button size="small" onClick={reset} disabled={!isModelSearching(filters) && !keyword.trim()}>
        清空
      </Button>
      {props.optionsFailed && (
        <span className="text-[11px] text-[#b54708]">分层或负责人加载失败，其余筛选条件仍可用</span>
      )}
    </div>
  );
}
