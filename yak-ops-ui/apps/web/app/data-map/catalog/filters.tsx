import {
  Button,
  Checkbox,
  Input,
  Popover,
  PopoverContent,
  PopoverTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { ChevronDown, Search } from "lucide-react";

import type { TagKey } from "@/service/tags";

import { tagFilterGroups, tagFilterLabel } from "@/app/data-governance/tags/tag-filter";
import type { DatasourceOption } from "../shared/lookups";

interface Props {
  keyword: string;
  datasourceId: string;
  database: string;
  ownerId: string;
  tagValueIds: string[];
  includeMissing: boolean;
  datasources: DatasourceOption[];
  databases: string[];
  tagDictionary: TagKey[];
  ownerOptions: { userId: string; displayName: string }[];
  hasActiveFilters: boolean;
  onKeywordChange: (value: string) => void;
  onKeywordCommit: () => void;
  onDatasourceChange: (value: string) => void;
  onDatabaseChange: (value: string) => void;
  onOwnerChange: (value: string) => void;
  onTagValuesChange: (valueIds: string[]) => void;
  onIncludeMissingChange: (value: boolean) => void;
  onReset: () => void;
}

interface Option {
  value: string;
  label: string;
}

function FilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: Option[];
  onChange: (value: string) => void;
}) {
  return (
    <Select
      size="small"
      items={options}
      value={value}
      onValueChange={(next) => onChange(next ?? "")}
    >
      <SelectTrigger variant="outlined" className="w-[190px]">
        <span className="mr-2 shrink-0 text-[var(--yak-components-muted-text)]">{label}</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/**
 * Multi-select tag filter grouped by key.
 *
 * <p>Values of one key are OR-ed and different keys are AND-ed on the server, so the trigger shows
 * one `key: values` clause per selected key and the selection itself stays a flat id list.
 */
function TagFilter({
  tagDictionary,
  selectedValueIds,
  onChange,
}: {
  tagDictionary: TagKey[];
  selectedValueIds: string[];
  onChange: (valueIds: string[]) => void;
}) {
  const groups = tagFilterGroups(tagDictionary, selectedValueIds);
  const toggle = (valueId: string, checked: boolean) =>
    onChange(
      checked
        ? [...new Set([...selectedValueIds, valueId])]
        : selectedValueIds.filter((id) => id !== valueId),
    );

  return (
    <Popover>
      <PopoverTrigger
        className="flex h-7 w-[240px] items-center justify-between gap-1 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] px-2.5 text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-input-text)] outline-none hover:border-[var(--yak-components-input-border-focus)]"
        aria-label="标签筛选"
      >
        <span className="min-w-0 truncate">
          <span className="mr-2 text-[var(--yak-components-muted-text)]">标签</span>
          {tagFilterLabel(groups)}
        </span>
        <ChevronDown size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[280px]">
        {tagDictionary.length === 0 ? (
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            还没有标签键，请先到标签管理创建。
          </p>
        ) : (
          <div className="max-h-72 space-y-3 overflow-auto">
            {tagDictionary.map((key) => (
              <section key={key.id}>
                <h3 className="text-xs font-medium text-[var(--yak-components-muted-text)]">
                  {key.name}
                </h3>
                <div className="mt-1 space-y-1">
                  {key.values.map((value) => (
                    <label
                      key={value.id}
                      className="flex cursor-pointer items-center gap-2 text-sm text-[var(--yak-components-panel-text)]"
                    >
                      <Checkbox
                        checked={selectedValueIds.includes(value.id)}
                        aria-label={value.name}
                        onCheckedChange={(checked) => toggle(value.id, checked === true)}
                      />
                      <span className="min-w-0 flex-1 truncate">{value.name}</span>
                      <span className="text-xs text-[var(--yak-components-muted-text)]">
                        {value.usageCount}
                      </span>
                    </label>
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
        {selectedValueIds.length > 0 && (
          <div className="mt-2 flex justify-end border-t border-[var(--yak-components-panel-border)] pt-2">
            <Button size="small" variant="ghost" onClick={() => onChange([])}>
              清空标签
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}

/** Search and filters above the catalog grid; the tree on the left is the only taxonomy control. */
export default function CatalogFilters(props: Props) {
  const datasourceOptions: Option[] = [
    { value: "", label: "全部数据源" },
    ...props.datasources.map((item) => ({ value: item.id, label: item.name })),
  ];
  const databaseOptions: Option[] = [
    { value: "", label: "全部库" },
    ...props.databases.map((item) => ({ value: item, label: item })),
  ];
  const ownerOptions: Option[] = [
    { value: "", label: "全部负责人" },
    ...props.ownerOptions.map((item) => ({ value: item.userId, label: item.displayName })),
  ];
  const statusOptions: Option[] = [
    { value: "active", label: "正常" },
    { value: "all", label: "包含已缺失" },
  ];

  return (
    <section className="flex shrink-0 flex-wrap items-center gap-2">
      <div className="relative w-[280px]">
        <Search
          size={14}
          aria-hidden="true"
          className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
        />
        <Input
          size="small"
          variant="outlined"
          value={props.keyword}
          className="pl-8"
          placeholder="搜索表名、描述或字段"
          aria-label="搜索资产"
          onChange={(event) => props.onKeywordChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") props.onKeywordCommit();
          }}
        />
      </div>

      <FilterSelect
        label="数据源"
        value={props.datasourceId}
        options={datasourceOptions}
        onChange={props.onDatasourceChange}
      />
      <FilterSelect
        label="库"
        value={props.database}
        options={databaseOptions}
        onChange={props.onDatabaseChange}
      />
      <FilterSelect
        label="负责人"
        value={props.ownerId}
        options={ownerOptions}
        onChange={props.onOwnerChange}
      />
      <TagFilter
        tagDictionary={props.tagDictionary}
        selectedValueIds={props.tagValueIds}
        onChange={props.onTagValuesChange}
      />

      <FilterSelect
        label="状态"
        value={props.includeMissing ? "all" : "active"}
        options={statusOptions}
        onChange={(value) => props.onIncludeMissingChange(value === "all")}
      />

      {props.hasActiveFilters && (
        <Button size="small" variant="ghost" onClick={props.onReset}>
          重置
        </Button>
      )}
    </section>
  );
}
