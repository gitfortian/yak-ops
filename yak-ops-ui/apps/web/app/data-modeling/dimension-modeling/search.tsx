import {
  Button,
  Card,
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
import { ALL, EMPTY_MODEL_SEARCH, isModelSearching, type ModelSearchFilters } from "./filters";

/** One ownership node the selected tree node already fixes: a data domain or a data mart. */
export interface PinnedScope {
  id: string;
  name: string;
}

interface Props {
  filters: ModelSearchFilters;
  layers: Layer[];
  owners: OwnerOption[];
  tree: PlanningTree;
  /**
   * Data domain the selected tree node already fixes. Its select shows that domain and cannot be
   * changed, because a second domain filter would contradict the node.
   */
  pinnedDomain?: PinnedScope | null;
  /** Data mart the selected tree node already fixes, with the same rule as {@code pinnedDomain}. */
  pinnedDatamart?: PinnedScope | null;
  /** Set when layers or owners could not be read, so the bar can say so instead of hiding it. */
  optionsFailed?: boolean;
  onChange: (next: ModelSearchFilters) => void;
}

function FilterSelect(props: {
  label: string;
  value: string;
  items: { value: string; label: string }[];
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <Select
      value={props.value}
      size="small"
      items={props.items}
      disabled={props.disabled}
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
 * filters apply immediately because a select change is a single deliberate action. A tree node
 * that already fixes the owning domain or data mart pins the matching select, so the bar can never
 * ask for a scope the visible list does not have.
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
    <Card className="grid grid-cols-[repeat(auto-fit,minmax(9rem,1fr))] items-center gap-2 p-3">
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
      {props.pinnedDatamart ? null : (
        <FilterSelect
          label={props.pinnedDomain ? "数据域" : "全部数据域"}
          value={props.pinnedDomain ? props.pinnedDomain.id : filters.domainId || ALL}
          items={
            props.pinnedDomain
              ? [{ value: props.pinnedDomain.id, label: props.pinnedDomain.name }]
              : [{ value: ALL, label: "全部数据域" }, ...domains]
          }
          disabled={Boolean(props.pinnedDomain)}
          onChange={(value) => update({ domainId: value === ALL ? "" : value })}
        />
      )}
      {props.pinnedDomain ? null : (
        <FilterSelect
          label={props.pinnedDatamart ? "数据集市" : "全部数据集市"}
          value={props.pinnedDatamart ? props.pinnedDatamart.id : filters.datamartId || ALL}
          items={
            props.pinnedDatamart
              ? [{ value: props.pinnedDatamart.id, label: props.pinnedDatamart.name }]
              : [
                  { value: ALL, label: "全部数据集市" },
                  ...props.tree.datamarts.map((datamart) => ({
                    value: datamart.id,
                    label: datamart.name,
                  })),
                ]
          }
          disabled={Boolean(props.pinnedDatamart)}
          onChange={(value) => update({ datamartId: value === ALL ? "" : value })}
        />
      )}
      <Button
        size="small"
        className="justify-self-start"
        onClick={reset}
        disabled={!isModelSearching(filters) && !keyword.trim()}
      >
        清空
      </Button>
      {props.optionsFailed && (
        <span className="text-xs text-[var(--yak-components-badge-warning-text)]">
          分层或负责人加载失败，其余筛选条件仍可用
        </span>
      )}
    </Card>
  );
}
