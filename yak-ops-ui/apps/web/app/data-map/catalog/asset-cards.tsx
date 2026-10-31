import { Table2 } from "lucide-react";
import { Link } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import { TABLE_STATUS_LABEL, TABLE_STATUS_TONE } from "../shared/labels";
import { useDomainTaxonomy, useOwnerNames } from "../shared/lookups";
import DomainBadge from "../shared/domain-badge";
import UserAvatar from "../shared/user-avatar";

interface Props {
  records: TableSummary[];
  loading: boolean;
}

const TONE_CLASS: Record<string, string> = {
  success:
    "bg-[var(--yak-components-badge-success-bg)] text-[var(--yak-components-badge-success-text)]",
  warning:
    "bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]",
  danger:
    "bg-[var(--yak-components-badge-danger-bg)] text-[var(--yak-components-badge-danger-text)]",
  info: "bg-[var(--yak-components-badge-info-bg)] text-[var(--yak-components-badge-info-text)]",
  neutral:
    "bg-[var(--yak-components-badge-neutral-bg)] text-[var(--yak-components-badge-neutral-text)]",
};

/** 卡片视图：与列表同构的资产卡片，悬停浮起。 */
export default function AssetCards({ records, loading }: Props) {
  const { domainNames } = useDomainTaxonomy();
  const { names: ownerNames } = useOwnerNames();

  if (loading) {
    return (
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        {[0, 1, 2, 3, 4, 5].map((key) => (
          <div
            key={key}
            className="h-32 animate-pulse rounded-[var(--yak-radius-md)] bg-[var(--yak-components-skeleton-bg)]"
          />
        ))}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      {records.map((record) => {
        const domain = record.domainId ? domainNames.get(record.domainId) : undefined;
        const owner = record.ownerId ? ownerNames.get(record.ownerId) : undefined;
        return (
          <Link
            key={record.id}
            to={`/data-governance/data-map/tables/${record.id}`}
            className="block rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 shadow-[var(--yak-datamap-card-shadow)] transition-all duration-200 hover:-translate-y-0.5 hover:border-[var(--yak-color-primary-ring)] hover:shadow-[var(--yak-datamap-card-shadow-hover)]"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex min-w-0 items-start gap-2">
                <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                  <Table2 size={13} aria-hidden="true" />
                </span>
                <div className="min-w-0">
                  <div
                    className="truncate font-mono text-[12.5px] font-semibold text-[var(--yak-color-primary)]"
                    title={record.name}
                  >
                    {record.name}
                  </div>
                  <div className="mt-0.5 truncate text-[11px] text-[var(--yak-components-muted-text)]">
                    {[record.database, record.schema].filter(Boolean).join(" / ") || "-"}
                  </div>
                </div>
              </div>
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${TONE_CLASS[TABLE_STATUS_TONE[record.status]]}`}
              >
                {TABLE_STATUS_LABEL[record.status]}
              </span>
            </div>
            <p className="mt-2.5 line-clamp-2 min-h-10 text-xs leading-5 text-[var(--yak-components-panel-text)]">
              {record.description || record.remarks || (
                <span className="text-[var(--yak-components-muted-text)]">— 缺少描述 —</span>
              )}
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-1.5">
              <DomainBadge id={record.domainId} name={domain} />
              <span className="inline-flex items-center gap-1 rounded-[4px] bg-[var(--yak-components-collapse-section-bg)] px-[7px] py-0.5 text-[11px] text-[var(--yak-components-panel-text)]">
                {owner ? (
                  <>
                    <UserAvatar name={owner} />
                    {owner}
                  </>
                ) : (
                  <span className="text-[var(--yak-components-muted-text)]">未指派</span>
                )}
              </span>
              {record.tags.slice(0, 2).map((tag) => (
                <span
                  key={tag.valueId}
                  className="rounded-[4px] bg-[var(--yak-components-collapse-section-bg)] px-[7px] py-0.5 text-[11px] font-medium text-[var(--yak-components-panel-text)]"
                >
                  {tag.valueName}
                </span>
              ))}
            </div>
          </Link>
        );
      })}
    </div>
  );
}
