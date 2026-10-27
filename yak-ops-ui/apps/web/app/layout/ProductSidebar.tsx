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
    <aside className="flex w-48 shrink-0 flex-col border-r border-[#e6e8eb] bg-[#FAFAFA]">
      <div className="px-3 pb-2 pt-4 text-[11px] font-medium text-[#8b929e]">{productLabel}</div>

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
                  className="flex h-8 w-full cursor-pointer items-center gap-3.5 border-0 bg-transparent px-[14px] text-left text-[13px] transition-colors hover:bg-[#f2f2f2] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--yak-color-primary)]"
                >
                  <Icon
                    className={`h-4 w-4 shrink-0 ${
                      activeChild ? "text-[var(--yak-color-primary)]" : "text-[#26282c]"
                    }`}
                    strokeWidth={1.8}
                  />
                  <span
                    className={`min-w-0 flex-1 truncate ${
                      activeChild
                        ? "font-semibold text-[var(--yak-color-primary)]"
                        : "font-normal text-[#26282c]"
                    }`}
                  >
                    {item.label}
                  </span>
                  <ChevronDown
                    className={`h-3.5 w-3.5 shrink-0 text-[#8b929e] transition-transform ${
                      open ? "" : "-rotate-90"
                    }`}
                    strokeWidth={1.8}
                  />
                </button>

                {open ? (
                  <div
                    id={`nav-group-${index}`}
                    className="ml-[30px] border-l border-[#e6e8eb] pl-3"
                  >
                    {item.children.map((child) => (
                      <NavLink
                        key={child.path}
                        to={child.path}
                        className={[
                          "flex h-8 items-center text-[13px] transition-colors",
                          isNavigationChildActive(child, pathname)
                            ? "font-semibold text-[var(--yak-color-primary)]"
                            : "font-normal text-[#5f6368] hover:text-[#26282c]",
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
                <div className="px-[14px] pb-1 pt-2 text-[11px] font-medium text-[#8b929e]">
                  {item.groupLabel}
                </div>
              ) : null}
              <NavLink
                to={item.path}
                className={({ isActive }) =>
                  [
                    "flex h-8 items-center gap-3.5 border-r-2 px-[14px] text-[13px] text-[#26282c] transition-colors",
                    isActive
                      ? "border-[var(--yak-color-primary)] bg-[var(--yak-color-primary-subtle)] font-semibold"
                      : "border-transparent font-normal hover:bg-[#f2f2f2]",
                  ].join(" ")
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon
                      className={`h-4 w-4 shrink-0 transition-colors ${
                        isActive ? "text-[var(--yak-color-primary)]" : "text-[#26282c]"
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
