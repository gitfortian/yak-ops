# Navigation ↔ Menu Authorization Contract

Yak Ops navigation and Yak Security menu authorization are separate models that share one stable identifier: `menuCode`.

## Contract

- `route.id`, `path`, `title`, `component`, icon and grouping are frontend UI metadata. They may change without changing RBAC.
- `menuCode` is the stable database-backed authorization identifier. Do not derive it from `route.id` or URL shape.
- Yak Ops business menu codes are declared in `src/constants/securityMenuCodes.ts` and correspond to the Yak Ops security catalog (currently reconciled by `V2006__reconcile_menu_permission_catalog.sql`).
- System-management menu codes in the same constants file are framework-owned and correspond to the Yak Security menu catalog. Yak Ops must not duplicate those rows in its own Flyway migrations.
- Every protected route that represents a menu page declares a `menuCode` explicitly.
- Hidden detail/editor routes normally omit `menuCode` and inherit the nearest parent route's stable menu code.
- Hidden utility/public routes that are not database-backed menus may omit `menuCode`.

## Runtime authorization

Navigation visibility and direct-route access use the same `canAccessNavigationRoute` function. Access therefore requires the route's permission requirement and, when the backend exposes `menuCodes`, its resolved stable menu grant.

Compatibility rules:

1. `mode: public` keeps its existing public behavior; this contract does not convert public routes into protected routes.
2. `security:root` bypasses menu-grant checks as before.
3. `menuCodes === undefined` means an older backend has not exposed menu authorization yet, so the frontend keeps staggered rollout compatibility.
4. Once `menuCodes` is present, an empty list or a missing stable mapping denies protected menu access.
5. A hidden child route resolves the same menu code as its parent, so sidebar visibility and direct URL authorization cannot drift.

## Ownership boundary

This contract intentionally does not make TypeScript generate backend menu rows and does not make the frontend navigation tree database-driven. Each side keeps the metadata it owns while sharing stable menu codes.

A follow-up consistency check can mechanically compare protected visible navigation resources with the backend catalog and fail CI on missing, duplicate or invalid mappings. That enforcement is intentionally outside this PR.
