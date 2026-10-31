import { ArrowRight, Database, Plus, Shapes } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { DATA_MAP_BASE } from "../shared/labels";

const STEPS = [
  {
    icon: Database,
    title: "接入数据源",
    description: "登记 MySQL、StarRocks 等数据源",
    to: "/data-source",
    action: "去接入",
  },
  {
    icon: Plus,
    title: "发起采集",
    description: "把库表结构同步到资产目录",
    to: `${DATA_MAP_BASE}/tasks/new`,
    action: "发起采集",
  },
  {
    icon: Shapes,
    title: "归类与打标",
    description: "划分数据域、指派负责人、绑定标签",
    to: `${DATA_MAP_BASE}/catalog`,
    action: "去资产目录",
  },
];

/** 空工作区的首启引导：接入 → 采集 → 治理，三步直达对应入口。 */
export default function FirstRunGuide() {
  const navigate = useNavigate();

  return (
    <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
      {STEPS.map((step, index) => {
        const Icon = step.icon;
        return (
          <button
            key={step.title}
            type="button"
            onClick={() => navigate(step.to)}
            className="group relative cursor-pointer rounded-[var(--yak-radius-md)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-datamap-surface)] p-4 text-left shadow-[var(--yak-datamap-card-shadow)] transition-all duration-200 hover:-translate-y-0.5 hover:border-[var(--yak-color-primary-ring)] hover:shadow-[var(--yak-datamap-card-shadow-hover)]"
          >
            <div className="flex items-center gap-2.5">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                <Icon size={15} aria-hidden="true" />
              </span>
              <span className="text-[11px] font-bold text-[var(--yak-components-muted-text)] [font-variant-numeric:tabular-nums]">
                STEP {index + 1}
              </span>
            </div>
            <div className="mt-3 text-sm font-bold text-[var(--yak-components-panel-text)]">
              {step.title}
            </div>
            <p className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
              {step.description}
            </p>
            <span className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-[var(--yak-color-primary)]">
              {step.action}
              <ArrowRight
                size={12}
                aria-hidden="true"
                className="transition-transform duration-150 group-hover:translate-x-0.5"
              />
            </span>
          </button>
        );
      })}
    </div>
  );
}
