import type { ProductNavigationChild } from "./navigation";

/**
 * A child owns its own route and everything beneath it, so a detail page keeps the list entry lit.
 * The slash is required: `/catalog-archive` must not count as being under `/catalog`.
 */
const owns = (pathname: string, path: string) =>
  pathname === path || pathname.startsWith(`${path}/`);

export const isNavigationChildActive = (child: ProductNavigationChild, pathname: string) =>
  owns(pathname, child.path) ||
  (child.activePrefixes ?? []).some((prefix) => owns(pathname, prefix));
