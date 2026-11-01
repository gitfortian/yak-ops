# FYOIP-20: Asset Comments — Collaboration Threads on Data Map Assets

Status: Discussion

Discussion: https://github.com/gitfortian/yak-ops/pull/70

Decision: N/A

Implementation: Opens with migration `V13__v1_15_0.sql` in the same PR as this proposal.

Release: N/A until released

Related FLIPs: N/A

## Motivation

The data map answers "what is this asset" but not "what do people think about it". A data engineer discovers a stale comment, a business analyst questions a metric's caliber, a reviewer asks the owner to clarify a field's meaning — today these conversations happen in chat tools where the context is lost after the thread scrolls away.

OpenMetadata and DataHub both treat asset activity as first-class: OpenMetadata renders a conversation panel per asset; DataHub links each asset to its discussion feed. The pattern is the same: a thread anchored to an asset, with a lightweight awareness of what is new since the reader last looked.

This proposal adds a Workspace-scoped comment thread to every data map asset. A comment is a row in a flat list anchored to one asset; replies are comments whose `parent_id` points to another comment on the same asset. `@` tokens inside a body are a frontend rendering concern: the server stores plain text, and awareness of new activity is a per-user, per-asset unread count rather than per-mention delivery.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Scope | Every comment belongs to exactly one Workspace via its asset. |
| Anchoring | A comment anchors to `(target_type, target_id)`; this release only accepts `DATA_MAP_TABLE` and requires the anchored asset to exist, but the type column is open for later targets. |
| Replies | A reply is a comment whose `parent_id` points to a top-level comment on the same asset. One level of nesting only; a reply cannot have a reply. |
| Mentions | A body may contain `@<name>` tokens. The server stores plain text and performs no member resolution in this release; the frontend renders token-shaped text as accent chips. A rename or an unresolvable token changes nothing on the server. |
| Read state | A per-user `last_read_time` marks the newest comment a user has seen. Unread count = comments newer than `last_read_time`, excluding the user's own. Opening the thread advances the marker; there is no per-mention notification. |
| Access | Every Workspace member may read, create and reply. Only the comment's author may delete it; manager deletion requires a role-resolution capability that does not exist in this scope. |
| Ordering | Top-level comments newest first; replies oldest first within their parent. |
| Retention | Comments inherit the lifetime of their anchored asset row. The catalog has no asset-deletion flow in this release; when one is introduced, its owner must delete anchored comments as part of that flow (the flat anchor makes this a single keyed delete). |
| Editing | Comments are immutable after creation (edit = delete + re-post). This avoids edit-history complexity in the first release. |

### REST

New endpoints under `/api/v1/data-map/comments`, all Workspace-scoped:

| Method and path | Purpose |
| --- | --- |
| `GET /?targetType=&targetId=&pageNo=&pageSize=` | Paged top-level comments with their replies nested (newest first), in the standard `PagingData` shape. |
| `GET /unread-count?targetType=&targetId=` | The caller's unread count for one asset; lets the tab label show a badge without loading the thread. |
| `POST /` | Create a top-level comment or reply (`body`, `targetType`, `targetId`, `parentId?`). |
| `DELETE /{commentId}` | Delete a comment authored by the caller. Deleting a top-level comment cascades its replies. |
| `POST /read?targetId=` | Advance the caller's `last_read_time` for one asset to now. |

Error codes in `io.yak.ops.common.enums.datamap.DataMapErrorCode`, continuing the data map block:

| Code | Use |
| --- | --- |
| `19723 COMMENT_TOO_LONG` | Body exceeds 2000 characters. |
| `19724 COMMENT_PARENT_MISMATCH` | The parent comment is missing, is itself a reply, or anchors to a different asset. |
| `19725 COMMENT_NOT_FOUND` | The comment id does not exist in the caller's Workspace. |
| `19726 COMMENT_FORBIDDEN` | The caller is not the comment's author. |
| `19727 COMMENT_TARGET_INVALID` | The target type is not supported in this release, or the anchored asset does not exist. |

An empty body is rejected as `1001 PARAM_NOT_VALID` from the common block.

### Persisted formats

The migration is additive. Two tables:

`yak_ops_data_map_comment`

