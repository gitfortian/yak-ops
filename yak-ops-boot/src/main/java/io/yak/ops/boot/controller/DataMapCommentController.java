package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datamap.comment.DataMapCommentService;
import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentThread;
import io.yak.ops.common.bean.vo.datamap.DataMapCommentVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP boundary for asset comment threads. */
@Tag(name = "资产评论接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-map/comments")
public class DataMapCommentController {

    @Resource
    private DataMapCommentService commentService;

    @Resource
    private AuthenticationManager authentication;

    record CommentCreateRequest(String targetType, String targetId, String body, String parentId) {}

    @Operation(summary = "读取资产评论")
    @GetMapping
    public Result<PagingData<DataMapCommentVO>> comments(
            @RequestParam(defaultValue = "DATA_MAP_TABLE") String targetType,
            @RequestParam String targetId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        PageData<CommentThread> page = commentService.page(
                WorkspaceContext.requireWorkspaceId(),
                targetType,
                targetId,
                authentication.getLoginUserId(),
                pageNo,
                pageSize);
        List<DataMapCommentVO> threads = page.records().stream().map(this::toVO).toList();
        return Result.success(PagingData.from(PageData.of(threads, page.total(), page.pageNo(), page.pageSize())));
    }

    @Operation(summary = "读取资产评论未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(
            @RequestParam(defaultValue = "DATA_MAP_TABLE") String targetType, @RequestParam String targetId) {
        return Result.success(commentService.unreadCount(
                WorkspaceContext.requireWorkspaceId(), targetType, targetId, authentication.getLoginUserId()));
    }

    @Operation(summary = "创建评论或回复")
    @PostMapping
    public Result<DataMapCommentVO> create(@RequestBody CommentCreateRequest input) {
        DataMapCommentEntity saved = commentService.create(
                WorkspaceContext.requireWorkspaceId(),
                authentication.getLoginUserId(),
                authentication.getLoginUsername(),
                input.targetType(),
                input.targetId(),
                input.body(),
                input.parentId());
        return Result.success(toEntityVO(saved));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public Result<Boolean> delete(@PathVariable String commentId) {
        commentService.delete(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), commentId);
        return Result.success(true);
    }

    @Operation(summary = "标记已读")
    @PostMapping("/read")
    public Result<Boolean> markRead(@RequestParam String targetId) {
        commentService.advanceRead(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), targetId);
        return Result.success(true);
    }

    private DataMapCommentVO toEntityVO(DataMapCommentEntity c) {
        DataMapCommentVO vo = new DataMapCommentVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setBody(c.getBody());
        vo.setAuthorId(c.getAuthorId());
        vo.setAuthorName(c.getAuthorName());
        vo.setReplyCount(c.getReplyCount());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private DataMapCommentVO toVO(CommentThread thread) {
        DataMapCommentEntity c = thread.comment();
        DataMapCommentVO vo = toEntityVO(c);
        vo.setReplies(thread.replies().stream().map(this::toEntityVO).toList());
        return vo;
    }
}
