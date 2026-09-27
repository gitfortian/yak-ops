import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
  listUserPreferences,
  updateUserPreferenceFavorite,
  type UserPreferenceRecord,
} from "@/service/preference";

import AllProductMenu from "./AllProductMenu";
import { GLOBAL_PRODUCT_MENU } from "./navigation";

const FIRST_LEVEL_CLOSE_DELAY_MS = 36;
const PRODUCT_MENU_SCENE = "PRODUCT_MENU" as const;

type ProductLauncherProps = {
  open: boolean;
  onClose: () => void;
};

const upsertPreference = (preferences: UserPreferenceRecord[], nextPreference: UserPreferenceRecord) => {
  const exists = preferences.some((preference) => preference.itemKey === nextPreference.itemKey);
  if (!exists) return [...preferences, nextPreference];
  return preferences.map((preference) =>
    preference.itemKey === nextPreference.itemKey ? nextPreference : preference,
  );
};

export default function ProductLauncher({ open, onClose }: ProductLauncherProps) {
  const [allProductsOpen, setAllProductsOpen] = useState(false);
  const [firstLevelVisible, setFirstLevelVisible] = useState(open);
  const [preferences, setPreferences] = useState<UserPreferenceRecord[]>([]);
  const [favoritesLoading, setFavoritesLoading] = useState(false);
  const [favoriteMutatingIds, setFavoriteMutatingIds] = useState<Set<string>>(
    () => new Set(),
  );

  useEffect(() => {
    if (open) {
      setFirstLevelVisible(true);
      return;
    }

    setAllProductsOpen(false);
    const timer = window.setTimeout(() => setFirstLevelVisible(false), FIRST_LEVEL_CLOSE_DELAY_MS);

    return () => window.clearTimeout(timer);
  }, [open]);

  useEffect(() => {
    if (!open) return;

    let active = true;
    setFavoritesLoading(true);
    void listUserPreferences(PRODUCT_MENU_SCENE)
      .then((nextPreferences) => {
        if (active) setPreferences(nextPreferences);
      })
      .catch(() => undefined)
      .finally(() => {
        if (active) setFavoritesLoading(false);
      });

    return () => {
      active = false;
    };
  }, [open]);

  const favoritePreferences = preferences
    .filter((preference) => preference.favorite)
    .sort((left, right) => left.sortOrder - right.sortOrder);
  const favoriteProductIds = new Set(favoritePreferences.map((preference) => preference.itemKey));
  const favoriteProducts = favoritePreferences.flatMap((preference) => {
    const product = GLOBAL_PRODUCT_MENU.find((item) => item.id === preference.itemKey);
    return product ? [product] : [];
  });

  const toggleFavorite = async (productId: string) => {
    if (favoriteMutatingIds.has(productId)) return;

    const previousPreference = preferences.find((preference) => preference.itemKey === productId);
    const nextFavorite = !Boolean(previousPreference?.favorite);
    const nextSortOrder = nextFavorite
      ? Math.max(
          0,
          ...preferences.filter((item) => item.favorite).map((item) => item.sortOrder),
        ) + 1
      : 0;
    const optimisticPreference: UserPreferenceRecord = {
      scene: PRODUCT_MENU_SCENE,
      itemKey: productId,
      favorite: nextFavorite,
      sortOrder: nextSortOrder,
      useCount: previousPreference?.useCount ?? 0,
      lastUsedTime: previousPreference?.lastUsedTime,
    };

    setPreferences((current) => upsertPreference(current, optimisticPreference));
    setFavoriteMutatingIds((current) => new Set(current).add(productId));

    try {
      const saved = await updateUserPreferenceFavorite(PRODUCT_MENU_SCENE, productId, {
        favorite: nextFavorite,
      });
      setPreferences((current) => upsertPreference(current, saved));
    } catch {
      setPreferences((current) => {
        if (previousPreference) return upsertPreference(current, previousPreference);
        return current.filter((preference) => preference.itemKey !== productId);
      });
    } finally {
      setFavoriteMutatingIds((current) => {
        const next = new Set(current);
        next.delete(productId);
        return next;
      });
    }
  };

  const secondLevelOpen = open && allProductsOpen;

  return (
    <>
      <button
        type="button"
        aria-label="关闭产品菜单"
        tabIndex={-1}
        className={[
          "fixed inset-x-0 bottom-0 top-10 z-20 cursor-default border-0 bg-transparent p-0",
          open ? "pointer-events-auto" : "pointer-events-none",
        ].join(" ")}
        onClick={onClose}
      />

      <div
        className={[
          "fixed bottom-0 left-0 top-10 z-40 w-[220px] transform-gpu transition-transform ease-in-out motion-reduce:transition-none",
          open ? "duration-300" : "duration-[220ms]",
          firstLevelVisible ? "translate-x-0" : "-translate-x-[220px]",
          open ? "" : "pointer-events-none",
        ].join(" ")}
      >
        <aside
          id="global-product-launcher"
          aria-label="全局产品一级菜单"
          aria-hidden={!open}
          className={[
            "user-menu flex h-full w-[220px] flex-col bg-[#15181c] text-xs text-white",
            "box-border",
            firstLevelVisible ? "user-menu-active" : "",
          ].join(" ")}
        >
          <button
            type="button"
            aria-expanded={secondLevelOpen}
            aria-controls="all-product-menu"
            tabIndex={open ? 0 : -1}
            className={[
              "view-all mb-[7px] flex h-10 w-full shrink-0 cursor-pointer items-center border-0 px-0 text-left text-[#d3d3d3] transition-colors",
              "hover:bg-[#282b2e] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/20",
              secondLevelOpen ? "bg-[#1c1e21] text-white" : "bg-transparent",
            ].join(" ")}
            onClick={() => setAllProductsOpen((value) => !value)}
          >
            <span className="text ml-[14px] min-w-0 flex-1 truncate">全部产品</span>
            <span className="right mr-3 flex shrink-0 items-center">
              <ChevronRight
                className={secondLevelOpen ? "h-3.5 w-3.5 text-white" : "h-3.5 w-3.5 text-white/65"}
                strokeWidth={1.8}
              />
            </span>
          </button>

          <div className="item-list min-h-0 flex-1 overflow-y-auto px-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {favoriteProducts.length > 0 ? (
              favoriteProducts.map((item) => {
                const Icon = item.icon;

                return (
                  <Link
                    key={item.id}
                    to={item.path}
                    tabIndex={open ? 0 : -1}
                    className="item group my-0.5 flex h-8 w-full cursor-pointer items-center rounded px-1.5 text-[#cbced3] transition-colors hover:bg-[#282b2e] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                    onClick={onClose}
                  >
                    <Icon
                      className="h-3.5 w-3.5 shrink-0 text-[#5d6064] transition-colors group-hover:text-white"
                      strokeWidth={1.8}
                    />
                    <span className="product-name ml-2 min-w-0 flex-1 truncate">{item.label}</span>
                  </Link>
                );
              })
            ) : (
              <div className="px-2 py-3 text-[11px] leading-5 text-white/35">
                {favoritesLoading
                  ? "正在加载常用产品…"
                  : "暂无常用产品，可在全部产品中标星添加"}
              </div>
            )}
          </div>
        </aside>

        <AllProductMenu
          open={secondLevelOpen}
          favoriteProductIds={favoriteProductIds}
          favoriteMutatingIds={favoriteMutatingIds}
          onNavigate={onClose}
          onToggleFavorite={(productId) => void toggleFavorite(productId)}
        />
      </div>
    </>
  );
}
