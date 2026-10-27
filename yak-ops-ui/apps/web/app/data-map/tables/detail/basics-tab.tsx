import {
  Alert,
  Badge,
  Button,
  Field,
  FieldLabel,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { updateTable } from "@/service/data-map/catalog";
import { dataMapFailureMessage } from "@/service/data-map/request";
import type { TableDetail } from "@/service/data-map/types";

import { dataMapKeys } from "../../shared/keys";
import { locationLabel } from "../../shared/labels";
import { useDomainTaxonomy, useOwnerOptions } from "../../shared/lookups";
import { useDataMapScope } from "../../shared/scope";
import TagDialog from "./tag-dialog";

interface Props {
  detail: TableDetail;
  onSaved: () => void;
}

const NO_DOMAIN = "__none__";
const NO_OWNER = "__none__";

const Fact = ({ label, value }: { label: string; value: string }) => (
  <div className="min-w-0">
    <dt className="text-xs text-[var(--yak-components-muted-text)]">{label}</dt>
    <dd className="truncate text-[13px]" title={value}>
      {value || "-"}
    </dd>
  </div>
);

/** Curated metadata of one asset; the crawler never writes these three fields. */
export default function BasicsTab({ detail, onSaved }: Props) {
  const { scope } = useDataMapScope();
  const queryClient = useQueryClient();
  const owners = useOwnerOptions();
  const taxonomy = useDomainTaxonomy();
  const [description, setDescription] = useState(detail.description ?? "");
  const [ownerId, setOwnerId] = useState(detail.ownerId ?? "");
  const [domainId, setDomainId] = useState(detail.domainId ?? "");
  const [tagsOpen, setTagsOpen] = useState(false);
  const [failure, setFailure] = useState("");

  const save = useMutation({
    mutationFn: () =>
      updateTable(detail.id, {
        description: description.trim() || null,
        ownerId: ownerId || null,
        domainId: domainId || null,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: dataMapKeys.table(scope, detail.id) });
      void queryClient.invalidateQueries({ queryKey: [...dataMapKeys.all, "tables"] });
      toast.success("已保存");
      onSaved();
    },
    onError: (error) => setFailure(dataMapFailureMessage(error, "保存失败，请稍后重试。")),
  });

  const ownerOptions = [
    { value: NO_OWNER, label: "未指定" },
    ...(owners.data ?? []).map((owner) => ({ value: owner.userId, label: owner.displayName })),
  ];
  const domainOptions = [
    { value: NO_DOMAIN, label: "未分类" },
    ...taxonomy.domains.map((domain) => ({ value: domain.id, label: domain.name })),
  ];

  return (
    <div className="space-y-5">
      <dl className="grid grid-cols-2 gap-4 border border-[var(--yak-components-panel-border)] p-4 md:grid-cols-3">
        <Fact label="数据源" value={detail.datasourceName ?? detail.datasourceId} />
        <Fact label="位置" value={locationLabel(detail.database, detail.schema)} />
        <Fact label="表类型" value={detail.tableType ?? ""} />
        <Fact label="引擎注释" value={detail.remarks ?? ""} />
        <Fact label="首次采集" value={detail.firstSeenTime} />
        <Fact label="最近采集" value={detail.lastSeenTime} />
      </dl>

      <div className="grid max-w-3xl gap-4">
        <Field>
          <FieldLabel>描述</FieldLabel>
          <Textarea
            value={description}
            rows={3}
            maxLength={2000}
            placeholder="说明这张表的业务含义"
            onValueChange={(value) => setDescription(value)}
          />
        </Field>

        <Field>
          <FieldLabel>负责人</FieldLabel>
          <Select
            items={ownerOptions}
            value={ownerId || NO_OWNER}
            onValueChange={(value) => setOwnerId(value === NO_OWNER ? "" : (value ?? ""))}
          >
            <SelectTrigger variant="outlined" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {ownerOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Field>
          <FieldLabel>数据域</FieldLabel>
          <Select
            items={domainOptions}
            value={domainId || NO_DOMAIN}
            onValueChange={(value) => setDomainId(value === NO_DOMAIN ? "" : (value ?? ""))}
          >
            <SelectTrigger variant="outlined" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {domainOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Field>
          <FieldLabel>标签</FieldLabel>
          <div className="flex flex-wrap items-center gap-2">
            {detail.tags.length === 0 ? (
              <span className="text-[13px] text-[var(--yak-components-muted-text)]">未打标签</span>
            ) : (
              detail.tags.map((tag) => (
                <Badge key={tag.id} tone="info">
                  {tag.name}
                </Badge>
              ))
            )}
            <Button size="small" variant="ghost" onClick={() => setTagsOpen(true)}>
              管理标签
            </Button>
          </div>
        </Field>

        {failure && <Alert>{failure}</Alert>}

        <div>
          <Button
            size="small"
            variant="primary"
            loading={save.isPending}
            onClick={() => save.mutate()}
          >
            保存
          </Button>
        </div>
      </div>

      {tagsOpen && (
        <TagDialog
          tableId={detail.id}
          selectedTagIds={detail.tags.map((tag) => tag.id)}
          onClose={() => setTagsOpen(false)}
          onSaved={() => {
            setTagsOpen(false);
            void queryClient.invalidateQueries({ queryKey: dataMapKeys.table(scope, detail.id) });
            onSaved();
          }}
        />
      )}
    </div>
  );
}
