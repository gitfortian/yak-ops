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

/** Submits one run for a datasource; the queue owns execution and the list polls its status. */
export default function CrawlDialog({ onClose, onSubmitted }: Props) {
  const queryClient = useQueryClient();
  const datasources = useDatasourceOptions();
  const [datasourceId, setDatasourceId] = useState("");
  const [database, setDatabase] = useState("");
  const [schema, setSchema] = useState("");
  const databases = useDatabases(datasourceId);
  const schemas = useSchemas(datasourceId, database);

  const submission = useMutation({
    mutationFn: () =>
      submitCrawl({
        datasourceId,
        database: database || undefined,
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
        <DialogTitle className="text-sm font-medium">提交采集</DialogTitle>
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
          <SelectField
            label="库"
            value={database}
            placeholder="使用数据源默认库"
            disabled={!datasourceId || databases.isPending}
            options={(databases.data ?? []).map((item) => ({ value: item, label: item }))}
            onChange={(value) => {
              setDatabase(value);
              setSchema("");
            }}
          />
          <SelectField
            label="Schema"
            value={schema}
            placeholder="使用默认 Schema"
            disabled={!datasourceId || schemas.isPending}
            options={(schemas.data ?? []).map((item) => ({ value: item, label: item }))}
            onChange={setSchema}
          />
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
            disabled={!datasourceId}
            onClick={() => submission.mutate()}
          >
            提交
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
