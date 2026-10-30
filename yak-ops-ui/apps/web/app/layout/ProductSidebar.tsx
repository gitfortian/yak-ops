import { ChevronDown } from "lucide-react";
import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";

import type { ProductNavigationItem } from "./navigation";
import { isNavigationChildActive } from "./navigation-active";

type ProductSidebarProps = {
  productLabel: string;
  navigation: ProductNavigationItem[];
};

export default function ProductSidebar({ productLabel, navigation }: ProductSidebarProps) {
  const { pathname } = useLocation();
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  return (
    <aside className="flex w-48 shrink-0 flex-col border-r border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)]">
      <div className="px-4 pb-2 pt-5 text-[11px] font-medium tracking-[0.04em] text-[var(--yak-components-muted-text)]">
        {productLabel}
      </div>

      <nav className="flex flex-col gap-1">
        {navigation.map((item, index) => {
          const previousGroupLabel = index > 0 ? navigation[index - 1]?.groupLabel : undefined;
          const showGroupLabel = Boolean(item.groupLabel && item.groupLabel !== previousGroupLabel);
          const Icon = item.icon;

          if (item.children) {
            const activeChild = item.children.some((child) =>
              isNavigationChildActive(child, pathname),
            );
            const open = openGroups[item.path] ?? activeChild;

            return (
              <div key={item.path}>
                <button
                  type="button"
                  aria-expanded={open}
                  aria-controls={`nav-group-${index}`}
                  onClick={() => setOpenGroups((state) => ({ ...state, [item.path]: !open }))}
                  className="mx-2 flex h-9 w-[calc(100%-1rem)] cursor-pointer items-center gap-2.5 rounded-[6px] border-0 bg-transparent px-2.5 text-left text-[14px] transition-colors hover:bg-[var(--yak-color-surface-muted)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary-ring)]"
                >
                  <Icon
                    className={`h-4 w-4 shrink-0 ${
                      activeChild
                        ? "text-[var(--yak-color-primary)]"
                        : "text-[var(--yak-color-text-secondary)]"
                    }`}
                    strokeWidth={1.8}
                  />
                  <span
                    className={`min-w-0 flex-1 truncate ${
                      activeChild
                        ? "font-semibold text-[var(--yak-color-primary)]"
                        : "font-normal text-[var(--yak-color-text-primary)]"
                    }`}
                  >
                    {item.label}
                  </span>
                  <ChevronDown
                    className={`h-3.5 w-3.5 shrink-0 text-[var(--yak-color-text-secondary)] transition-transform ${
                      open ? "" : "-rotate-90"
                    }`}
                    strokeWidth={1.8}
                  />
                </button>

                {open ? (
                  <div
                    id={`nav-group-${index}`}
                    className="ml-[26px] border-l border-[var(--yak-color-border)] pl-3"
                  >
                    {item.children.map((child) => (
                      <NavLink
                        key={child.path}
                        to={child.path}
                        className={[
                          "flex h-8 items-center text-[13px] transition-colors",
                          isNavigationChildActive(child, pathname)
                            ? "font-semibold text-[var(--yak-color-primary)]"
                            : "font-normal text-[var(--yak-color-text-secondary)] hover:text-[var(--yak-color-text-primary)]",
                        ].join(" ")}
                      >
                        {child.label}
                      </NavLink>
                    ))}
                  </div>
                ) : null}
              </div>
            );
          }

          return (
            <div key={item.path}>
              {showGroupLabel ? (
                <div className="px-4 pb-1.5 pt-5 text-[11px] font-medium tracking-[0.04em] text-[var(--yak-components-muted-text)]">
                  {item.groupLabel}
                </div>
              ) : null}
              <NavLink
                to={item.path}
                className={({ isActive }) =>
                  [
                    "mx-2 flex h-9 items-center gap-2.5 rounded-[6px] px-2.5 text-[14px] transition-colors",
                    isActive
                      ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]"
                      : "font-normal text-[var(--yak-color-text-primary)] hover:bg-[var(--yak-color-surface-muted)]",
                  ].join(" ")
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon
                      className={`h-4 w-4 shrink-0 transition-colors ${
                        isActive
                          ? "text-[var(--yak-color-primary)]"
                          : "text-[var(--yak-color-text-secondary)]"
                      }`}
                      strokeWidth={1.8}
                    />
                    <span className="truncate">{item.label}</span>
                  </>
                )}
              </NavLink>
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
