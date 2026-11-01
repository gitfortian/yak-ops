package io.yak.ops.business.datamap.comment;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;

import java.util.List;

/**
 * Reads and writes the comment thread of one data map asset.
 *
 * <p>Comments are flat with one level of replies. Mentions are resolved at creation time against
 * active Workspace members. Read markers advance per user per asset.
 */
public interface DataMapCommentService {

    /** One top-level comment with its nested replies, projected for the caller. */
    record CommentThread(DataMapCommentEntity comment, List<DataMapCommentEntity> replies) {}

    /** Page wrapper carrying the caller's unread comment count. */
    record CommentPage(PageData<CommentThread> page, long unreadCount) {}

    /**
     * Reads one page of top-level comments with replies nested, newest first.
     *
     * @param workspaceId current Workspace
     * @param targetType anchored asset kind
     * @param targetId anchored asset identity
     * @param userId authenticated caller (drives unread count)
     * @param pageNo one-based page number
     * @param pageSize rows per page
     * @return one page with nested replies and the caller's unread count
     */
    CommentPage page(String workspaceId, String targetType, String targetId,
            String userId, int pageNo, int pageSize);

    /**
     * Creates a top-level comment or a reply.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user id
     * @param actorName actor display name
     * @param targetType anchored asset kind
     * @param targetId anchored asset identity
     * @param body comment body; @DisplayName tokens resolve to mention ids
     * @param parentId parent comment id, or null for a top-level comment
     * @return the created comment
     */
    DataMapCommentEntity create(String workspaceId, String actor, String actorName,
            String targetType, String targetId, String body, String parentId);

    /**
     * Deletes a comment; the author or a Workspace manager may delete.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user id
     * @param commentId comment identity
     */
    void delete(String workspaceId, String actor, String commentId);

    /**
     * Advances the caller's read marker for one asset to now.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated user id
     * @param targetId anchored asset identity
     */
    void advanceRead(String workspaceId, String userId, String targetId);
}
