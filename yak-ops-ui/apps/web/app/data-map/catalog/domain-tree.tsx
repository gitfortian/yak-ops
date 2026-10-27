import { Button } from "@yak-ops/yak-ui";

import type { DomainCategory } from "../shared/lookups";

export type DomainSelection =
  | { kind: "all" }
  | { kind: "unclassified" }
  | { kind: "domain"; id: string };

interface Props {
  categories: DomainCategory[];
  loading: boolean;
  selected: DomainSelection;
  onSelect: (selection: DomainSelection) => void;
}

const isSelected = (selected: DomainSelection, candidate: DomainSelection) =>
  selected.kind === candidate.kind &&
  (selected.kind !== "domain" || (candidate.kind === "domain" && selected.id === candidate.id));

/**
 * The catalog's only tree: the planning taxonomy filtered to data domains, plus 未分类, which is
 * `domain_id IS NULL` rather than a synthetic node. Yak UI has no tree control.
 */
export default function DomainTree({ categories, loading, selected, onSelect }: Props) {
  const entry = (label: string, candidate: DomainSelection) => (
    <li key={label}>
      <Button
        size="small"
        variant={isSelected(selected, candidate) ? "secondary" : "ghost"}
        className="w-full justify-start"
        aria-current={isSelected(selected, candidate) ? true : undefined}
        onClick={() => onSelect(candidate)}
      >
        <span className="truncate">{label}</span>
      </Button>
    </li>
  );

  return (
    <nav aria-label="数据域" className="space-y-4">
      <ul className="space-y-1">
        {entry("全部资产", { kind: "all" })}
        {entry("未分类", { kind: "unclassified" })}
      </ul>

      {loading && <p className="px-1 text-xs text-[var(--yak-components-muted-text)]">加载中</p>}

      {categories.map((category) => (
        <section key={category.id}>
          <p className="mb-1 truncate px-1 text-xs text-[var(--yak-components-muted-text)]">
            {category.name}
          </p>
          <ul className="space-y-1">
            {category.domains.map((domain) => (
              <li key={domain.id}>
                <Button
                  size="small"
                  variant={
                    selected.kind === "domain" && selected.id === domain.id ? "secondary" : "ghost"
                  }
                  className="w-full justify-start pl-3"
                  aria-current={
                    selected.kind === "domain" && selected.id === domain.id ? true : undefined
                  }
                  onClick={() => onSelect({ kind: "domain", id: domain.id })}
                >
                  <span className="truncate">{domain.name}</span>
                </Button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </nav>
  );
}
