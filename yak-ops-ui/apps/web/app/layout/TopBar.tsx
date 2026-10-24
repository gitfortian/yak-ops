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
    <header className="relative z-50 flex h-10 shrink-0 items-center bg-[#14171a] text-white">
      <div className="flex h-full w-48 shrink-0 items-center gap-2 border-r border-white/10 px-2">
        <button
          type="button"
          aria-label={launcherOpen ? "关闭全部产品" : "打开全部产品"}
          aria-expanded={launcherOpen}
          aria-haspopup="menu"
          aria-controls="global-product-launcher"
          className="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-white/65 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
          onClick={onToggleLauncher}
        >
          {launcherOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>
        <Link
          to={productPath}
          className="flex min-w-0 items-center"
          onClick={() => { if (launcherOpen) onToggleLauncher(); }}
        >
          <img
            src="/logo.png"
            alt="Yak Ops"
            draggable={false}
            className="h-6 w-auto select-none object-contain brightness-0 invert"
          />
        </Link>
      </div>
      <div className="flex min-w-0 flex-1 items-center justify-between px-4">
        <div className="flex min-w-0 items-center gap-3 text-xs">
          <span className="truncate font-semibold text-white/90">{productLabel}</span>
          {showWorkspaceSwitcher ? (
            <>
              <span className="h-3 w-px shrink-0 bg-white/15" />
              <WorkspaceSwitcher />
            </>
          ) : null}
        </div>
        <AccountMenu />
      </div>
    </header>
  );
}
