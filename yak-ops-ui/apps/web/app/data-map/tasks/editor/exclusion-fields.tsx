import {
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  Field,
  FieldDescription,
  FieldLabel,
  Input,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { dataMapFailureMessage, isSearchTooFrequent } from "@/service/data-map/request";
import { previewExclude } from "@/service/data-map/task";
import { listDataSourceTables } from "@/service/datasource";
import type { DataSourceCatalogTable } from "@/service/datasource";

import { dataMapKeys } from "../../shared/keys";
import { useDataMapScope } from "../../shared/scope";
import type { TaskDraft, TaskDraftErrors } from "./form";

const SEARCH_DEBOUNCE_MS = 400;
const PREVIEW_DEBOUNCE_MS = 500;
const TABLE_OPTION_LIMIT = 200;
const EXCLUDE_TABLE_LIMIT = 500;

const unique = (names: string[]) => [...new Set(names.map((name) => name.trim()).filter(Boolean))];

function ExcludeChip({ name, onRemove }: { name: string; onRemove: () => void }) {
  return (
    <span className="inline-flex max-w-full items-center gap-1 rounded-full border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] py-0.5 pl-2.5 pr-1 text-xs text-[var(--yak-components-panel-text)]">
      <span className="truncate font-mono" title={name}>
        {name}
      </span>
      <button
        type="button"
        aria-label={`移除 ${name}`}
        className="inline-flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-full text-[var(--yak-components-muted-text)] outline-none hover:bg-[var(--yak-components-menu-item-hover)] hover:text-[var(--yak-components-panel-text)] focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-input-focus-ring)]"
        onClick={onRemove}
      >
        <X size={11} aria-hidden="true" />
      </button>
    </span>
  );
}

export default function ExclusionFields({
  draft,
  errors,
  onChange,
}: {
  draft: TaskDraft;
  errors: TaskDraftErrors;
  onChange: (patch: Partial<TaskDraft>) => void;
}) {
  const { scope, ready } = useDataMapScope();
  const [keyword, setKeyword] = useState("");
  const [settledKeyword, setSettledKeyword] = useState("");
  const [settledPattern, setSettledPattern] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => setSettledKeyword(keyword.trim()), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => setSettledPattern(draft.excludePattern.trim()),
      PREVIEW_DEBOUNCE_MS,
    );
    return () => window.clearTimeout(timer);
  }, [draft.excludePattern]);

  const hasScope = Boolean(draft.datasourceId && draft.database.trim());

  const tableOptions = useQuery({
    queryKey: dataMapKeys.tableOptions(
      scope,
      draft.datasourceId,
      draft.database,
      draft.schema,
      settledKeyword,
    ),
    queryFn: () =>
      listDataSourceTables(draft.datasourceId, {
        database: draft.database || undefined,
        schema: draft.schema || undefined,
        keyword: settledKeyword || undefined,
        limit: TABLE_OPTION_LIMIT,
      }),
    enabled: ready && hasScope && draft.excludeMode === "tables",
    staleTime: 30_000,
    retry: false,
    placeholderData: (previous) => previous,
  });

  const patternError = useMemo(() => {
    const pattern = draft.excludePattern.trim();
    if (!pattern) return "";
    try {
      new RegExp(pattern);
      return "";
    } catch {
      return "正则表达式无法编译，请检查语法";
    }
  }, [draft.excludePattern]);

  const previewEnabled =
    ready &&
    hasScope &&
    draft.excludeMode === "pattern" &&
    settledPattern.length > 0 &&
    settledPattern === draft.excludePattern.trim() &&
    !patternError;

  const preview = useQuery({
    queryKey: dataMapKeys.excludePreview(
      scope,
      draft.datasourceId,
      draft.database,
      draft.schema,
      settledPattern,
    ),
    queryFn: ({ signal }) =>
      previewExclude(
        {
          datasourceId: draft.datasourceId,
          database: draft.database.trim(),
          schema: draft.schema || undefined,
          regex: settledPattern,
        },
        signal,
      ),
    enabled: previewEnabled,
    retry: false,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });

  const selected = unique(draft.excludeTables);
  const available = [
    ...new Set([
      ...selected,
      ...(tableOptions.data ?? []).map((table: DataSourceCatalogTable) => table.name),
    ]),
  ];
  const tableEmpty =
    tableOptions.isSuccess && (tableOptions.data ?? []).length === 0 && settledKeyword.length > 0;

  const toggleTable = (names: string[]) => {
    onChange({ excludeTables: unique(names) });
    setKeyword("");
  };

  return (
    <div>
      <Tabs
        value={draft.excludeMode}
        onValueChange={(value) =>
          onChange({ excludeMode: String(value) === "pattern" ? "pattern" : "tables" })
        }
      >
        <TabsList>
          <TabsTab value="tables">按表排除</TabsTab>
          <TabsTab value="pattern">按正则排除</TabsTab>
        </TabsList>

        <TabsPanel value="tables" className="pt-4">
          <Field invalid={Boolean(errors.excludeTables)}>
            <FieldLabel>排除的表</FieldLabel>
            <Combobox<string, true>
              multiple
              items={available}
              value={selected}
              inputValue={keyword}
              disabled={!hasScope}
              onInputValueChange={(value) => setKeyword(value)}
              onValueChange={(names) => toggleTable(names)}
            >
              <ComboboxInput
                variant="outlined"
                aria-label="搜索并选择要排除的表"
                placeholder={
                  hasScope
                    ? tableOptions.isFetching
                      ? "正在搜索表…"
                      : "输入表名搜索并选择"
                    : "先选择数据源与数据库"
                }
              />
              <ComboboxContent
                emptyText={
                  tableEmpty
                    ? "没有匹配的表"
                    : tableOptions.isError
                      ? "表列表加载失败，请稍后重试"
                      : "输入关键字搜索表名"
                }
              >
                {(name: string) => (
                  <ComboboxItem key={name} value={name}>
                    <span className="min-w-0 flex-1 truncate font-mono">{name}</span>
                    <ComboboxItemIndicator />
                  </ComboboxItem>
                )}
              </ComboboxContent>
            </Combobox>
            <FieldDescription>
              可搜索数据源中的表并多选，最多 {EXCLUDE_TABLE_LIMIT} 张。
              {selected.length > 0 ? `已选 ${selected.length} 张。` : ""}
            </FieldDescription>
            {errors.excludeTables && (
              <FieldDescription className="!text-[var(--yak-components-field-error)]">
                {errors.excludeTables}
              </FieldDescription>
            )}
          </Field>

          {selected.length > 0 && (
            <div className="mt-3 flex max-h-36 flex-wrap gap-1.5 overflow-auto rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface)] p-2">
              {selected.map((name) => (
                <ExcludeChip
                  key={name}
                  name={name}
                  onRemove={() =>
                    onChange({ excludeTables: selected.filter((item) => item !== name) })
                  }
                />
              ))}
            </div>
          )}
        </TabsPanel>

        <TabsPanel value="pattern" className="pt-4">
          <Field invalid={Boolean(errors.excludePattern || patternError)}>
            <FieldLabel>排除正则</FieldLabel>
            <Input
              value={draft.excludePattern}
              variant="outlined"
              maxLength={256}
              aria-label="排除表正则"
              placeholder="例如：^tmp_|_bak$"
              className="font-mono"
              onChange={(event) => onChange({ excludePattern: event.target.value })}
            />
            <FieldDescription>
              使用 Java 正则匹配表名。预览在数据源引擎中执行，不会把全部表名加载到平台内存。
            </FieldDescription>
            {(errors.excludePattern || patternError) && (
              <FieldDescription className="!text-[var(--yak-components-field-error)]">
                {errors.excludePattern || patternError}
              </FieldDescription>
            )}
          </Field>

          <div className="mt-3 rounded-[var(--yak-radius-control-small)] bg-[var(--yak-color-hover)] px-3 py-2.5 text-xs">
            {!hasScope ? (
              <span className="text-[var(--yak-components-muted-text)]">
                先选择数据源与数据库，再预览命中的表。
              </span>
            ) : !draft.excludePattern.trim() ? (
              <span className="text-[var(--yak-components-muted-text)]">
                输入正则后，这里会显示命中的表。
              </span>
            ) : patternError ? (
              <span className="text-[var(--yak-components-field-error)]">
                正则表达式无法编译，请检查语法。
              </span>
            ) : preview.isError ? (
              <div className="flex flex-wrap items-center gap-2 text-[var(--yak-components-muted-text)]">
                <span>
                  {isSearchTooFrequent(preview.error)
                    ? "预览过于频繁，请稍后再试。"
                    : dataMapFailureMessage(preview.error, "正则预览查询失败")}
                </span>
                <button
                  type="button"
                  className="text-[var(--yak-color-primary)] hover:underline"
                  onClick={() => void preview.refetch()}
                >
                  重试
                </button>
              </div>
            ) : preview.isFetching || preview.data === undefined ? (
              <span className="text-[var(--yak-components-muted-text)]">正在计算命中的表…</span>
            ) : (
              <div>
                <div className="text-[var(--yak-components-panel-text)]">
                  命中 <span className="font-medium tabular-nums">{preview.data.matchedCount}</span>{" "}
                  张表
                  {preview.data.truncated ? "（仅展示前 200 张）" : ""}
                </div>
                {preview.data.tables.length > 0 && (
                  <div className="mt-2 grid max-h-44 grid-cols-1 gap-x-4 gap-y-1 overflow-auto font-mono text-[var(--yak-components-muted-text)] sm:grid-cols-2 lg:grid-cols-3">
                    {preview.data.tables.map((name) => (
                      <span key={name} className="truncate" title={name}>
                        {name}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </TabsPanel>
      </Tabs>

      <div className="mt-4 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] px-3 py-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
        被排除的表不会被采集，也不会被标记为缺失。取消排除后，下一次采集会重新同步这些表。
      </div>
    </div>
  );
}
