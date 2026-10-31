import { Link } from "react-router-dom";

import { DATA_MAP_BASE } from "../../shared/labels";

const TABS = [
  { key: "tasks", label: "采集任务", to: `${DATA_MAP_BASE}/tasks` },
  { key: "runs", label: "实例记录", to: `${DATA_MAP_BASE}/crawls` },
] as const;

/**
 * Switcher between the two questions of metadata collection: which tasks exist, and what each run
 * actually produced. Each tab is its own route, so a filtered feed can be shared as a link.
 */
export default function TaskTabs({ active }: { active: (typeof TABS)[number]["key"] }) {
  return (
    <div className="flex shrink-0 items-center gap-1 border-b border-[var(--yak-components-control-border)] px-3">
      {TABS.map((tab) => {
        const current = tab.key === active;
        return (
          <Link
            key={tab.key}
            to={tab.to}
            aria-current={current ? "page" : undefined}
            className={`-mb-px border-b-2 px-3 py-2.5 text-xs transition-colors ${
              current
                ? "border-[var(--yak-color-primary)] font-medium text-[var(--yak-components-panel-text)]"
                : "border-transparent text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]"
            }`}
          >
            {tab.label}
          </Link>
        );
      })}
    </div>
  );
}
