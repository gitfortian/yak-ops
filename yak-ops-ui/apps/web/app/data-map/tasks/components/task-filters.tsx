import {
  Button,
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
import type { ReactNode } from "react";

import type { DatasourceOption } from "../../shared/lookups";

export const STATUS_OPTIONS = [
  { value: "", label: "全部状态" },
  { value: "RUNNING", label: "已上线" },
  { value: "OFFLINE", label: "已下线" },
  { value: "DRAFT", label: "草稿" },
];

interface Props {
  keyword: string;
  status: string;
  datasourceId: string;
  datasources: DatasourceOption[];
  hasFilters: boolean;
  summary: ReactNode;
  onKeywordChange: (value: string) => void;
  onStatusChange: (value: string) => void;
  onDatasourceChange: (value: string) => void;
  onReset: () => void;
}

export default function TaskFilters({
  keyword,
  status,
  datasourceId,
  datasources,
  hasFilters,
  summary,
  onKeywordChange,
  onStatusChange,
  onDatasourceChange,
  onReset,
}: Props) {
  const datasourceOptions = [
    { value: "", label: "全部数据源" },
    ...datasources.map((option) => ({ value: option.id, label: option.name })),
  ];

  return (
    <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-[var(--yak-components-control-border)] px-4 py-2.5">
      <div className="relative w-[240px]">
        <Search
          size={14}
          aria-hidden="true"
          className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
        />
        <Input
          size="small"
          variant="outlined"
          value={keyword}
          className="pl-8"
          placeholder="搜索任务名、数据源或库"
          aria-label="搜索采集任务"
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>

      <Select
        size="small"
        items={STATUS_OPTIONS}
        value={status}
        onValueChange={(next) => onStatusChange(next ?? "")}
      >
        <SelectTrigger variant="outlined" className="w-[132px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {STATUS_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              <SelectItemText>{option.label}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        size="small"
        items={datasourceOptions}
        value={datasourceId}
        onValueChange={(next) => onDatasourceChange(next ?? "")}
      >
        <SelectTrigger variant="outlined" className="w-[170px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {datasourceOptions.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              <SelectItemText>{option.label}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {hasFilters && (
        <Button size="small" variant="ghost" onClick={onReset}>
          重置
        </Button>
      )}

      <span className="ml-auto text-xs text-[var(--yak-components-muted-text)]">{summary}</span>
    </div>
  );
}
