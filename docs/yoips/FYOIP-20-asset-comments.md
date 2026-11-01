# FYOIP-20: Asset Comments — Collaboration Threads on Data Map Assets

Status: Draft

Discussion: N/A (this document)

Decision: N/A

Implementation: N/A

Release: N/A

Related FLIPs: N/A

## Motivation

The data map answers "what is this asset" but not "what do people think about it". A data engineer discovers a stale comment, a business analyst questions a metric's caliber, a reviewer asks the owner to clarify a field's meaning — today these conversations happen in chat tools where the context is lost after the thread scrolls away.

OpenMetadata and DataHub both treat asset activity as first-class: OpenMetadata renders a conversation panel per asset; DataHub links each asset to its discussion feed. The pattern is the same: a thread anchored to an asset, with mentions that notify the mentioned user.

This proposal adds a Workspace-scoped comment thread to every data map asset. A comment is a row in a flat list anchored to one asset; replies are comments whose `parent_id` points to another comment on the same asset. @mentions store the mentioned user's id and surface in a notification badge; they do not carry delivery guarantees beyond an unread marker on the mentioned user's next page load.

## Public Interfaces

### Product behavior

| Item | Contract |
| --- | --- |
| Scope | Every comment belongs to exactly one Workspace via its asset. |
| Anchoring | A comment anchors to `(target_type, target_id)`; this release only emits `DATA_MAP_TABLE`, but the type column is open for later targets. |
| Replies | A reply is a comment whose `parent_id` points to a top-level comment on the same asset. One level of nesting only; a reply cannot have a reply. |
| Mentions | A comment body may contain `@<displayName>` tokens. The server resolves exact display-name matches against active Workspace members and stores the resolved user ids in a mention column; unresolved tokens are left as plain text. |
| Read state | A per-user `last_read_time` marks the newest comment a user has seen. Unread count = comments newer than `last_read_time`, excluding the user's own. |
| Access | Every Workspace member may read, create, reply and resolve. The author and Workspace managers may delete their own or any comment. |
| Ordering | Top-level comments newest first; replies oldest first within their parent. |
| Retention | Comments are retained for the life of the asset; deleting an asset (data map cascade) cascades its comments. |
| Editing | Comments are immutable after creation (edit = delete + re-post). This avoids edit-history complexity in the first release. |

### REST

New endpoints under `/api/v1/data-map/comments`, all Workspace-scoped:

| Method and path | Purpose |
| --- | --- |
| `GET /?targetType=&targetId=` | Paged top-level comments with their replies nested (newest first), including unread count for the caller. |
| `POST /` | Create a top-level comment or reply (`body`, `targetType`, `targetId`, `parentId?`, `mentionIds?`). |
| `DELETE /{commentId}` | Delete a comment (author or Workspace manager). Replies cascade. |
| `POST /read` | Advance the caller's `last_read_time` for one asset to now. |

Error codes in `io.yak.ops.common.enums.datamap.DataMapErrorCode`:

| Code | Use |
| --- | --- |
| `20101 COMMENT_NOT_FOUND` | The comment id does not exist in the caller's Workspace. |
| `20102 COMMENT_FORBIDDEN` | The caller is not the author and not a Workspace manager. |
| `20103 COMMENT_TOO_LONG` | Body exceeds 2000 characters. |
| `20104 COMMENT_PARENT_MISMATCH` | The parent comment belongs to a different asset. |

### Persisted formats

The migration is additive (the next version number at implementation time). Two tables:

`yak_ops_data_map_comment`

- `id`, `workspace_id`, `target_type`, `target_id`
- `parent_id VARCHAR(64) NULL` — set for replies; the parent must be a top-level comment on the same asset
- `body TEXT NOT NULL` — plain text with `@DisplayName` tokens
- `author_id`, `author_name VARCHAR(128)` — denormalized author display name
- `mention_ids VARCHAR(1024) NULL` — comma-separated mentioned user ids resolved at creation
- `reply_count INT NOT NULL DEFAULT 0`
- `create_time`, `update_time`
- keys: `PRIMARY KEY (id)`, `KEY (workspace_id, target_type, target_id, create_time)`, `KEY (parent_id)`

