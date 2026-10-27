import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldLabel,
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
import { useState } from "react";

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

/** Reports why a cascading select has no options yet, so an empty list never looks like a bug. */
function CascadeHint({
  loading,
  failed,
  empty,
  emptyText,
  onRetry,
}: {
  loading: boolean;
  failed: boolean;
  empty: boolean;
  emptyText: string;
  onRetry: () => void;
}) {
  const className = "mt-1 text-xs text-[var(--yak-components-muted-text)]";
  if (failed) {
    return (
      <div className={`${className} flex items-center gap-2`}>
        列表加载失败
        <Button size="small" variant="ghost" onClick={onRetry}>
          重试
        </Button>
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
 * live from the datasource, so the operator picks a database that actually exists.
 */
export default function CrawlDialog({ onClose, onSubmitted }: Props) {
  const queryClient = useQueryClient();
  const datasources = useDatasourceOptions();
  const [datasourceId, setDatasourceId] = useState("");
  const [database, setDatabase] = useState("");
  const [schema, setSchema] = useState("");
  const databases = useDatabases(datasourceId);
  const schemas = useSchemas(datasourceId, database);

  const databaseOptions = (databases.data ?? []).map((item) => ({ value: item, label: item }));
  const schemaOptions = (schemas.data ?? []).map((item) => ({ value: item, label: item }));

  const submission = useMutation({
    mutationFn: () =>
      submitCrawl({
        datasourceId,
        database,
        schema: schema || undefined,
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
      <DialogContent className="w-[440px]">
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
            }}
          />

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
              />
            )}
          </div>

          <div>
            <SelectField
              label="Schema"
              value={schema}
              placeholder={database ? "不指定（使用默认 Schema）" : "请先选择库"}
              disabled={!database || schemas.isPending}
              options={schemaOptions}
              onChange={setSchema}
            />
            {database && (
              <CascadeHint
                loading={schemas.isPending || schemas.isFetching}
                failed={schemas.isError}
                empty={schemas.isSuccess && schemaOptions.length === 0}
                emptyText="该库下没有可选的 Schema，将使用默认 Schema"
                onRetry={() => void schemas.refetch()}
              />
            )}
          </div>
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
            disabled={!datasourceId || !database}
            onClick={() => submission.mutate()}
          >
            发起采集
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
