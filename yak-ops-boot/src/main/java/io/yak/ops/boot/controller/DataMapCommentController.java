package io.yak.ops.boot.controller;

import io.yak.ops.business.datamap.comment.DataMapCommentService;
import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentPage;
import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentThread;
import io.yak.ops.common.bean.vo.datamap.DataMapCommentVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import io.yak.ops.security.authentication.AuthenticationManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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

    record CommentPageResponse(List<DataMapCommentVO> comments, long total, long unreadCount) {}

    @Operation(summary = "读取资产评论")
    @GetMapping
    public Result<CommentPageResponse> comments(
            @RequestParam(defaultValue = "DATA_MAP_TABLE") String targetType,
            @RequestParam String targetId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String actor = authentication.getLoginUserId();
        CommentPage page = commentService.page(workspaceId, targetType, targetId, actor, pageNo, pageSize);
        return Result.success(new CommentPageResponse(
                page.page().records().stream().map(this::toVO).toList(),
                page.page().total(), page.unreadCount()));
    }

    @Operation(summary = "创建评论或回复")
    @PostMapping
    public Result<DataMapCommentVO> create(@RequestBody CommentCreateRequest input) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        String actor = authentication.getLoginUserId();
        DataMapCommentEntity saved = commentService.create(
                workspaceId, actor, actor,
                input.targetType(), input.targetId(), input.body(), input.parentId());
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
        commentService.advanceRead(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), targetId);
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
