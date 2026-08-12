package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.api.DevelopmentTaskApi.PublishRequest;
import io.yak.ops.business.development.api.DevelopmentTaskApi.SaveDraftRequest;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.service.DevelopmentTaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Draft, publish and immutable revision APIs for one development node. */
@Tag(name = "数据开发任务接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
public class DevelopmentTaskController {

  private final DevelopmentTaskService service;

  public DevelopmentTaskController(DevelopmentTaskService service) {
    this.service = service;
  }

  @Operation(summary = "读取节点草稿")
  @GetMapping("/{nodeId}/draft")
  public Result<DevelopmentTaskDraft> getDraft(@PathVariable("nodeId") Long nodeId) {
    return Result.success(service.getDraft(nodeId));
  }

  @Operation(summary = "保存节点草稿")
  @PutMapping("/{nodeId}/draft")
  public Result<DevelopmentTaskDraft> saveDraft(
      @PathVariable("nodeId") Long nodeId,
      @Valid @RequestBody SaveDraftRequest request) {
    return Result.success(service.saveDraft(
        nodeId,
        request.taskType(),
        request.schemaVersion(),
        request.content(),
        request.configJson(),
        request.baseRevision()));
  }

  @Operation(summary = "发布节点版本")
  @PostMapping("/{nodeId}/publish")
  public Result<DevelopmentTaskRevision> publish(
      @PathVariable("nodeId") Long nodeId,
      @Valid @RequestBody PublishRequest request) {
    return Result.success(service.publish(nodeId, request.draftRevision()));
  }

  @Operation(summary = "查询节点发布版本")
  @GetMapping("/{nodeId}/revisions")
  public Result<List<DevelopmentTaskRevisionSummary>> listRevisions(
      @PathVariable("nodeId") Long nodeId) {
    return Result.success(service.listRevisions(nodeId));
  }

  @Operation(summary = "查询节点发布版本详情")
  @GetMapping("/{nodeId}/revisions/{revisionNo}")
  public Result<DevelopmentTaskRevision> getRevision(
      @PathVariable("nodeId") Long nodeId,
      @PathVariable("revisionNo") int revisionNo) {
    return Result.success(service.getRevision(nodeId, revisionNo));
  }
}
