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
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { dataMapFailureMessage } from "@/service/data-map/request";
import { previewScope } from "@/service/data-map/task";

import CascadeHint from "../../shared/cascade-hint";
import { dataMapKeys } from "../../shared/keys";
import {
  useDatabases,
  useDatasourceOptions,
  useOwnerOptions,
  useSchemas,
} from "../../shared/lookups";
import { useDataMapScope } from "../../shared/scope";
import type { TaskDraft, TaskDraftErrors } from "./form";

interface OwnerChoice {
  userId: string;
  displayName: string;
}

function SelectField({
  label,
  required,
  value,
  placeholder,
  options,
  disabled,
  error,
  onChange,
}: {
  label: string;
  required?: boolean;
  value: string;
  placeholder: string;
  options: string[];
  disabled?: boolean;
  error?: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field invalid={Boolean(error)}>
      <FieldLabel required={required}>{label}</FieldLabel>
      <Select
        items={[
          { value: "", label: placeholder },
          ...options.map((item) => ({ value: item, label: item })),
        ]}
        value={value}
        disabled={disabled}
        onValueChange={(next) => onChange(next ?? "")}
      >
        <SelectTrigger variant="outlined" className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="">
            <SelectItemText>{placeholder}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
          {options.map((item) => (
            <SelectItem key={item} value={item}>
              <SelectItemText>{item}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && (
        <FieldDescription className="!text-[var(--yak-components-field-error)]">
          {error}
        </FieldDescription>
      )}
    </Field>
  );
}

const withCurrent = (options: string[], current: string) =>
  current && !options.includes(current) ? [current, ...options] : options;

export default function BasicFields({
  draft,
  errors,
  onChange,
  onScopeBlockedChange,
}: {
  draft: TaskDraft;
  errors: TaskDraftErrors;
  onChange: (patch: Partial<TaskDraft>) => void;
  onScopeBlockedChange?: (blocked: boolean) => void;
}) {
  const { scope: workspaceScope, ready } = useDataMapScope();
  const datasources = useDatasourceOptions();
  const databases = useDatabases(draft.datasourceId);
  const schemas = useSchemas(draft.datasourceId, draft.database);
  const owners = useOwnerOptions();
  const [manualDatabase, setManualDatabase] = useState(false);
  const [manualName, setManualName] = useState("");

  const scoped = Boolean(draft.datasourceId && draft.database.trim());

  /**
   * Size of the namespace, counted by the datasource engine. It is shown before scheduling so a
   * scope over the collection ceiling can be corrected here instead of failing the first run.
   */
  const estimate = useQuery({
    queryKey: dataMapKeys.scopePreview(
      workspaceScope,
      draft.datasourceId,
      draft.database.trim(),
      draft.schema,
    ),
    queryFn: ({ signal }) =>
      previewScope(
        {
          datasourceId: draft.datasourceId,
          database: draft.database.trim(),
          schema: draft.schema || undefined,
        },
        signal,
      ),
    enabled: ready && scoped,
    retry: false,
    staleTime: 30_000,
  });

  const blocked = estimate.data?.exceeded ?? false;
  useEffect(() => {
    onScopeBlockedChange?.(blocked);
  }, [blocked, onScopeBlockedChange]);

  const databaseOptions = withCurrent(databases.data ?? [], manualDatabase ? "" : draft.database);
  const schemaOptions = withCurrent(schemas.data ?? [], draft.schema);

  const ownerChoices: OwnerChoice[] = (owners.data ?? []).map((owner) => ({
    userId: owner.userId,
    displayName: owner.displayName,
  }));
  if (draft.ownerId && !ownerChoices.some((owner) => owner.userId === draft.ownerId)) {
    ownerChoices.unshift({ userId: draft.ownerId, displayName: draft.ownerId });
  }
  const selectedOwner = ownerChoices.find((owner) => owner.userId === draft.ownerId) ?? null;

  const selectDatasource = (datasourceId: string) => {
    setManualDatabase(false);
    setManualName("");
    onChange({ datasourceId, database: "", schema: "" });
  };

  return (
    <div className="grid gap-5 md:grid-cols-2">
      <Field className="md:col-span-2" invalid={Boolean(errors.name)}>
        <FieldLabel required>任务名称</FieldLabel>
        <Input
          value={draft.name}
          maxLength={128}
          variant="outlined"
          placeholder="例如：交易库订单表每日采集"
          onChange={(event) => onChange({ name: event.target.value })}
        />
        {errors.name && (
          <FieldDescription className="!text-[var(--yak-components-field-error)]">
            {errors.name}
          </FieldDescription>
        )}
      </Field>

      <Field invalid={Boolean(errors.datasourceId)}>
        <FieldLabel required>数据源</FieldLabel>
        <Select
          items={[
            { value: "", label: "选择数据源" },
            ...datasources.options.map((item) => ({ value: item.id, label: item.name })),
          ]}
          value={draft.datasourceId}
          disabled={datasources.isPending}
          onValueChange={(value) => selectDatasource(value ?? "")}
        >
          <SelectTrigger variant="outlined" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">
              <SelectItemText>选择数据源</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
            {datasources.options.map((item) => (
              <SelectItem key={item.id} value={item.id}>
                <SelectItemText>{item.name}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {datasources.isError && (
          <FieldDescription className="!text-[var(--yak-components-field-error)]">
            数据源加载失败
          </FieldDescription>
        )}
        {errors.datasourceId && (
          <FieldDescription className="!text-[var(--yak-components-field-error)]">
            {errors.datasourceId}
          </FieldDescription>
        )}
      </Field>

      <Field invalid={Boolean(errors.ownerId)}>
        <FieldLabel required>负责人</FieldLabel>
        <Combobox<OwnerChoice>
          items={ownerChoices}
          value={selectedOwner}
          autoHighlight
          disabled={owners.isPending}
          itemToStringLabel={(owner) => owner.displayName}
          itemToStringValue={(owner) => owner.userId}
          isItemEqualToValue={(left, right) => left.userId === right.userId}
          onValueChange={(owner) => onChange({ ownerId: owner?.userId ?? "" })}
        >
          <ComboboxInput variant="outlined" placeholder="搜索工作空间成员" aria-label="负责人" />
          <ComboboxContent emptyText="没有匹配的工作空间成员">
            {(owner: OwnerChoice) => (
              <ComboboxItem key={owner.userId} value={owner}>
                <span className="min-w-0 flex-1 truncate">{owner.displayName}</span>
                <ComboboxItemIndicator />
              </ComboboxItem>
            )}
          </ComboboxContent>
        </Combobox>
        {owners.isError && (
          <FieldDescription className="!text-[var(--yak-components-field-error)]">
            成员列表加载失败
          </FieldDescription>
        )}
        {errors.ownerId && (
          <FieldDescription className="!text-[var(--yak-components-field-error)]">
            {errors.ownerId}
          </FieldDescription>
        )}
      </Field>

      <div>
        {manualDatabase ? (
          <Field invalid={Boolean(errors.database)}>
            <FieldLabel required>数据库</FieldLabel>
            <div className="flex items-center gap-2">
              <Input
                value={manualName}
                variant="outlined"
                maxLength={128}
                placeholder="输入数据库名"
                onChange={(event) => {
                  setManualName(event.target.value);
                  onChange({ database: event.target.value, schema: "" });
                }}
              />
              <button
                type="button"
                className="shrink-0 text-xs text-[var(--yak-color-primary)] hover:underline"
                onClick={() => {
                  setManualDatabase(false);
                  setManualName("");
                  onChange({ database: "", schema: "" });
                }}
              >
                选择列表
              </button>
            </div>
            {errors.database && (
              <FieldDescription className="!text-[var(--yak-components-field-error)]">
                {errors.database}
              </FieldDescription>
            )}
          </Field>
        ) : (
          <div>
            <SelectField
              label="数据库"
              required
              value={draft.database}
              placeholder="选择数据库"
              disabled={!draft.datasourceId || databases.isPending}
              options={databaseOptions}
              error={errors.database}
              onChange={(value) => onChange({ database: value, schema: "" })}
            />
            {draft.datasourceId && (
              <CascadeHint
                loading={databases.isPending || databases.isFetching}
                failed={databases.isError}
                empty={databases.isSuccess && databaseOptions.length === 0}
                emptyText="该数据源下没有可采集的数据库"
                onRetry={() => void databases.refetch()}
                onFallback={() => {
                  setManualDatabase(true);
                  setManualName(draft.database);
                }}
              />
            )}
          </div>
        )}
      </div>

      <div>
        <SelectField
          label="Schema"
          value={draft.schema}
          placeholder="不指定（使用默认 Schema）"
          disabled={!draft.database || schemas.isPending}
          options={schemaOptions}
          onChange={(value) => onChange({ schema: value })}
        />
        {draft.database && (
          <CascadeHint
            loading={schemas.isPending || schemas.isFetching}
            failed={schemas.isError}
            empty={schemas.isSuccess && schemaOptions.length === 0}
            emptyText="该库下没有可选的 Schema，将使用默认 Schema"
            onRetry={() => void schemas.refetch()}
          />
        )}
      </div>

      <div
        className={`md:col-span-2 rounded-[var(--yak-radius-control-small)] px-3 py-2.5 text-xs leading-5 ${
          blocked
            ? "bg-[var(--yak-components-badge-danger-bg)] text-[var(--yak-components-badge-danger-text)]"
            : "bg-[var(--yak-color-hover)] text-[var(--yak-components-muted-text)]"
        }`}
      >
        {!scoped ? (
          "选择数据库后，这里会估算本次采集的表数量。"
        ) : estimate.isError ? (
          <span className="flex flex-wrap items-center gap-2">
            {dataMapFailureMessage(estimate.error, "表数量统计失败")}
            <button
              type="button"
              className="text-[var(--yak-color-primary)] hover:underline"
              onClick={() => void estimate.refetch()}
            >
              重试
            </button>
          </span>
        ) : estimate.isPending ? (
          "正在统计表数量…"
        ) : (
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              预计{" "}
              <span className="font-medium tabular-nums text-[var(--yak-components-panel-text)]">
                {estimate.data.tableCount.toLocaleString()}
              </span>{" "}
              张表
            </span>
            {draft.excludeTables.length > 0 && (
              <span>按表排除 {draft.excludeTables.length} 张</span>
            )}
            {blocked ? (
              <span>
                超过单次采集上限 {estimate.data.limit.toLocaleString()} 张，请缩小 Schema
                范围或改用排除规则。
              </span>
            ) : (
              <span>上限 {estimate.data.limit.toLocaleString()} 张</span>
            )}
          </span>
        )}
      </div>
    </div>
  );
}
