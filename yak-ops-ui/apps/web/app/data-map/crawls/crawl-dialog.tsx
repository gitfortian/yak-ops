import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";

import { submitCrawl } from "@/service/data-map/crawl";
import { dataMapFailureMessage } from "@/service/data-map/request";

import { dataMapKeys } from "../shared/keys";
import { useDatabases, useDatasourceOptions, useSchemas } from "../shared/lookups";

interface Props {
  onClose: () => void;
  onSubmitted: () => void;
}

interface Option {
  value: string;
  label: string;
}

function SelectField({
  label,
  value,
  options,
  disabled,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  options: Option[];
  disabled?: boolean;
  placeholder: string;
  onChange: (value: string) => void;
}) {
  const items = [{ value: "", label: placeholder }, ...options];
  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      <Select
        items={items}
        value={value}
        disabled={disabled}
        onValueChange={(next) => onChange(next ?? "")}
      >
        <SelectTrigger variant="outlined" className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              <SelectItemText>{item.label}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </Field>
  );
}

/** Reports why a cascading select has no options yet, with a manual-input escape hatch. */
function CascadeHint({
  loading,
  failed,
  empty,
  emptyText,
  onRetry,
  onFallback,
}: {
  loading: boolean;
  failed: boolean;
  empty: boolean;
  emptyText: string;
  onRetry: () => void;
  onFallback?: () => void;
}) {
  const className = "mt-1 flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]";
  if (failed) {
    return (
      <div className={className}>
        列表加载失败
        <Button size="small" variant="ghost" onClick={onRetry}>
          重试
        </Button>
        {onFallback && (
          <Button size="small" variant="ghost" onClick={onFallback}>
            手动输入
          </Button>
        )}
      </div>
    );
  }
  if (loading) return <div className={className}>正在加载…</div>;
  if (empty) return <div className={className}>{emptyText}</div>;
  return null;
}

/**
 * Submits one run for a datasource namespace.
 *
 * <p>The database is a required choice rather than a default: a run without it would enumerate
 * every catalog the account can see and store them under one identity, so the catalog could not
 * filter by database and same-named tables would overwrite each other. The list is always read
 * live from the datasource, so the operator picks a database that actually exists — and when the
 * datasource itself cannot answer, the database can be typed by hand instead of blocking the run.
 *
 * <p>The exclude pattern is validated while typing and re-validated server side; matching tables
 * stay ACTIVE so a run can never retire a deliberately skipped table as missing.
 */
export default function CrawlDialog({ onClose, onSubmitted }: Props) {
  const queryClient = useQueryClient();
  const datasources = useDatasourceOptions();
  const [datasourceId, setDatasourceId] = useState("");
  const [database, setDatabase] = useState("");
  const [schema, setSchema] = useState("");
  const [excludePattern, setExcludePattern] = useState("");
  const [manualDatabase, setManualDatabase] = useState(false);
  const [manualName, setManualName] = useState("");
  const databases = useDatabases(datasourceId);
  const schemas = useSchemas(datasourceId, database);

  const databaseOptions = (databases.data ?? []).map((item) => ({ value: item, label: item }));
  const schemaOptions = (schemas.data ?? []).map((item) => ({ value: item, label: item }));

  const databaseValue = manualDatabase ? manualName.trim() : database;

  const patternError = useMemo(() => {
    const pattern = excludePattern.trim();
    if (pattern.length === 0) return "";
    try {
      new RegExp(pattern);
      return "";
    } catch {
      return "正则表达式无法编译，请检查语法";
    }
  }, [excludePattern]);

  const submission = useMutation({
    mutationFn: () =>
      submitCrawl({
        datasourceId,
        database: databaseValue,
        schema: schema || undefined,
        excludePattern: excludePattern.trim() || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "crawls"] });
      toast.success("采集任务已提交");
      onSubmitted();
    },
  });

  return (
    <Dialog
      open
      disablePointerDismissal
      onOpenChange={(open) => {
        if (!open && !submission.isPending) onClose();
      }}
    >
      <DialogContent className="w-[460px]">
        <DialogTitle className="text-sm font-medium">发起采集</DialogTitle>
        <DialogDescription className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
          同一数据源同时只能有一个进行中的采集任务。
        </DialogDescription>

        <div className="mt-4 space-y-3">
          <SelectField
            label="数据源"
            value={datasourceId}
            placeholder="选择数据源"
            options={datasources.options.map((item) => ({ value: item.id, label: item.name }))}
            onChange={(value) => {
              setDatasourceId(value);
              setDatabase("");
              setSchema("");
              setManualDatabase(false);
              setManualName("");
            }}
          />

          {manualDatabase ? (
            <Field>
              <FieldLabel>库名</FieldLabel>
              <Input
                value={manualName}
                variant="outlined"
                placeholder="输入要采集的库名"
                onChange={(event) => setManualName(event.target.value)}
              />
            </Field>
          ) : (
            <div>
              <SelectField
                label="库"
                value={database}
                placeholder="请选择库"
                disabled={!datasourceId || databases.isPending}
                options={databaseOptions}
                onChange={(value) => {
                  setDatabase(value);
                  setSchema("");
                }}
              />
              {datasourceId && (
                <CascadeHint
                  loading={databases.isPending || databases.isFetching}
                  failed={databases.isError}
                  empty={databases.isSuccess && databaseOptions.length === 0}
                  emptyText="该数据源下没有可采集的库"
                  onRetry={() => void databases.refetch()}
                  onFallback={() => setManualDatabase(true)}
                />
              )}
            </div>
          )}

          {!manualDatabase && database && (
            <div>
              <SelectField
                label="Schema"
                value={schema}
                placeholder="不指定（使用默认 Schema）"
                disabled={schemas.isPending}
                options={schemaOptions}
                onChange={setSchema}
              />
              <CascadeHint
                loading={schemas.isPending || schemas.isFetching}
                failed={schemas.isError}
                empty={schemas.isSuccess && schemaOptions.length === 0}
                emptyText="该库下没有可选的 Schema，将使用默认 Schema"
                onRetry={() => void schemas.refetch()}
              />
            </div>
          )}

          <Field>
            <FieldLabel>忽略表（选填）</FieldLabel>
            <Input
              value={excludePattern}
              variant="outlined"
              placeholder="配置正则忽略不想采集的表（如 ^tmp_）"
              aria-label="忽略表正则"
              onChange={(event) => setExcludePattern(event.target.value)}
            />
            {patternError && (
              <div className="mt-1 text-xs text-[var(--yak-components-badge-danger-text)]">
                {patternError}
              </div>
            )}
          </Field>
        </div>

        {submission.isError && (
          <Alert className="mt-3">{dataMapFailureMessage(submission.error, "采集提交失败")}</Alert>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <Button size="small" disabled={submission.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={submission.isPending}
            disabled={!datasourceId || databaseValue.length === 0 || patternError.length > 0}
            onClick={() => submission.mutate()}
          >
            发起采集
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
