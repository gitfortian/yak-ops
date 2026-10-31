import { Link } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import { locationLabel, tablePath } from "./labels";
import { useOwnerNames } from "./lookups";
import UserAvatar from "./user-avatar";

/**
 * Accountable-owner cell: the initial avatar beside the display name, or an
 * explicit 未指派 placeholder that keeps the column width stable.
 */
export default function OwnerCell({ ownerId }: { ownerId: string | null }) {
  const { names } = useOwnerNames();
  const name = ownerId ? names.get(ownerId) : undefined;
  if (!name) {
    return <span className="text-xs text-[var(--yak-components-muted-text)]">未指派</span>;
  }
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-[var(--yak-components-panel-text)]">
      <UserAvatar name={name} />
      {name}
    </span>
  );
}

/** Asset name cell: mono-style name over the namespace sub-line, linking to the detail page. */
export function AssetNameCell({ record, mono = false }: { record: TableSummary; mono?: boolean }) {
  return (
    <div className="min-w-0">
      <Link
        to={tablePath(record.id)}
        className={`block truncate text-[13px] font-semibold text-[var(--yak-color-primary)] hover:underline ${
          mono ? "font-mono text-[12.5px]" : ""
        }`}
        title={record.name}
      >
        {record.name}
      </Link>
      <div className="mt-0.5 flex items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
        <span className="truncate">{locationLabel(record.database, record.schema)}</span>
      </div>
    </div>
  );
}
