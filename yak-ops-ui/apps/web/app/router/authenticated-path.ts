/**
 * The single authority on which post-login destinations survive a round trip through the login page.
 *
 * <p>`resolveReturnTo` drops anything it does not recognise and sends the caller to the datasource
 * list instead. A route added without its path or prefix therefore loses the destination silently,
 * so this module is imported by the router rather than duplicated there, and a unit test pins the
 * deep links that must survive.
 */

export const DEFAULT_AUTHENTICATED_PATH = "/data-source";

export const AUTHENTICATED_PATHS: ReadonlySet<string> = new Set([
  "/data-modeling/warehouse-planning",
  "/data-modeling/data-standards",
  "/data-modeling/metrics",
  "/data-modeling/dimension-modeling",
  "/data-modeling/dimension-modeling/reverse",
  "/data-modeling/dimensions",
  "/data-modeling/logical-models",
  "/data-modeling/logical-models/new",
  "/data-source",
  "/data-sync",
  "/data-sync/new",
  "/data-governance/data-map/overview",
  "/data-governance/data-map/catalog",
  "/data-governance/data-map/crawls",
  "/data-governance/data-map/personal",
  "/operations",
  "/operations/offline-tasks",
  "/operations/realtime-tasks",
  "/management/users",
  "/management/workspaces",
]);

/** Prefixes cover detail routes whose identifiers cannot be enumerated in a set. */
export const AUTHENTICATED_PREFIXES = [
  "/data-sync/",
  "/data-governance/",
  "/operations/",
  "/data-modeling/logical-models/",
] as const;

export const isAuthenticatedPath = (pathname: string) =>
  AUTHENTICATED_PATHS.has(pathname) ||
  AUTHENTICATED_PREFIXES.some((prefix) => pathname.startsWith(prefix));

/** Returns the destination to navigate to; unknown or cross-origin requests fall back. */
export const resolveReturnTo = (requested: string | null, origin: string) => {
  if (!requested) return DEFAULT_AUTHENTICATED_PATH;

  try {
    const destination = new URL(requested, origin);
    if (destination.origin !== origin || !isAuthenticatedPath(destination.pathname)) {
      return DEFAULT_AUTHENTICATED_PATH;
    }
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return DEFAULT_AUTHENTICATED_PATH;
  }
};
