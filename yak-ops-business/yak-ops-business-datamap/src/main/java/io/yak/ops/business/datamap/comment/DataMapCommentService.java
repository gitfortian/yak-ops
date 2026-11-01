package io.yak.ops.business.datamap.comment;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import java.util.List;

/**
 * Reads and writes the comment thread of one data map asset.
 *
 * <p>Comments are flat with one level of replies. Bodies are plain text; @tokens inside them are a
 * frontend rendering concern and carry no delivery guarantees. Read markers advance per user per
 * asset when the reader opens the thread.
 */
public interface DataMapCommentService {

    /** One top-level comment with its nested replies, projected for the caller. */
    record CommentThread(DataMapCommentEntity comment, List<DataMapCommentEntity> replies) {}

    /**
     * Reads one page of top-level comments with replies nested, newest first.
     *
     * @param workspaceId current Workspace
     * @param targetType anchored asset kind
     * @param targetId anchored asset identity
     * @param userId authenticated caller (drives unread count)
     * @param pageNo one-based page number
     * @param pageSize rows per page
     * @return one page with nested replies
     */
    PageData<CommentThread> page(
            String workspaceId, String targetType, String targetId, String userId, int pageNo, int pageSize);

    /**
     * Counts the caller's unread comments on one asset: comments newer than the caller's read
     * marker, excluding the caller's own.
     *
     * @param workspaceId current Workspace
     * @param targetType anchored asset kind
     * @param targetId anchored asset identity
     * @param userId authenticated caller
     * @return the unread count
     */
    long unreadCount(String workspaceId, String targetType, String targetId, String userId);

    /**
     * Creates a top-level comment or a reply. A reply's parent must be a top-level comment anchored
     * to the same asset; a top-level comment's target must be a supported, existing asset.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user id
     * @param actorName actor display name
     * @param targetType anchored asset kind
     * @param targetId anchored asset identity
     * @param body plain-text comment body
     * @param parentId parent comment id, or null for a top-level comment
     * @return the created comment
     */
    DataMapCommentEntity create(
            String workspaceId,
            String actor,
            String actorName,
            String targetType,
            String targetId,
            String body,
            String parentId);

    /**
     * Deletes a comment authored by the caller. Deleting a top-level comment cascades its replies
     * by foreign key; deleting a reply keeps the parent's reply counter consistent.
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
