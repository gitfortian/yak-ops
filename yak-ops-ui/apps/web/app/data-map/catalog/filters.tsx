import {
  Button,
  Checkbox,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { Search } from "lucide-react";

import type { Tag } from "@/service/data-map/types";

import type { DatasourceOption } from "../shared/lookups";

interface Props {
  keyword: string;
  datasourceId: string;
  database: string;
  tagId: string;
  includeMissing: boolean;
  datasources: DatasourceOption[];
  databases: string[];
  tags: Tag[];
  hasActiveFilters: boolean;
  onKeywordChange: (value: string) => void;
  onKeywordCommit: () => void;
  onDatasourceChange: (value: string) => void;
  onDatabaseChange: (value: string) => void;
  onTagChange: (value: string) => void;
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
  const tagOptions: Option[] = [
    { value: "", label: "全部标签" },
    ...props.tags.map((tag) => ({ value: tag.id, label: tag.name })),
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
        label="标签"
        value={props.tagId}
        options={tagOptions}
        onChange={props.onTagChange}
      />

      <label className="flex cursor-pointer items-center gap-1.5 text-xs text-[var(--yak-components-muted-text)]">
        <Checkbox
          checked={props.includeMissing}
          onCheckedChange={(checked) => props.onIncludeMissingChange(checked === true)}
        />
        包含已缺失
      </label>

      {props.hasActiveFilters && (
        <Button size="small" variant="ghost" onClick={props.onReset}>
          重置
        </Button>
      )}
    </section>
  );
}
