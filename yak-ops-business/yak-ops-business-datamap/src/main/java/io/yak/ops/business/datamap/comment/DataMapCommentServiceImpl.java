package io.yak.ops.business.datamap.comment;

import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentThread;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCommentMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default comment reader and writer; one level of replies, body stored as plain text. */
@Service
public class DataMapCommentServiceImpl implements DataMapCommentService {

    /** The only anchored asset kind in this release. */
    private static final String TARGET_TYPE_DATA_MAP_TABLE = "DATA_MAP_TABLE";

    private static final int MAX_BODY_LENGTH = 2000;

    private final DataMapCommentMapper commentMapper;

    private final DataMapTableMapper tableMapper;

    public DataMapCommentServiceImpl(DataMapCommentMapper commentMapper, DataMapTableMapper tableMapper) {
        this.commentMapper = commentMapper;
        this.tableMapper = tableMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageData<CommentThread> page(
            String workspaceId, String targetType, String targetId, String userId, int pageNo, int pageSize) {
        long total = commentMapper.countTopLevel(workspaceId, targetType, targetId);
        if (total == 0) return PageData.of(List.of(), 0, pageNo, pageSize);
        List<DataMapCommentEntity> topLevel =
                commentMapper.pageTopLevel(workspaceId, targetType, targetId, pageSize, (pageNo - 1) * pageSize);
        Map<String, List<DataMapCommentEntity>> repliesByParent = new LinkedHashMap<>();
        for (DataMapCommentEntity reply : commentMapper.repliesOfTarget(workspaceId, targetType, targetId)) {
            repliesByParent
                    .computeIfAbsent(reply.getParentId(), parent -> new ArrayList<>())
                    .add(reply);
        }
        List<CommentThread> threads = topLevel.stream()
                .map(comment -> new CommentThread(comment, repliesByParent.getOrDefault(comment.getId(), List.of())))
                .toList();
        return PageData.of(threads, total, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String workspaceId, String targetType, String targetId, String userId) {
        return commentMapper.unreadCount(workspaceId, targetType, targetId, userId);
    }

    @Override
    @Transactional
    public DataMapCommentEntity create(
            String workspaceId,
            String actor,
            String actorName,
            String targetType,
            String targetId,
            String body,
            String parentId) {
        String trimmed = body == null ? "" : body.strip();
        if (trimmed.isEmpty()) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new BusinessException(DataMapErrorCode.COMMENT_TOO_LONG);
        }
        if (!TARGET_TYPE_DATA_MAP_TABLE.equals(targetType) || tableMapper.findById(workspaceId, targetId) == null) {
            throw new BusinessException(DataMapErrorCode.COMMENT_TARGET_INVALID);
        }
        if (parentId != null) {
            DataMapCommentEntity parent = commentMapper.findById(workspaceId, parentId);
            if (parent == null
                    || parent.getParentId() != null
                    || !parent.getTargetType().equals(targetType)
                    || !parent.getTargetId().equals(targetId)) {
                throw new BusinessException(DataMapErrorCode.COMMENT_PARENT_MISMATCH);
            }
        }
        DataMapCommentEntity comment = new DataMapCommentEntity();
        comment.initCreate(actor);
        comment.setWorkspaceId(workspaceId);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setParentId(parentId);
        comment.setBody(trimmed);
        comment.setAuthorId(actor);
        comment.setAuthorName(actorName);
        comment.setReplyCount(0);
        commentMapper.insert(comment);
        if (parentId != null) {
            commentMapper.incrementReplyCount(parentId);
        }
        return comment;
    }

    @Override
    @Transactional
    public void delete(String workspaceId, String actor, String commentId) {
        DataMapCommentEntity comment = commentMapper.findById(workspaceId, commentId);
        if (comment == null) throw new BusinessException(DataMapErrorCode.COMMENT_NOT_FOUND);
        if (!comment.getAuthorId().equals(actor)) {
            throw new BusinessException(DataMapErrorCode.COMMENT_FORBIDDEN);
        }
        commentMapper.delete(workspaceId, commentId);
        if (comment.getParentId() != null) {
            commentMapper.decrementReplyCount(comment.getParentId());
        }
    }

    @Override
    @Transactional
    public void advanceRead(String workspaceId, String userId, String targetId) {
        commentMapper.upsertReadMarker(IdUtils.nextId(), workspaceId, targetId, userId, LocalDateTime.now());
    }
}
