import { Badge } from "@yak-ops/yak-ui";
import { Box, Folder, Layers, Workflow } from "lucide-react";
import { useMemo, type ReactNode } from "react";
import {
  PUBLICATION_STATUS_LABEL,
  type PublicationStatus,
} from "@/service/data-modeling/dimension-modeling/publication";
import type {
  ConceptDimension,
  LogicalModel,
} from "@/service/data-modeling/dimension-modeling/types";
import type { Layer } from "@/service/data-modeling/warehouse-planning/layers";
import type { PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import {
  countByModelKey,
  layerCounts,
  publicationCounts,
  sameNode,
  type Selection,
} from "./workbench-views";

type TreeIcon = "root" | "folder" | "dimension" | "process";

const TREE_ICONS = {
  root: Layers,
  folder: Folder,
  dimension: Box,
  process: Workflow,
} as const;

type DotTone = "info" | "success" | "danger" | "warning" | "neutral";

// Literal classes so Tailwind keeps every variant; a dynamic string would drop them from the build.
const DOT_CLASS: Record<DotTone, string> = {
  info: "bg-[var(--yak-components-badge-info-bg)]",
  success: "bg-[var(--yak-components-badge-success-bg)]",
  danger: "bg-[var(--yak-components-badge-danger-bg)]",
  warning: "bg-[var(--yak-components-badge-warning-bg)]",
  neutral: "bg-[var(--yak-components-badge-neutral-bg)]",
};

function TreeItem(props: {
  label: string;
  meta?: ReactNode;
  level: number;
  active?: boolean;
  icon?: TreeIcon;
  dot?: DotTone;
  onClick: () => void;
}) {
  const Icon = TREE_ICONS[props.icon ?? "folder"];
  return (
    <button
      type="button"
      onClick={props.onClick}
      style={{ paddingLeft: 8 + props.level * 10 }}
      className={[
        "mx-1 flex h-8 w-[calc(100%-0.5rem)] items-center gap-2 rounded-lg pr-2 text-left text-[13px] transition-colors duration-150",
        props.active
          ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
          : "text-[var(--yak-color-text-primary)] hover:bg-[var(--yak-color-surface-muted)]",
      ].join(" ")}
    >
      {props.dot ? (
        <span className={`size-2 shrink-0 rounded-full ${DOT_CLASS[props.dot]}`} />
      ) : (
        <Icon
          size={15}
          strokeWidth={1.75}
          className={
            "shrink-0 " +
            (props.active
              ? "text-[var(--yak-color-primary)]"
              : "text-[var(--yak-components-muted-text)]")
          }
        />
      )}
      <span className="truncate">{props.label}</span>
      {props.meta != null && props.meta !== "" && (
        <span className="ml-auto shrink-0 text-xs font-normal text-[var(--yak-components-muted-text)]">
          {props.meta}
        </span>
      )}
    </button>
  );
}

function GroupLabel({ children, level }: { children: ReactNode; level: number }) {
  return (
    <div
      className="pb-1 pt-2.5 text-[11px] font-medium tracking-[0.04em] text-[var(--yak-components-muted-text)]"
      style={{ paddingLeft: 8 + level * 12 }}
    >
      {children}
    </div>
  );
}

const countLabel = (count: number) => `${count} 表`;

/** What the right-hand panel says about the selected node. */
export interface PanelContent {
  title: string;
  /** Owner labels the header shows as tags; only the dimension node has any. */
  tags?: string[];
  /** The node's own business description. Scope nodes omit it: the title already names the scope. */
  description?: string;
}

export function nodeHeading(selection: Selection): PanelContent {
  switch (selection.kind) {
    case "ROOT":
      return { title: "全部逻辑模型" };
    case "LAYER":
      return { title: `${selection.layer.enCode} · ${selection.layer.cnName}` };
    case "CATEGORY":
      return { title: selection.category.name };
    case "DOMAIN":
      return { title: `${selection.category.name} / ${selection.domain.name}` };
    case "DIMENSION": {
      const dimension = selection.dimension;
      return {
        title: dimension.name,
        tags: [dimension.domainName ?? dimension.datamartName, dimension.code].filter(
          (tag): tag is string => Boolean(tag),
        ),
        description: dimension.description ?? undefined,
      };
    }
    case "PROCESS":
      return { title: `${selection.domain.name} / ${selection.process.name}` };
    case "DATAMART":
      return { title: selection.datamart.name };
    case "PUBLICATION":
      return {
        title: selection.status
          ? `发布状态：${PUBLICATION_STATUS_LABEL[selection.status]}`
          : "未发布的模型",
      };
  }
}

/** 分层视图：one row per warehouse layer, so each physical tier shows how full it is. */
export function LayerTreePanel(props: {
  layers: Layer[];
  models: LogicalModel[];
  selected: Selection;
  onSelect: (selection: Selection) => void;
}) {
  const counts = useMemo(
    () => layerCounts(props.layers, props.models),
    [props.layers, props.models],
  );
  return (
    <nav aria-label="分层目录">
      <TreeItem
        label="全部逻辑模型"
        icon="root"
        level={0}
        active={props.selected.kind === "ROOT"}
        meta={countLabel(props.models.length)}
        onClick={() => props.onSelect({ kind: "ROOT" })}
      />
      <GroupLabel level={0}>数仓分层</GroupLabel>
      {props.layers.map((layer) => (
        <TreeItem
          key={layer.id}
          label={`${layer.enCode} · ${layer.cnName}${layer.enabled ? "" : "（未启用）"}`}
          icon="folder"
          level={0}
          active={sameNode(props.selected, { kind: "LAYER", layer })}
          meta={countLabel(counts.get(layer.id) ?? 0)}
          onClick={() => props.onSelect({ kind: "LAYER", layer })}
        />
      ))}
    </nav>
  );
}

/**
 * 业务视图：the planning skeleton with mounted model counts. Model rows stay in the right-hand
 * table — the tree names scopes, the list names models.
 */
export function BizTreePanel(props: {
  tree: PlanningTree;
  dimensions: ConceptDimension[];
  models: LogicalModel[];
  selected: Selection;
  onSelect: (selection: Selection) => void;
}) {
  const byCategory = useMemo(
    () => countByModelKey(props.models, (m) => m.categoryId),
    [props.models],
  );
  const byDomain = useMemo(() => countByModelKey(props.models, (m) => m.domainId), [props.models]);
  const byProcess = useMemo(
    () => countByModelKey(props.models, (m) => m.processId),
    [props.models],
  );
  const byDimension = useMemo(
    () => countByModelKey(props.models, (m) => m.dimensionId),
    [props.models],
  );
  const byDatamart = useMemo(
    () => countByModelKey(props.models, (m) => m.datamartId),
    [props.models],
  );
  const dimensionItem = (dimension: ConceptDimension, level: number) => (
    <TreeItem
      key={dimension.id}
      label={dimension.name}
      icon="dimension"
      level={level}
      active={sameNode(props.selected, { kind: "DIMENSION", dimension })}
      meta={countLabel(byDimension.get(dimension.id) ?? 0)}
      onClick={() => props.onSelect({ kind: "DIMENSION", dimension })}
    />
  );
  return (
    <nav aria-label="业务目录">
      <TreeItem
        label="全部逻辑模型"
        icon="root"
        level={0}
        active={props.selected.kind === "ROOT"}
        meta={countLabel(props.models.length)}
        onClick={() => props.onSelect({ kind: "ROOT" })}
      />
      <GroupLabel level={0}>公共层</GroupLabel>
      {props.tree.categories.map((category) => (
        <div key={category.id}>
          <TreeItem
            label={category.name}
            level={0}
            active={sameNode(props.selected, { kind: "CATEGORY", category })}
            meta={countLabel(byCategory.get(category.id) ?? 0)}
            onClick={() => props.onSelect({ kind: "CATEGORY", category })}
          />
          {category.children.map((domain) => (
            <div key={domain.id}>
              <TreeItem
                label={domain.name}
                level={1}
                active={sameNode(props.selected, { kind: "DOMAIN", category, domain })}
                meta={countLabel(byDomain.get(domain.id) ?? 0)}
                onClick={() => props.onSelect({ kind: "DOMAIN", category, domain })}
              />
              <GroupLabel level={2}>维度</GroupLabel>
              {props.dimensions
                .filter((dimension) => dimension.domainId === domain.id)
                .map((dimension) => dimensionItem(dimension, 2))}
              <GroupLabel level={2}>业务过程</GroupLabel>
              {domain.children.map((process) => (
                <TreeItem
                  key={process.id}
                  label={process.name}
                  icon="process"
                  level={2}
                  active={sameNode(props.selected, {
                    kind: "PROCESS",
                    category,
                    domain,
                    process,
                  })}
                  meta={countLabel(byProcess.get(process.id) ?? 0)}
                  onClick={() => props.onSelect({ kind: "PROCESS", category, domain, process })}
                />
              ))}
            </div>
          ))}
        </div>
      ))}
      <GroupLabel level={0}>应用层</GroupLabel>
      {props.tree.datamarts.map((datamart) => (
        <div key={datamart.id}>
          <TreeItem
            label={datamart.name}
            level={0}
            active={sameNode(props.selected, { kind: "DATAMART", datamart })}
            meta={countLabel(byDatamart.get(datamart.id) ?? 0)}
            onClick={() => props.onSelect({ kind: "DATAMART", datamart })}
          />
          {props.dimensions
            .filter((dimension) => dimension.datamartId === datamart.id)
            .map((dimension) => dimensionItem(dimension, 1))}
        </div>
      ))}
    </nav>
  );
}

const PUBLISH_GROUPS: {
  status: PublicationStatus | null;
  label: string;
  tone: "info" | "success" | "danger" | "warning" | "neutral";
}[] = [
  { status: null, label: "未发布", tone: "neutral" },
  { status: "PENDING", label: PUBLICATION_STATUS_LABEL.PENDING, tone: "info" },
  { status: "SUCCEEDED", label: PUBLICATION_STATUS_LABEL.SUCCEEDED, tone: "success" },
  { status: "FAILED", label: PUBLICATION_STATUS_LABEL.FAILED, tone: "danger" },
  { status: "UNKNOWN", label: PUBLICATION_STATUS_LABEL.UNKNOWN, tone: "warning" },
];

/** 发布视图：the same models grouped by the outcome of their newest publication. */
export function PublishTreePanel(props: {
  models: LogicalModel[];
  selected: Selection;
  onSelect: (selection: Selection) => void;
}) {
  const counts = useMemo(() => publicationCounts(props.models), [props.models]);
  return (
    <nav aria-label="发布状态目录">
      <TreeItem
        label="全部逻辑模型"
        icon="root"
        level={0}
        active={props.selected.kind === "ROOT"}
        meta={countLabel(props.models.length)}
        onClick={() => props.onSelect({ kind: "ROOT" })}
      />
      <GroupLabel level={0}>发布状态</GroupLabel>
      {PUBLISH_GROUPS.map((group) => {
        const count = group.status ? (counts.byStatus.get(group.status) ?? 0) : counts.none;
        return (
          <TreeItem
            key={group.status ?? "none"}
            label={group.label}
            dot={group.tone}
            level={0}
            active={sameNode(props.selected, { kind: "PUBLICATION", status: group.status })}
            meta={<Badge tone={group.tone}>{count}</Badge>}
            onClick={() => props.onSelect({ kind: "PUBLICATION", status: group.status })}
          />
        );
      })}
    </nav>
  );
}
