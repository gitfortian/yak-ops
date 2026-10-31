import { Link } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import { locationLabel, TABLE_STATUS_LABEL, TABLE_STATUS_TONE, tablePath } from "../shared/labels";
import { useDomainTaxonomy, useOwnerNames } from "../shared/lookups";

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

/** Card presentation of the catalog result set, mirroring the list columns. */
export default function AssetCards({ records, loading }: Props) {
  const { domainNames } = useDomainTaxonomy();
  const { names: ownerNames } = useOwnerNames();

  if (loading) {
    return (
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        {[0, 1, 2, 3, 4, 5].map((key) => (
          <div
            key={key}
            className="h-28 animate-pulse rounded-[var(--yak-radius-control-medium)] bg-[var(--yak-components-skeleton-bg)]"
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
            to={tablePath(record.id)}
            className="block rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-3.5 transition-shadow hover:border-[var(--yak-color-primary)] hover:shadow-[var(--yak-components-panel-shadow)]"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <div
                  className="truncate text-sm font-medium [color:var(--yak-color-primary)]"
                  title={record.name}
                >
                  {record.name}
                </div>
                <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
                  {locationLabel(record.database, record.schema)}
                </div>
              </div>
              <span
                className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${TONE_CLASS[TABLE_STATUS_TONE[record.status]]}`}
              >
                {TABLE_STATUS_LABEL[record.status]}
              </span>
            </div>
            <p className="mt-2 line-clamp-2 min-h-10 text-xs leading-5 text-[var(--yak-components-panel-text)]">
              {record.description || record.remarks || "— 缺少描述 —"}
            </p>
            <div className="mt-2.5 flex flex-wrap items-center gap-1.5 text-[11px] text-[var(--yak-components-muted-text)]">
              <span className="rounded-full bg-[var(--yak-components-table-header-bg)] px-2 py-0.5">
                {domain ?? "未分类"}
              </span>
              <span className="rounded-full bg-[var(--yak-components-table-header-bg)] px-2 py-0.5">
                {owner ?? "未指派"}
              </span>
              {record.tags.slice(0, 2).map((tag) => (
                <span
                  key={tag.valueId}
                  className="rounded-full bg-[var(--yak-components-badge-info-bg)] px-2 py-0.5 text-[var(--yak-components-badge-info-text)]"
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
