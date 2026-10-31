import {
  Alert,
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

import AccentBadge, { accentToneOf } from "../../shared/accent-badge";
import { dataMapKeys } from "../../shared/keys";
import { relativeTime } from "../../shared/labels";
import { useDomainTaxonomy, useOwnerOptions } from "../../shared/lookups";
import { useDataMapScope } from "../../shared/scope";
import TagDialog from "./tag-dialog";

interface Props {
  detail: TableDetail;
  editing: boolean;
  onCancel: () => void;
  onSaved: () => void;
  /** Enters edit mode from the read view; the header owns the same action. */
  onEdit: () => void;
}

const NO_DOMAIN = "__none__";
const NO_OWNER = "__none__";

/** Groups the asset's tag values by their key, keeping the key order of the response. */
function groupTagsByKey(tags: TableDetail["tags"]) {
  const grouped = new Map<string, { keyName: string; values: string[] }>();
  for (const tag of tags) {
    const entry = grouped.get(tag.keyId) ?? { keyName: tag.keyName, values: [] };
    entry.values.push(tag.valueName);
    grouped.set(tag.keyId, entry);
  }
  return [...grouped].map(([keyId, entry]) => ({ keyId, ...entry }));
}

/**
 * Curated metadata of one asset.
 *
 * <p>Read mode is the default and the only mode a visitor sees: the description, owner, domain and
 * tags are plain text until the overview card's edit action turns this into a form. The crawler
 * never writes these fields, so saving here cannot conflict with a run.
 */
export default function BasicsTab({ detail, editing, onCancel, onSaved, onEdit }: Props) {
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

  const tagGroups = groupTagsByKey(detail.tags);

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
        <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,1fr)_260px]">
          <div className="space-y-6">
            <section>
              <h2 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                业务描述
              </h2>
              {detail.description ? (
                <p className="mt-2.5 whitespace-pre-wrap rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)] px-4 py-3 text-sm leading-6 text-[var(--yak-components-panel-text)]">
                  {detail.description}
                </p>
              ) : (
                <button
                  type="button"
                  onClick={onEdit}
                  className="mt-2.5 w-full cursor-pointer rounded-[var(--yak-radius-md)] border border-dashed border-[var(--yak-components-control-border)] px-4 py-5 text-left text-sm text-[var(--yak-components-muted-text)] transition-colors hover:border-[var(--yak-color-primary)] hover:text-[var(--yak-color-primary)]"
                >
                  暂无业务描述，点击补充这张表的业务含义、口径与使用注意事项。
                </button>
              )}
            </section>

            <section>
              <h2 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                业务标签
              </h2>
              <div className="mt-2.5 flex flex-wrap items-center gap-2">
                {tagGroups.length === 0 ? (
                  <span className="text-sm text-[var(--yak-components-muted-text)]">未打标签</span>
                ) : (
                  tagGroups.map((group) => (
                    <span key={group.keyId} className="flex flex-wrap items-center gap-1.5">
                      <span className="text-xs text-[var(--yak-components-muted-text)]">
                        {group.keyName}
                      </span>
                      {group.values.map((value) => (
                        <AccentBadge key={value} tone={accentToneOf(value)}>
                          {value}
                        </AccentBadge>
                      ))}
                    </span>
                  ))
                )}
                <Button size="small" variant="ghost" onClick={() => setTagsOpen(true)}>
                  编辑标签
                </Button>
              </div>
            </section>

            {detail.remarks && (
              <section>
                <h2 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                  引擎注释
                </h2>
                <p className="mt-2.5 text-sm leading-6 text-[var(--yak-components-muted-text)]">
                  {detail.remarks}
                </p>
              </section>
            )}
          </div>

          <div className="space-y-4">
            <div className="rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                资产统计
              </h3>
              <dl className="mt-3 space-y-2.5">
                {[
                  { label: "字段数", value: detail.columns.length },
                  {
                    label: "主键",
                    value: detail.columns.filter((column) => column.primaryKey).length,
                  },
                  {
                    label: "可空字段",
                    value: detail.columns.filter((column) => column.nullable).length,
                  },
                  { label: "累计访问", value: detail.viewCount },
                ].map((stat) => (
                  <div key={stat.label} className="flex items-center justify-between gap-2">
                    <dt className="text-xs text-[var(--yak-components-muted-text)]">
                      {stat.label}
                    </dt>
                    <dd className="text-sm font-bold text-[var(--yak-components-panel-text)] [font-variant-numeric:tabular-nums]">
                      {stat.value}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>

            <div className="rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-[var(--yak-components-muted-text)]">
                采集生命周期
              </h3>
              <dl className="mt-3 space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-xs text-[var(--yak-components-muted-text)]">首次采集</dt>
                  <dd
                    className="text-xs text-[var(--yak-components-panel-text)]"
                    title={detail.firstSeenTime}
                  >
                    {relativeTime(detail.firstSeenTime)}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-xs text-[var(--yak-components-muted-text)]">最近采集</dt>
                  <dd
                    className="text-xs text-[var(--yak-components-panel-text)]"
                    title={detail.lastSeenTime}
                  >
                    {relativeTime(detail.lastSeenTime)}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-xs text-[var(--yak-components-muted-text)]">表类型</dt>
                  <dd className="font-mono text-xs text-[var(--yak-components-panel-text)]">
                    {detail.tableType ?? "-"}
                  </dd>
                </div>
              </dl>
            </div>
          </div>
        </div>
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

      {tagsOpen && (
        <TagDialog
          tableId={detail.id}
          selectedValueIds={detail.tags.map((tag) => tag.valueId)}
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
