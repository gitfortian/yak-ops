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
  editing: boolean;
  onCancel: () => void;
  onSaved: () => void;
}

const NO_DOMAIN = "__none__";
const NO_OWNER = "__none__";

const Fact = ({ label, value }: { label: string; value: string }) => (
  <div className="min-w-0">
    <dt className="text-xs text-[var(--yak-components-muted-text)]">{label}</dt>
    <dd className="mt-0.5 truncate text-sm text-[var(--yak-components-panel-text)]" title={value}>
      {value || "-"}
    </dd>
  </div>
);

/**
 * Curated metadata of one asset.
 *
 * <p>Read mode is the default and the only mode a visitor sees: the description, owner, domain and
 * tags are plain text until the overview card's edit action turns this into a form. The crawler
 * never writes these fields, so saving here cannot conflict with a run.
 */
export default function BasicsTab({ detail, editing, onCancel, onSaved }: Props) {
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

  const ownerName = ownerId
    ? ((owners.data ?? []).find((owner) => owner.userId === ownerId)?.displayName ?? ownerId)
    : null;
  const domainName = domainId ? taxonomy.domainNames.get(domainId) : null;

  const ownerOptions = [
    { value: NO_OWNER, label: "未指定" },
    ...(owners.data ?? []).map((owner) => ({ value: owner.userId, label: owner.displayName })),
  ];
  const domainOptions = [
    { value: NO_DOMAIN, label: "未分类" },
    ...taxonomy.domains.map((domain) => ({ value: domain.id, label: domain.name })),
  ];

  /** Nothing to send means nothing to save; the form says so instead of issuing a no-op put. */
  const dirty =
    description.trim() !== (detail.description ?? "").trim() ||
    ownerId !== (detail.ownerId ?? "") ||
    domainId !== (detail.domainId ?? "");

  const submit = () => {
    if (!dirty) {
      toast.warning("未做任何修改");
      return;
    }
    save.mutate();
  };

  return (
    <div className="space-y-6">
      {!editing && (
        <section>
          <h2 className="text-xs font-medium text-[var(--yak-components-muted-text)]">业务描述</h2>
          {detail.description ? (
            <p className="mt-2 max-w-3xl whitespace-pre-wrap text-sm leading-6 text-[var(--yak-components-panel-text)]">
              {detail.description}
            </p>
          ) : (
            <p className="mt-2 text-sm text-[var(--yak-components-muted-text)]">
              暂无业务描述，点击右上角「编辑信息」补充。
            </p>
          )}
        </section>
      )}

      {editing && (
        <div className="grid max-w-3xl gap-5">
          <Field>
            <FieldLabel>业务描述</FieldLabel>
            <Textarea
              value={description}
              rows={4}
              maxLength={2000}
              placeholder="说明这张表的业务含义、口径与使用注意事项"
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
            <FieldLabel>业务标签</FieldLabel>
            <div className="flex flex-wrap items-center gap-2">
              {detail.tags.length === 0 ? (
                <span className="text-sm text-[var(--yak-components-muted-text)]">未打标签</span>
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

          <div className="flex items-center gap-2 border-t border-[var(--yak-components-panel-border)] pt-4">
            <Button size="small" variant="primary" loading={save.isPending} onClick={submit}>
              保存
            </Button>
            <Button size="small" disabled={save.isPending} onClick={onCancel}>
              取消
            </Button>
            {!dirty && (
              <span className="text-xs text-[var(--yak-components-muted-text)]">
                尚未修改任何内容
              </span>
            )}
          </div>
        </div>
      )}

      <dl className="grid grid-cols-2 gap-x-8 gap-y-4 border-t border-[var(--yak-components-panel-border)] pt-5 md:grid-cols-3">
        <Fact label="数据源" value={detail.datasourceName ?? detail.datasourceId} />
        <Fact label="位置" value={locationLabel(detail.database, detail.schema)} />
        <Fact label="表类型" value={detail.tableType ?? ""} />
        <Fact label="引擎注释" value={detail.remarks ?? ""} />
        <Fact label="首次采集" value={detail.firstSeenTime} />
        <Fact label="最近采集" value={detail.lastSeenTime} />
        {!editing && <Fact label="负责人" value={ownerName ?? "未指派"} />}
        {!editing && <Fact label="数据域" value={domainName ?? "未分类"} />}
      </dl>

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