`yak_ops_data_map_comment_read`

- `id`, `workspace_id`, `target_id`, `user_id`, `last_read_time`
- keys: `PRIMARY KEY (id)`, `UNIQUE (workspace_id, target_id, user_id)`

### Frontend

The asset detail page gains a 评论 Tab (alongside 基础信息 / 字段 / 数据预览 / 变更记录 / 治理). The tab renders:

- A comment composer (textarea + @user autocomplete triggered by typing `@` + a mention chip preview).
- Top-level comments newest first with reply counts; expanding a thread shows replies oldest first with a reply composer.
- @mention rendering: matched `@DisplayName` tokens become clickable user chips (avatar + name) linking to the mentioned user; plain text stays plain.
- Unread badge on the 评论 tab label; opening the tab advances `last_read_time`.
- Delete button (author or manager only) with a confirmation dialog.

The field editor's description column already exists and is editable; no additional work is needed for the "字段描述" request beyond what the editor provides.

## Proposed Changes

### 1. Flat list + one-level replies, not a tree

A tree structure (arbitrary depth) requires recursive queries and makes unread counting ambiguous. One level of replies covers the chat pattern (question → answer → follow-up) without a recursive CTE. A reply that needs its own thread is a new top-level comment quoting the parent.

### 2. Denormalized author name

The author's display name is stored on the comment row. Joining to the user table on every read adds no value — names are display-only and a user's rename should not retroactively change historical comments.

### 3. Mention resolution at creation time

`@DisplayName` tokens are resolved against active Workspace members when the comment is created. The resolved ids go into `mention_ids`; the body stays as plain text. This means a rename after the fact does not break old mentions, and the mention list is queryable without parsing bodies at read time.

### 4. Per-user read marker, not per-comment read flags

Storing one `last_read_time` per (user, asset) is a single row update; per-comment read flags would be N rows per user per asset. Unread count = `COUNT(*) WHERE create_time > last_read_time AND author_id != caller`.

## Compatibility, Deprecation, and Migration Plan

- Additive migration; no existing table is modified.
- No existing endpoint changes. The data map detail page gains a new tab.
- No downgrade path; the supported rollback is restoring the pre-upgrade backup.

## Test Plan

- **Create and list**: create three comments and one reply; list returns the reply nested under its parent with correct ordering.
- **Mention resolution**: create a comment with `@系统管理员` and `@不存在的用户`; the first resolves into mention_ids, the second stays plain text.
- **Unread count**: user A posts, user B reads (advance read marker), user A posts again; user B's unread count is 1.
- **Reply nesting**: a reply cannot have a reply (`COMMENT_PARENT_MISMATCH`); a reply to a different asset's comment returns `COMMENT_PARENT_MISMATCH`.
- **Delete**: author deletes a top-level comment; replies cascade; a non-author non-manager gets `COMMENT_FORBIDDEN`.
- **Workspace isolation**: a foreign-Workspace comment id returns `COMMENT_NOT_FOUND`.

## Rejected Alternatives

1. **Arbitrary-depth reply tree**: recursive queries and ambiguous unread counting for a pattern that one level covers.
2. **Per-comment read flags**: N rows per user per asset vs one row per user per asset.
3. **Server-rendered mention HTML**: the server stores plain text; the frontend owns how `@DisplayName` renders.
4. **Notification service integration**: mentions surface as an unread badge on the tab; a full notification center is a separate product.
5. **Rich-text or Markdown bodies**: plain text with `@DisplayName` tokens is sufficient; sanitization is free.

## What an implementation must not add

1. A notification center or push channel.
2. Comment editing or edit history.
3. Reactions, emoji or likes.
4. Cross-asset threading or a global comment feed.
5. File attachments or images in comments.