- `id`, `workspace_id`, `target_type`, `target_id`
- `parent_id VARCHAR(64) NULL` — set for replies; the parent must be a top-level comment on the same asset
- `body TEXT NOT NULL` — plain text with `@` tokens rendered client-side
- `author_id`, `author_name VARCHAR(128)` — denormalized author display name
- `reply_count INT NOT NULL DEFAULT 0`
- `create_time`, `update_time`, `create_by`, `update_by` — the module's standard audit columns
- keys: `PRIMARY KEY (id)`, `KEY (workspace_id, target_type, target_id, create_time)`, `KEY (parent_id)`, `FOREIGN KEY (parent_id) ... ON DELETE CASCADE` so deleting a top-level comment removes its replies
- no mention column: tokens stay in the body text

`yak_ops_data_map_comment_read`

- `id`, `workspace_id`, `target_id`, `user_id`, `last_read_time`
- keys: `PRIMARY KEY (id)`, `UNIQUE (workspace_id, target_id, user_id)`; upserted with `ON DUPLICATE KEY UPDATE`

### Frontend

The asset detail page gains a 评论 Tab (alongside 基础信息 / 字段 / 数据预览 / 变更记录 / 治理). The tab renders:

- A comment composer (plain textarea, Ctrl+Enter submits).
- Top-level comments newest first with reply counts; expanding a thread shows replies oldest first with a reply action that pre-fills `@<author>`.
- `@` token rendering: token-shaped text after a word boundary becomes a non-interactive accent chip; plain text stays plain.
- Unread badge on the 评论 tab label, driven by the unread-count endpoint; opening the tab advances `last_read_time` and clears the badge.
- Delete button on the author's own comments only.

## Proposed Changes

### 1. Flat list + one-level replies, not a tree

A tree structure (arbitrary depth) requires recursive queries and makes unread counting ambiguous. One level of replies covers the chat pattern (question → answer → follow-up) without a recursive CTE. A reply that needs its own thread is a new top-level comment quoting the parent.

### 2. Denormalized author name

The author's display name is stored on the comment row. Joining to the user table on every read adds no value — names are display-only and a user's rename should not retroactively change historical comments.

### 3. Plain-text mention tokens, no server resolution

`@` tokens stay in the body as plain text. Resolving tokens against Workspace members would add a member-lookup dependency, a mention column and a notification channel for a feature whose only current consumer is a visual chip. When real mention delivery is wanted, it needs a notification design of its own; this release deliberately keeps the server side free of it.

### 4. Per-user read marker, not per-comment read flags

Storing one `last_read_time` per (user, asset) is a single row upsert; per-comment read flags would be N rows per user per asset. Unread count = `COUNT(*) WHERE create_time > last_read_time AND author_id != caller`.

## Compatibility, Deprecation, and Migration Plan

- Additive migration; no existing table is modified.
- No existing endpoint changes. The data map detail page gains a new tab; the comments endpoints follow the repository's standard `PagingData` response shape.
- No downgrade path; the supported rollback is restoring the pre-upgrade backup.

## Test Plan

- **Create and list**: service tests cover trimming, audit-field fill via the shared base entity, and page reads that nest replies under their parent with correct ordering.
- **Reply nesting**: a reply to a missing parent, to a reply, or to a comment anchored to a different asset is rejected with `COMMENT_PARENT_MISMATCH` before any insert; a valid reply increments the parent's reply counter.
- **Unread count**: the marker upsert and the unread projection are covered at the mapper boundary; user A posts, user B reads, user A posts again — user B's unread count is 1.
- **Delete**: a missing id returns `COMMENT_NOT_FOUND`; a non-author returns `COMMENT_FORBIDDEN`; the author's delete of a reply decrements the parent counter, and a top-level delete relies on the foreign-key cascade declared by the migration.
- **Target validation**: an unsupported target type and a missing anchored asset are rejected with `COMMENT_TARGET_INVALID`.

## Rejected Alternatives

1. **Arbitrary-depth reply tree**: recursive queries and ambiguous unread counting for a pattern that one level covers.
2. **Per-comment read flags**: N rows per user per asset vs one row per user per asset.
3. **Server-side mention resolution with a mention column**: a member-lookup dependency and a dead column for a rendering-only consumer; revisit together with a real notification design.
4. **Server-rendered mention HTML**: the server stores plain text; the frontend owns how `@` tokens render.
5. **Notification service integration**: mentions surface at most as the per-asset unread badge; a full notification center is a separate product.
6. **Rich-text or Markdown bodies**: plain text with `@` tokens is sufficient; sanitization is free.
7. **Manager deletion**: deleting other members' comments needs a Workspace-role resolution capability that has no owner in this scope; the author-only rule is enforced on the server.
