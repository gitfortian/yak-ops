package io.yak.ops.business.datamap.comment;

import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentPage;
import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentThread;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCommentMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default comment reader and writer; one level of replies, body stored as plain text. */
@Service
public class DataMapCommentServiceImpl implements DataMapCommentService {

    private final DataMapCommentMapper commentMapper;

    public DataMapCommentServiceImpl(DataMapCommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPage page(String workspaceId, String targetType, String targetId,
            String userId, int pageNo, int pageSize) {
        long total = commentMapper.countTopLevel(workspaceId, targetType, targetId);
        if (total == 0) return new CommentPage(PageData.of(List.of(), 0, pageNo, pageSize), 0);
        List<DataMapCommentEntity> topLevel = commentMapper.pageTopLevel(
                workspaceId, targetType, targetId, pageSize, (pageNo - 1) * pageSize);
        List<CommentThread> threads = topLevel.stream()
                .map(comment -> new CommentThread(comment, commentMapper.repliesOf(comment.getId())))
                .toList();
        long unread = commentMapper.unreadCount(workspaceId, targetType, targetId, userId);
        return new CommentPage(PageData.of(threads, total, pageNo, pageSize), unread);
    }

    @Override
    @Transactional
    public DataMapCommentEntity create(String workspaceId, String actor, String actorName,
            String targetType, String targetId, String body, String parentId) {
        if (body == null || body.isBlank())
            throw new BusinessException(DataMapErrorCode.COMMENT_TOO_LONG);
        if (body.length() > MAX_BODY_LENGTH)
            throw new BusinessException(DataMapErrorCode.COMMENT_TOO_LONG);
        DataMapCommentEntity comment = new DataMapCommentEntity();
        comment.setId(UUID.randomUUID().toString().replace("-", ""));
        comment.setWorkspaceId(workspaceId);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setParentId(parentId);
        comment.setBody(body.trim());
        comment.setAuthorId(actor);
        comment.setAuthorName(actorName);
        comment.setReplyCount(0);
        LocalDateTime now = LocalDateTime.now();
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        commentMapper.insert(comment);
        if (parentId != null) {
            DataMapCommentEntity parent = commentMapper.findById(workspaceId, parentId);
            if (parent == null || parent.getParentId() != null)
                throw new BusinessException(DataMapErrorCode.COMMENT_PARENT_MISMATCH);
            commentMapper.incrementReplyCount(parentId);
        }
        return comment;
    }

    @Override
    @Transactional
    public void delete(String workspaceId, String actor, String commentId) {
        DataMapCommentEntity comment = commentMapper.findById(workspaceId, commentId);
        if (comment == null) throw new BusinessException(DataMapErrorCode.COMMENT_TOO_LONG);
        commentMapper.delete(workspaceId, commentId);
        if (comment.getParentId() != null)
            commentMapper.decrementReplyCount(comment.getParentId());
    }

    @Override
    @Transactional
    public void advanceRead(String workspaceId, String userId, String targetId) {
        commentMapper.upsertReadMarker(
                UUID.randomUUID().toString().replace("-", ""),
                workspaceId, targetId, userId, LocalDateTime.now());
    }

    private static final int MAX_BODY_LENGTH = 2000;
}
