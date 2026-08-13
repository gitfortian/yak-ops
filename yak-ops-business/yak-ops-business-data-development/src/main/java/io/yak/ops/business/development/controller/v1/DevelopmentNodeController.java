package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.api.DevelopmentNodeApi.CreateRequest;
import io.yak.ops.business.development.api.DevelopmentNodeApi.RenameRequest;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.service.DevelopmentNodeService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight resource-node management for the data-development tree. */
@Tag(name = "数据开发节点接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
public class DevelopmentNodeController {

  private final DevelopmentNodeService service;

  public DevelopmentNodeController(DevelopmentNodeService service) {
    this.service = service;
  }

  @Operation(summary = "查询数据开发节点")
  @GetMapping
  public Result<List<DevelopmentNode>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "新建数据开发节点")
  @PostMapping
  public Result<DevelopmentNode> create(
      @Valid @RequestBody CreateRequest request,
      Principal principal) {
    DevelopmentNode created = service.create(
        request.name(),
        request.type(),
        request.projectId(),
        request.directoryId());
    return Result.success(service.recordUpdater(created.id(), operatorName(principal)));
  }

  @Operation(summary = "重命名数据开发节点")
  @PutMapping("/{id}/name")
  public Result<DevelopmentNode> rename(
      @PathVariable("id") Long id,
      @Valid @RequestBody RenameRequest request,
      Principal principal) {
    DevelopmentNode renamed = service.rename(id, request.name());
    return Result.success(service.recordUpdater(renamed.id(), operatorName(principal)));
  }

  @Operation(summary = "删除数据开发节点")
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  private String operatorName(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }
}
