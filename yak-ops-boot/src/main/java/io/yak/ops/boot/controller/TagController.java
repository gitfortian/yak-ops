package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datamap.tagging.TagService;
import io.yak.ops.common.bean.dto.tag.TagBindingBatchDTO;
import io.yak.ops.common.bean.dto.tag.TagBindingUpdateDTO;
import io.yak.ops.common.bean.dto.tag.TagKeyRenameDTO;
import io.yak.ops.common.bean.dto.tag.TagKeySaveDTO;
import io.yak.ops.common.bean.dto.tag.TagValueSaveDTO;
import io.yak.ops.common.bean.vo.tag.TagKeyVO;
import io.yak.ops.common.bean.vo.tag.TagValueVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated HTTP boundary of the tag dictionary and its asset bindings.
 *
 * <p>Every route is Workspace-scoped through the existing interceptor; no route accepts a Workspace
 * identifier from the caller. Dictionary writes require a Workspace manager, which the service
 * enforces; binding writes stay open to every member.
 */
@Tag(name = "标签字典接口")
@RestController
@Validated
@RequestMapping(CommonConstants.API_PREFIX + "/tags")
public class TagController {

    @Resource
    private TagService tagService;

    @Resource
    private AuthenticationManager authentication;

    @Operation(summary = "查询标签字典")
    @GetMapping("/keys")
    public Result<List<TagKeyVO>> keys() {
        return Result.success(tagService.listKeys(WorkspaceContext.requireWorkspaceId()));
    }

    @Operation(summary = "创建标签键")
    @PostMapping("/keys")
    public Result<TagKeyVO> createKey(@Valid @RequestBody TagKeySaveDTO input) {
        return Result.success(tagService.createKey(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    @Operation(summary = "重命名标签键")
    @PutMapping("/keys/{keyId}")
    public Result<TagKeyVO> renameKey(@PathVariable String keyId, @Valid @RequestBody TagKeyRenameDTO input) {
        return Result.success(tagService.renameKey(WorkspaceContext.requireWorkspaceId(), actor(), keyId, input));
    }

    @Operation(summary = "删除标签键")
    @DeleteMapping("/keys/{keyId}")
    public Result<Boolean> deleteKey(@PathVariable String keyId) {
        tagService.deleteKey(WorkspaceContext.requireWorkspaceId(), actor(), keyId);
        return Result.success(true);
    }

    @Operation(summary = "新增标签值")
    @PostMapping("/keys/{keyId}/values")
    public Result<TagValueVO> addValue(@PathVariable String keyId, @Valid @RequestBody TagValueSaveDTO input) {
        return Result.success(tagService.addValue(WorkspaceContext.requireWorkspaceId(), actor(), keyId, input));
    }

    @Operation(summary = "重命名标签值")
    @PutMapping("/values/{valueId}")
    public Result<TagValueVO> renameValue(@PathVariable String valueId, @Valid @RequestBody TagValueSaveDTO input) {
        return Result.success(tagService.renameValue(WorkspaceContext.requireWorkspaceId(), actor(), valueId, input));
    }

    @Operation(summary = "删除标签值")
    @DeleteMapping("/values/{valueId}")
    public Result<Boolean> deleteValue(@PathVariable String valueId) {
        tagService.deleteValue(WorkspaceContext.requireWorkspaceId(), actor(), valueId);
        return Result.success(true);
    }

    @Operation(summary = "替换资产标签")
    @PutMapping("/tables/{tableId}")
    public Result<Boolean> replaceTableValues(
            @PathVariable String tableId, @Valid @RequestBody TagBindingUpdateDTO input) {
        tagService.replaceTableValues(WorkspaceContext.requireWorkspaceId(), actor(), tableId, input.getTagValueIds());
        return Result.success(true);
    }

    @Operation(summary = "批量绑定或解绑标签")
    @PostMapping("/bindings/batch")
    public Result<Integer> applyBatch(@Valid @RequestBody TagBindingBatchDTO input) {
        return Result.success(tagService.applyBatch(WorkspaceContext.requireWorkspaceId(), actor(), input));
    }

    private String actor() {
        return authentication.getLoginUserId();
    }
}
