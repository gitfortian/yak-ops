# User Preference Domain

Status: Active

Scope:
- cross-device user preference persistence
- product favorites
- usage counters for frequent-item ranking

## Principle

User Preference answers:

```text
Who am I?      → Security
Where am I?    → Workspace
What do I prefer? → User Preference
```

Preferences belong to the authenticated user and are persisted on the server. Browser storage may cache product state, but it is not the source of truth for preferences that must survive logout or device changes.

## Current Scenes

```text
PRODUCT_MENU
→ explicit product favorite / pin state

DATASOURCE_CREATE_TYPE
→ datasource type usage count + last used time
```

Scene item keys are stable product identifiers, not presentation data.

Examples:

```text
PRODUCT_MENU / data-integration
PRODUCT_MENU / management

DATASOURCE_CREATE_TYPE / MYSQL
DATASOURCE_CREATE_TYPE / POSTGRE_SQL
DATASOURCE_CREATE_TYPE / ORACLE
```

## HTTP Contract

```text
GET  /api/v1/user-preferences?scene={scene}
PUT  /api/v1/user-preferences/{scene}/{itemKey}/favorite
POST /api/v1/user-preferences/{scene}/{itemKey}/use
```

Favorite body:

```json
{
  "favorite": true
}
```

The caller never submits `userId`. Boot reads the authenticated identity and passes it to the User Preference Service.

## Persistence

`yak_ops_user_preference` stores one row per:

```text
user_id + scene + item_key
```

State:
- `favorite`
- `sort_order`
- `use_count`
- `last_used_time`

Favorite ordering is assigned when an item becomes a favorite. Removing a favorite resets its favorite order but preserves usage history.

Usage recording uses an atomic upsert so repeated or concurrent usage events cannot lose increments.

## Product Boundary

PR1 provides persistence and API only.

Product-menu star UI and datasource frequent-type UI are separate follow-up capabilities and must consume this API rather than duplicating preference storage.
