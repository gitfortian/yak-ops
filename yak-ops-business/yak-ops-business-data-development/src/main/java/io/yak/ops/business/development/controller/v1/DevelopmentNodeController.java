package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.api.DevelopmentNodeApi.CreateRequest;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.service.DevelopmentNodeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
  public Result<DevelopmentNode> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(
        request.name(),
        request.type(),
        request.projectId(),
        request.directoryId()));
  }
}
