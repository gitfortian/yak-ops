# User Preference Rules

Scope:
- `yak-ops-business/yak-ops-business-user-preference/**`
- User Preference persistence and HTTP changes

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- `/yak-ops-business/BUSINESS_RULES.md`
- `/docs/capabilities/user-preference/README.md`
- DAO changes load `/yak-ops-dao/DAO_RULES.md`
- HTTP changes load `/CONTROLLER_RULES.md`

## Ownership

User Preference owns user-scoped product preferences that must survive logout and synchronize across devices.

Security owns authenticated identity. User Preference receives the trusted current user ID from Boot and never accepts a caller-supplied user ID.

The current scenes are:
- `PRODUCT_MENU`: explicit favorite / pin state for product navigation.
- `DATASOURCE_CREATE_TYPE`: usage history used to derive frequent datasource types.

## Stable Service Boundary

```text
UserPreferenceService
→ UserPreferenceServiceImpl
```

Boot depends on `UserPreferenceService`, never its implementation or Repository.

## Persistence Contract

One row represents one user preference item:

```text
userId + scene + itemKey
→ favorite / sortOrder
→ useCount / lastUsedTime
```

The unique key is `user_id + scene + item_key`.

Explicit favorites and usage signals share the same row but remain independent:
- `PRODUCT_MENU` supports explicit favorite mutation.
- `DATASOURCE_CREATE_TYPE` supports usage recording.
- changing `favorite` must not reset usage counters.
- recording usage must not change favorite state or favorite order.
- unsupported scene/action combinations fail fast instead of silently creating meaningless preference data.

## Must

- derive user ownership from authenticated identity.
- keep preference data user-scoped, not Workspace-scoped.
- keep scene and item keys stable and independent from display labels, routes or icons.
- make usage recording atomic at the database boundary.
- return only preference state required by callers; never expose persistence IDs or user IDs.
- fall back to empty results when a user has no preferences in a scene.

## Must Not

- add favorite columns or preference JSON blobs to `yak_security_user`.
- accept `userId` from request DTOs or query parameters.
- store UI labels, route paths, icon names or datasource display names as preference identity.
- make User Preference depend on Workspace.
- create a generic settings JSON store in this capability.
