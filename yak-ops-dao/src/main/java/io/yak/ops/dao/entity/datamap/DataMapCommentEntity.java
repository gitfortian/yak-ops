package io.yak.ops.dao.entity.datamap;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** One comment (top-level or reply) anchored to a data map asset. */
@Getter
@Setter
public class DataMapCommentEntity extends BaseEntity {

    /** Workspace owning this comment. */
    private String workspaceId;

    /** Kind of anchored asset; DATA_MAP_TABLE in this release. */
    private String targetType;

    /** Identity of the anchored asset. */
    private String targetId;

    /** Parent comment id for replies; null for top-level comments. */
    private String parentId;

    /** Comment body; plain text with @DisplayName mention tokens. */
    private String body;

    /** Author user id. */
    private String authorId;

    /** Denormalized author display name so a rename does not rewrite history. */
    private String authorName;

    /** Comma-separated mentioned user ids resolved at creation; null when none. */
    private String mentionIds;

    /** Count of direct replies; maintained by the service on reply creation and cascade deletion. */
    private Integer replyCount;
}
