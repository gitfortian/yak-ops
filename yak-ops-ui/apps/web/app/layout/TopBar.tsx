import { Menu, X } from "lucide-react";
import { Link } from "react-router-dom";

import AccountMenu from "./account-menu";
import WorkspaceSwitcher from "./workspace-switcher";

type TopBarProps = {
  launcherOpen: boolean;
  onToggleLauncher: () => void;
  productLabel: string;
  productPath: string;
  showWorkspaceSwitcher?: boolean;
};

/** The header composes navigation; the account and Workspace controls own their actions. */
export default function TopBar({
  launcherOpen,
  onToggleLauncher,
  productLabel,
  productPath,
  showWorkspaceSwitcher = false,
}: TopBarProps) {
  return (
    <header className="relative z-50 flex h-12 shrink-0 items-center border-b border-[var(--yak-color-border)] bg-[var(--yak-components-panel-bg)] text-[var(--yak-color-text-primary)]">
      <div className="flex h-full w-48 shrink-0 items-center gap-2 border-r border-[var(--yak-color-border)] px-3">
        <button
          type="button"
          aria-label={launcherOpen ? "关闭全部产品" : "打开全部产品"}
          aria-expanded={launcherOpen}
          aria-haspopup="menu"
          aria-controls="global-product-launcher"
          className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-[var(--yak-color-text-secondary)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-text-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary-ring)]"
          onClick={onToggleLauncher}
        >
          {launcherOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>
        <Link
          to={productPath}
          className="flex min-w-0 items-center"
          onClick={() => {
            if (launcherOpen) onToggleLauncher();
          }}
        >
          <img
            src="/logo.png"
            alt="Yak Ops"
            draggable={false}
            className="h-6 w-auto select-none object-contain [filter:var(--yak-logo-filter)]"
          />
        </Link>
      </div>
      <div className="flex min-w-0 flex-1 items-center justify-between px-4">
        <div className="flex min-w-0 items-center gap-3 text-xs">
          <span className="truncate font-semibold text-[var(--yak-color-text-primary)]">
            {productLabel}
          </span>
          {showWorkspaceSwitcher ? (
            <>
              <span className="h-3 w-px shrink-0 bg-[var(--yak-color-border)]" />
              <WorkspaceSwitcher />
            </>
          ) : null}
        </div>
        <AccountMenu />
      </div>
    </header>
  );
}
