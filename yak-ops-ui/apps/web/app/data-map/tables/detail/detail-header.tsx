import { Badge, Button, Card, toast } from "@yak-ops/yak-ui";
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Clock3,
  Copy,
  Layers,
  Pencil,
  Star,
  UserRound,
} from "lucide-react";
import { useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import type { TableDetail } from "@/service/data-map/types";

import { DATA_MAP_BASE, locationLabel, TABLE_STATUS_LABEL } from "../../shared/labels";
import { useDomainTaxonomy, useOwnerOptions } from "../../shared/lookups";

interface Props {
  detail: TableDetail;
  onEdit: () => void;
  onToggleFavorite: () => void;
  favoritePending: boolean;
}

const META_CLASS = "flex min-w-0 items-center gap-1.5";
const META_LABEL_CLASS = "shrink-0 text-[var(--yak-components-muted-text)]";

/** One labelled fact of the summary strip; the value keeps its own styling. */
function Meta({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className={META_CLASS}>
      <span className="shrink-0 text-[var(--yak-components-muted-text)]">{icon}</span>
      <span className={META_LABEL_CLASS}>{label}</span>
      <span className="min-w-0 truncate text-sm text-[var(--yak-components-panel-text)]">
        {children}
      </span>
    </div>
  );
}

/**
 * Asset overview card above the tabs.
 *
 * <p>It answers "what is this table and who owns it" before any tab is opened: identity, location,
 * liveness, owner, domain, last crawl and tags. Everything here is read-only; curating happens
 * behind the edit action so a detail visit can never change data by accident.
 */
export default function DetailHeader({ detail, onEdit, onToggleFavorite, favoritePending }: Props) {
  const owners = useOwnerOptions();
  const taxonomy = useDomainTaxonomy();
  const [copied, setCopied] = useState(false);

  const missing = detail.status === "MISSING";
  const ownerName = detail.ownerId
    ? ((owners.data ?? []).find((owner) => owner.userId === detail.ownerId)?.displayName ??
      detail.ownerId)
    : null;
  const domainName = detail.domainId ? taxonomy.domainNames.get(detail.domainId) : null;

  const copyName = async () => {
    try {
      await navigator.clipboard.writeText(detail.name);
      setCopied(true);
      toast.success("表名已复制");
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      toast.error("复制失败，请手动选中表名");
    }
  };

  return (
    <Card className="shadow-[var(--yak-datamap-card-shadow)]">
      {missing && (
        <div className="flex items-start gap-2 border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-badge-danger-bg)] px-6 py-2.5 text-xs leading-5 text-[var(--yak-components-badge-danger-text)] max-md:px-4">
          <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>
            该表已不在其数据源中，通常意味着被删除或重命名。重命名后的新表会作为新资产出现，描述与负责人需要手工复制。
          </span>
        </div>
      )}

      <div className="px-6 pt-4 max-md:px-4">
        <Link
          to={`${DATA_MAP_BASE}/catalog`}
          className="inline-flex items-center gap-1 text-xs text-[var(--yak-components-muted-text)] hover:[color:var(--yak-color-primary)]"
        >
          <ArrowLeft size={13} aria-hidden="true" />
          资产目录
        </Link>

        <div className="mt-1.5 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="flex min-w-0 items-center gap-2.5">
              <h1
                className="truncate text-lg font-semibold leading-7 text-[var(--yak-components-panel-text)]"
                title={detail.name}
              >
                {detail.name}
              </h1>
              <Badge tone={missing ? "danger" : "success"}>
                {TABLE_STATUS_LABEL[detail.status]}
              </Badge>
            </div>
            <p className="mt-1 truncate text-xs text-[var(--yak-components-muted-text)]">
              {[
                detail.datasourceName ?? detail.datasourceId,
                locationLabel(detail.database, detail.schema),
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>

          <div className="flex shrink-0 flex-wrap items-center gap-1.5">
            <Button size="small" variant="ghost" onClick={() => void copyName()}>
              {copied ? (
                <Check size={13} aria-hidden="true" />
              ) : (
                <Copy size={13} aria-hidden="true" />
              )}
              {copied ? "已复制" : "复制表名"}
            </Button>
            <Button
              size="small"
              variant="ghost"
              loading={favoritePending}
              onClick={onToggleFavorite}
            >
              <Star
                size={13}
                aria-hidden="true"
                className={
                  detail.favorited
                    ? "fill-current text-[var(--yak-components-alert-warning-icon)]"
                    : undefined
                }
              />
              {detail.favorited ? "已收藏" : "收藏"}
            </Button>
            <Button size="small" onClick={onEdit}>
              <Pencil size={13} aria-hidden="true" />
              编辑信息
            </Button>
          </div>
        </div>
      </div>

      <div className="mt-3.5 flex flex-wrap items-center gap-x-7 gap-y-2 border-t border-[var(--yak-components-panel-border)] px-6 py-2.5 text-xs max-md:px-4">
        <Meta icon={<UserRound size={13} aria-hidden="true" />} label="负责人">
          {ownerName ?? (
            <span className="font-medium text-[var(--yak-components-badge-warning-text)]">
              未指派
            </span>
          )}
        </Meta>

        <Meta icon={<Layers size={13} aria-hidden="true" />} label="数据域">
          {domainName ? (
            <Badge tone="info">{domainName}</Badge>
          ) : (
            <span className="text-[var(--yak-components-muted-text)]">未分类</span>
          )}
        </Meta>

        <Meta icon={<Clock3 size={13} aria-hidden="true" />} label="最近采集">
          {detail.lastSeenTime}
        </Meta>

        <div className={`${META_CLASS} gap-2`}>
          <span className={META_LABEL_CLASS}>业务标签</span>
          {detail.tags.length === 0 ? (
            <span className="text-[var(--yak-components-muted-text)]">暂无标签</span>
          ) : (
            <span className="flex flex-wrap items-center gap-1">
              {detail.tags.map((tag) => (
                <Badge key={tag.id} tone="info">
                  {tag.name}
                </Badge>
              ))}
            </span>
          )}
        </div>
      </div>
    </Card>
  );
}
