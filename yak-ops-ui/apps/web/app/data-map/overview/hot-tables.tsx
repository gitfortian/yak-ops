import { useNavigate } from "react-router-dom";

import type { TableSummary } from "@/service/data-map/types";

import OwnerCell, { AssetNameCell } from "../shared/cells";
import DomainBadge from "../shared/domain-badge";
import HeatBar from "../shared/heat-bar";
import { relativeTime } from "../shared/labels";
import { useDomainTaxonomy } from "../shared/lookups";

const LIMIT = 5;

const HEAD = "px-3 py-2.5 text-left text-xs font-medium text-[var(--yak-components-muted-text)]";
const CELL = "px-3 py-2.5 align-middle";

function rankClass(rank: number) {
  if (rank === 1) {
    return "bg-[var(--yak-datamap-badge-gold-bg)] text-[var(--yak-datamap-badge-gold-text)]";
  }
  if (rank === 2) {
    return "bg-[var(--yak-components-badge-info-bg)] text-[var(--yak-components-badge-info-text)]";
  }
  if (rank === 3) {
    return "bg-[var(--yak-datamap-badge-purple-bg)] text-[var(--yak-datamap-badge-purple-text)]";
  }
  return "text-[var(--yak-components-muted-text)]";
}

/** 热门资产表：按访问热度排序，展示排名、数据域、负责人与真实热度值。 */
export default function HotTables({
  records,
  loading,
}: {
  records: TableSummary[];
  loading: boolean;
}) {
  const navigate = useNavigate();
  const { domainNames } = useDomainTaxonomy();
  const maxHeat = Math.max(1, ...records.map((record) => record.viewCount ?? 0));

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-collapse-section-bg)]">
            <th className={`${HEAD} w-12 pl-4`}>#</th>
            <th className={HEAD}>资产</th>
            <th className={HEAD}>数据域</th>
            <th className={HEAD}>负责人</th>
            <th className={HEAD}>使用热度</th>
            <th className={`${HEAD} pr-4`}>最近访问</th>
          </tr>
        </thead>
        <tbody>
          {loading &&
            [0, 1, 2].map((key) => (
              <tr key={key} className="border-b border-[var(--yak-datamap-divider-soft)]">
                <td colSpan={6} className="px-4 py-3.5">
                  <div className="h-4 animate-pulse rounded bg-[var(--yak-components-skeleton-bg)]" />
                </td>
              </tr>
            ))}
          {!loading &&
            records.slice(0, LIMIT).map((record, index) => (
              <tr
                key={record.id}
                className="cursor-pointer border-b border-[var(--yak-datamap-divider-soft)] transition-colors last:border-b-0 hover:bg-[var(--yak-color-primary-subtle)]"
                onClick={() => navigate(`/data-governance/data-map/tables/${record.id}`)}
              >
                <td className={`${CELL} pl-4`}>
                  <span
                    className={`inline-flex size-5 items-center justify-center rounded text-[11px] font-bold [font-variant-numeric:tabular-nums] ${rankClass(index + 1)}`}
                  >
                    {index + 1}
                  </span>
                </td>
                <td className={CELL}>
                  <AssetNameCell record={record} mono />
                </td>
                <td className={CELL}>
                  <DomainBadge
                    id={record.domainId}
                    name={record.domainId ? domainNames.get(record.domainId) : undefined}
                  />
                </td>
                <td className={CELL}>
                  <OwnerCell ownerId={record.ownerId} />
                </td>
                <td className={CELL}>
                  <HeatBar value={record.viewCount ?? 0} max={maxHeat} />
                </td>
                <td className={`${CELL} pr-4 text-xs text-[var(--yak-components-muted-text)]`}>
                  {relativeTime(record.lastSeenTime)}
                </td>
              </tr>
            ))}
          {!loading && records.length === 0 && (
            <tr>
              <td
                colSpan={6}
                className="px-4 py-8 text-center text-xs text-[var(--yak-components-muted-text)]"
              >
                暂无访问记录，打开任意资产详情后这里会展示访问最多的表。
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
