package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.standard.StandardService;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardCategoryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardFieldVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP assembly for standard catalogs and field definitions. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/data-standards")
public class DataStandardsController {
    @Resource
    private StandardService service;

    @Resource
    private AuthenticationManager authentication;

    private String workspace() {
        return WorkspaceContext.requireWorkspaceId();
    }

    private String actor() {
        return authentication.getLoginUserId();
    }

    @PostMapping("/catalog/initialize")
    public Result<List<StandardCategoryVO>> initialize() {
        return Result.success(service.initialize(workspace(), actor()));
    }

    @GetMapping("/categories/tree")
    public Result<List<StandardCategoryVO>> tree() {
        return Result.success(service.tree(workspace(), actor()));
    }

    @GetMapping("/owners")
    public Result<List<OwnerOptionVO>> owners() {
        return Result.success(service.owners(workspace(), actor()));
    }

    @PostMapping("/categories")
    public Result<StandardSavedVO> createCategory(@Valid @RequestBody CategoryInputDTO input) {
        return Result.success(service.createCategory(workspace(), actor(), input));
    }

    @PutMapping("/categories/{id}")
    public Result<StandardSavedVO> renameCategory(
            @PathVariable String id, @Valid @RequestBody CategoryUpdateDTO input) {
        return Result.success(service.renameCategory(workspace(), actor(), id, input));
    }

    @DeleteMapping("/categories/{id}")
    public Result<Boolean> deleteCategory(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        service.deleteCategory(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/fields")
    public Result<PagingData<StandardFieldVO>> page(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(
                PagingData.from(service.page(workspace(), actor(), categoryId, keyword, pageNo, pageSize)));
    }

    @GetMapping("/fields/{id}")
    public Result<StandardFieldVO> field(@PathVariable String id) {
        return Result.success(service.field(workspace(), actor(), id));
    }

    @PostMapping("/categories/{id}/fields")
    public Result<StandardSavedVO> createField(@PathVariable String id, @Valid @RequestBody StandardInputDTO input) {
        return Result.success(service.createField(workspace(), actor(), id, input));
    }

    @PutMapping("/fields/{id}")
    public Result<StandardSavedVO> updateField(@PathVariable String id, @Valid @RequestBody StandardUpdateDTO input) {
        return Result.success(service.updateField(workspace(), actor(), id, input));
    }

    @DeleteMapping("/fields/{id}")
    public Result<Boolean> deleteField(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        service.deleteField(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/fields/codes/availability")
    public Result<Map<String, Boolean>> available(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", service.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/fields/{id}/references")
    public Result<StandardReferencesVO> references(@PathVariable String id) {
        return Result.success(service.references(workspace(), actor(), id));
    }
}
