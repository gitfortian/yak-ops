# Platform Rules

Scope:
- `yak-ops-platform/**`

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`
- DAO changes also load `/yak-ops-dao/DAO_RULES.md`
- Schema changes also load `/yak-ops-dao/FLYWAY_RULES.md`
- HTTP contract changes also load `/CONTROLLER_RULES.md`

## Ownership

`yak-ops-platform` is the single physical Maven module for supporting product context:

- Security / User: authentication, login/logout, current identity and user management.
- Workspace: Workspace lifecycle, membership and membership validation.
- User Preference: authenticated-user-scoped favorites and usage signals.

These capabilities share one Maven module because they are small and do not need independent dependency, release or runtime isolation. They remain separate Java package and Service boundaries.

## Package Boundary

```text
io.yak.ops.security    → authentication / user
io.yak.ops.workspace   → workspace / membership
io.yak.ops.preference  → user preference
```

Package boundaries do not imply Maven child modules. Do not recreate a Maven module for each small capability only for organizational symmetry.

## Stable Service Boundary

Boot depends only on stable capability Services:

```text
LoginService
UserService
WorkspaceService
UserPreferenceService
```

Persistence stays in `yak-ops-dao`. HTTP Controllers and final runtime assembly stay in `yak-ops-boot`.

## Capability Rules

- Security → `SECURITY_RULES.md`
- Workspace → `WORKSPACE_RULES.md`
- User Preference → `USER_PREFERENCE_RULES.md`

## Must

- keep Security, Workspace and User Preference as distinct packages and Services.
- keep current identity sourced from the trusted authentication runtime.
- keep Workspace membership separate from Security user persistence.
- keep User Preference user-scoped and independent from Workspace.
- keep Platform as supporting product context, not a product-business dumping ground.

## Must Not

- add Datasource, Data Sync, YakFlow or datasource plugin behavior to Platform.
- use Platform as a generic home for scheduler, notification, audit, cache or miscellaneous utilities.
- recreate `yak-ops-platform-security`, `yak-ops-platform-workspace` or `yak-ops-platform-user-preference` without a concrete isolation requirement.
- merge Workspace roles into a generic Security RBAC model.
- merge preference columns or JSON blobs into the Security user table.
