package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.service.CustomTemplateService;
import io.yak.ops.common.bean.dto.quality.CustomQualityTemplateDTO;
import io.yak.ops.common.bean.vo.quality.CustomQualityTemplateVO;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量自定义规则模板")
@RestController
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/template")
@RequiresPermission(QualityPermissionCode.TEMPLATE_READ)
public class CustomTemplateController {
  private final CustomTemplateService service;

  @Operation(summary = "查询自定义规则模板")
  @GetMapping("/custom")
  public Result<CustomQualityTemplateVO.ListView> list(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "dimension", required = false) String dimension,
      @RequestParam(value = "folderId", required = false) Long folderId) {
    return Result.success(service.list(new CustomQualityTemplateDTO.Query(keyword, dimension, folderId)));
  }

  @Operation(summary = "查询自定义规则模板详情")
  @GetMapping("/custom/{id}")
  public Result<CustomQualityTemplateVO.Template> detail(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "查询自定义模板目录")
  @GetMapping("/folder")
  public Result<List<CustomQualityTemplateVO.Folder>> folders() {
    return Result.success(service.folders());
  }

  @Operation(summary = "创建自定义模板目录")
  @PostMapping("/folder")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_CREATE)
  public Result<CustomQualityTemplateVO.Folder> createFolder(
      @Valid @RequestBody CustomQualityTemplateDTO.SaveFolderRequest request,
      Principal principal) {
    return Result.success(service.createFolder(request, operator(principal)));
  }

  @Operation(summary = "更新自定义模板目录")
  @PutMapping("/folder/{id}")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_UPDATE)
  public Result<CustomQualityTemplateVO.Folder> updateFolder(
      @PathVariable long id,
      @Valid @RequestBody CustomQualityTemplateDTO.SaveFolderRequest request,
      Principal principal) {
    return Result.success(service.updateFolder(id, request, operator(principal)));
  }

  @Operation(summary = "删除自定义模板目录")
  @DeleteMapping("/folder/{id}")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_DELETE)
  public Result<Boolean> deleteFolder(@PathVariable long id, Principal principal) {
    return Result.success(service.deleteFolder(id, operator(principal)));
  }

  @Operation(summary = "创建自定义规则模板")
  @PostMapping("/custom")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_CREATE)
  public Result<CustomQualityTemplateVO.Template> create(
      @Valid @RequestBody CustomQualityTemplateDTO.SaveTemplateRequest request,
      Principal principal) {
    return Result.success(service.create(request, operator(principal)));
  }

  @Operation(summary = "更新自定义规则模板")
  @PutMapping("/custom/{id}")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_UPDATE)
  public Result<CustomQualityTemplateVO.Template> update(
      @PathVariable long id,
      @Valid @RequestBody CustomQualityTemplateDTO.SaveTemplateRequest request,
      Principal principal) {
    return Result.success(service.update(id, request, operator(principal)));
  }

  @Operation(summary = "复制自定义规则模板")
  @PostMapping("/custom/{id}/copy")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_CREATE)
  public Result<CustomQualityTemplateVO.Template> copy(
      @PathVariable long id,
      @Valid @RequestBody CustomQualityTemplateDTO.CopyTemplateRequest request,
      Principal principal) {
    return Result.success(service.copy(id, request, operator(principal)));
  }

  @Operation(summary = "删除自定义规则模板")
  @DeleteMapping("/custom/{id}")
  @RequiresPermission(QualityPermissionCode.TEMPLATE_DELETE)
  public Result<Boolean> delete(@PathVariable long id) {
    return Result.success(service.delete(id));
  }

  private static String operator(Principal principal) {
    return principal == null || principal.getName() == null || principal.getName().isBlank()
        ? "system" : principal.getName();
  }
}
