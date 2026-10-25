package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.standard.StandardLookupService;
import io.yak.ops.business.modeling.standard.StandardMeasureUnitService;
import io.yak.ops.business.modeling.standard.StandardNamingEntryService;
import io.yak.ops.business.modeling.standard.StandardService;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardCategoryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardFieldVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardLookupVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardMeasureUnitVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardNamingEntryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
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

/** Authenticated HTTP assembly for standard catalogs, the four standard kinds and their references. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/data-standards")
public class DataStandardsController {
    @Resource
    private StandardService service;

    @Resource
    private StandardLookupService lookups;

    @Resource
    private StandardMeasureUnitService units;

    @Resource
    private StandardNamingEntryService namingEntries;

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

    @GetMapping("/lookups")
    public Result<PagingData<StandardLookupVO>> lookupPage(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(
                PagingData.from(lookups.page(workspace(), actor(), categoryId, keyword, pageNo, pageSize)));
    }

    @GetMapping("/lookups/{id}")
    public Result<StandardLookupVO> lookup(@PathVariable String id) {
        return Result.success(lookups.detail(workspace(), actor(), id));
    }

    @PostMapping("/categories/{categoryId}/lookups")
    public Result<StandardSavedVO> createLookup(
            @PathVariable String categoryId, @Valid @RequestBody StandardLookupInputDTO input) {
        return Result.success(lookups.create(workspace(), actor(), categoryId, input));
    }

    @PutMapping("/lookups/{id}")
    public Result<StandardSavedVO> updateLookup(
            @PathVariable String id, @Valid @RequestBody StandardLookupUpdateDTO input) {
        return Result.success(lookups.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/lookups/{id}")
    public Result<Boolean> deleteLookup(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        lookups.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/lookups/codes/availability")
    public Result<Map<String, Boolean>> lookupCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", lookups.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/lookups/{id}/references")
    public Result<StandardReferencesVO> lookupReferences(@PathVariable String id) {
        return Result.success(lookups.references(workspace(), actor(), id));
    }

    @GetMapping("/measure-units")
    public Result<PagingData<StandardMeasureUnitVO>> unitPage(
            @RequestParam(required = false) MeasureUnitCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(PagingData.from(units.page(workspace(), actor(), category, keyword, pageNo, pageSize)));
    }

    @PostMapping("/measure-units")
    public Result<StandardSavedVO> createUnit(@Valid @RequestBody StandardMeasureUnitInputDTO input) {
        return Result.success(units.create(workspace(), actor(), input));
    }

    @PutMapping("/measure-units/{id}")
    public Result<StandardSavedVO> updateUnit(
            @PathVariable String id, @Valid @RequestBody StandardMeasureUnitUpdateDTO input) {
        return Result.success(units.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/measure-units/{id}")
    public Result<Boolean> deleteUnit(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        units.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/measure-units/codes/availability")
    public Result<Map<String, Boolean>> unitCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", units.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/measure-units/{id}/references")
    public Result<StandardReferencesVO> unitReferences(@PathVariable String id) {
        return Result.success(units.references(workspace(), actor(), id));
    }

    @GetMapping("/naming-entries")
    public Result<PagingData<StandardNamingEntryVO>> namingEntryPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(PagingData.from(namingEntries.page(workspace(), actor(), keyword, pageNo, pageSize)));
    }

    @PostMapping("/naming-entries")
    public Result<StandardSavedVO> createNamingEntry(@Valid @RequestBody StandardNamingEntryInputDTO input) {
        return Result.success(namingEntries.create(workspace(), actor(), input));
    }

    @PutMapping("/naming-entries/{id}")
    public Result<StandardSavedVO> updateNamingEntry(
            @PathVariable String id, @Valid @RequestBody StandardNamingEntryUpdateDTO input) {
        return Result.success(namingEntries.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/naming-entries/{id}")
    public Result<Boolean> deleteNamingEntry(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        namingEntries.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/naming-entries/codes/availability")
    public Result<Map<String, Boolean>> namingEntryCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", namingEntries.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/naming-entries/{id}/references")
    public Result<StandardReferencesVO> namingEntryReferences(@PathVariable String id) {
        return Result.success(namingEntries.references(workspace(), actor(), id));
    }
}
