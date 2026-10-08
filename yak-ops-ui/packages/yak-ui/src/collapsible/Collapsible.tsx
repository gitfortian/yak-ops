import { Collapsible as BaseCollapsible } from "@base-ui/react/collapsible";
import type { ReactNode } from "react";

import { cn } from "../cn";

export const Collapsible = BaseCollapsible.Root;
export const CollapsibleTrigger = BaseCollapsible.Trigger;

export type CollapsiblePanelProps = Omit<BaseCollapsible.Panel.Props, "className"> & {
  className?: string;
};
export function CollapsiblePanel({ className, ...props }: CollapsiblePanelProps) {
  return (
    <BaseCollapsible.Panel
      className={cn(
        "h-[var(--collapsible-panel-height)] overflow-hidden transition-[height] duration-150 data-starting-style:h-0 data-ending-style:h-0 motion-reduce:transition-none",
        className,
      )}
      {...props}
    />
  );
}

export interface CollapseSectionProps {
  id?: string;
  title: ReactNode;
  extra?: ReactNode;
  children: ReactNode;
  defaultOpen?: boolean;
  open?: boolean;
  disabled?: boolean;
  onOpenChange?: (open: boolean) => void;
  className?: string;
  triggerClassName?: string;
  contentClassName?: string;
}

function CollapseSectionChevron() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 16 16"
      fill="none"
      className="size-3.5 shrink-0 text-[var(--yak-components-collapse-section-icon)] transition-transform duration-150 group-data-[panel-open]:rotate-90 motion-reduce:transition-none"
    >
      <path
        d="M6 3.5 10.5 8 6 12.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function CollapseSection({
  id,
  title,
  extra,
  children,
  defaultOpen = true,
  open,
  disabled,
  onOpenChange,
  className,
  triggerClassName,
  contentClassName,
}: CollapseSectionProps) {
  return (
    <Collapsible
      id={id}
      defaultOpen={defaultOpen}
      open={open}
      disabled={disabled}
      onOpenChange={onOpenChange ? (nextOpen) => onOpenChange(nextOpen) : undefined}
      className={className}
    >
      <CollapsibleTrigger
        className={cn(
          "group flex h-9 w-full cursor-pointer items-center gap-2 rounded border border-[var(--yak-components-collapse-section-border)] bg-[var(--yak-components-collapse-section-bg)] px-3 text-left text-[var(--yak-components-collapse-section-text)] transition-colors duration-150 hover:bg-[var(--yak-components-collapse-section-bg-hover)] focus-visible:border-[var(--yak-color-primary)] focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
          triggerClassName,
        )}
      >
        <CollapseSectionChevron />
        <span className="min-w-0 flex-1 truncate text-sm font-semibold">{title}</span>
        {extra ? <span className="shrink-0">{extra}</span> : null}
      </CollapsibleTrigger>

      <CollapsiblePanel>
        <div className={cn("pt-3", contentClassName)}>{children}</div>
      </CollapsiblePanel>
    </Collapsible>
  );
}
