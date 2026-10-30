import { Star } from "lucide-react";
import { Link } from "react-router-dom";

import { ALL_PRODUCT_GROUPS } from "./navigation";

type AllProductMenuProps = {
  open: boolean;
  favoriteProductIds: ReadonlySet<string>;
  favoriteMutatingIds: ReadonlySet<string>;
  onNavigate: () => void;
  onToggleFavorite: (productId: string) => void;
};

export default function AllProductMenu({
  open,
  favoriteProductIds,
  favoriteMutatingIds,
  onNavigate,
  onToggleFavorite,
}: AllProductMenuProps) {
  return (
    <section
      id="all-product-menu"
      aria-label="全部产品二级菜单"
      aria-hidden={!open}
      className={[
        "absolute inset-y-0 left-full max-w-[calc(100vw-220px)] overflow-hidden bg-[var(--yak-color-surface-inverse-raised)] text-xs text-[var(--yak-color-text-inverse-secondary)]",
        "transition-[width] ease-in-out motion-reduce:transition-none",
        open ? "w-[765px] duration-[240ms]" : "w-0 duration-[170ms] pointer-events-none",
      ].join(" ")}
    >
      <div className="h-full w-[765px] max-w-[calc(100vw-220px)] overflow-y-auto">
        <div className="min-h-full px-12 pt-4">
          {ALL_PRODUCT_GROUPS.map((group) => (
            <section key={group.id} className="flex min-h-12 border-b border-[var(--yak-color-border-inverse)] py-2">
              <h3 className="m-0 w-40 shrink-0 py-2 text-xs font-normal leading-8 text-[var(--yak-color-text-inverse-primary)]">
                {group.label}
              </h3>

              <div className="flex min-w-0 flex-1 flex-wrap content-start gap-x-5">
                {group.products.map((product) => {
                  const Icon = product.icon;
                  const favorite = favoriteProductIds.has(product.id);
                  const mutating = favoriteMutatingIds.has(product.id);

                  return (
                    <div
                      key={product.id}
                      className="group my-0.5 flex h-8 w-60 items-center rounded px-2 text-[var(--yak-color-text-inverse-secondary)] transition-colors hover:bg-[var(--yak-color-surface-inverse-hover)] hover:text-[var(--yak-color-text-inverse-primary)]"
                    >
                      <Link
                        to={product.path}
                        tabIndex={open ? 0 : -1}
                        className="flex h-full min-w-0 flex-1 cursor-pointer items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                        onClick={onNavigate}
                      >
                        <Icon
                          className="h-3.5 w-3.5 shrink-0 text-[var(--yak-color-text-inverse-muted)] transition-colors group-hover:text-[var(--yak-color-text-inverse-primary)]"
                          strokeWidth={1.8}
                        />
                        <span className="ml-2 min-w-0 flex-1 truncate">{product.label}</span>
                      </Link>

                      <button
                        type="button"
                        aria-label={favorite ? `取消收藏${product.label}` : `收藏${product.label}`}
                        aria-pressed={favorite}
                        disabled={mutating}
                        tabIndex={open ? 0 : -1}
                        className={[
                          "ml-1 flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded transition-[background-color,color,opacity]",
                          "hover:bg-white/8 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20 disabled:cursor-default disabled:opacity-45",
                          favorite
                            ? "text-[var(--yak-color-accent-favorite)] opacity-100"
                            : "text-white/40 opacity-0 group-hover:opacity-100 focus-visible:opacity-100",
                        ].join(" ")}
                        onClick={() => onToggleFavorite(product.id)}
                      >
                        <Star
                          className="h-3.5 w-3.5"
                          fill={favorite ? "currentColor" : "none"}
                          strokeWidth={1.8}
                        />
                      </button>
                    </div>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      </div>
    </section>
  );
}
