package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.ops.business.development.api.DevelopmentTaskApi.LineagePreviewRequest;
import io.yak.ops.business.development.api.DevelopmentTaskApi.PublishRequest;
import io.yak.ops.business.development.api.DevelopmentTaskApi.RunRequest;
import io.yak.ops.business.development.api.DevelopmentTaskApi.SaveDraftRequest;
import io.yak.ops.business.development.domain.DevelopmentSqlLineagePreview;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.domain.DevelopmentTaskRunResult;
import io.yak.ops.business.development.service.DevelopmentNodeService;
import io.yak.ops.business.development.service.DevelopmentSqlLineagePreviewService;
import io.yak.ops.business.development.service.DevelopmentTaskRunService;
import io.yak.ops.business.development.service.DevelopmentTaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Draft, publish, revision and manual-run APIs for one development node. */
@Tag(name = "数据开发任务接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
public class DevelopmentTaskController {

  private final DevelopmentTaskService service;
  private final DevelopmentTaskRunService runService;
  private final DevelopmentSqlLineagePreviewService lineagePreviewService;
  private final DevelopmentNodeService nodeService;
  private final CurrentUserProvider currentUserProvider;

  public DevelopmentTaskController(
      DevelopmentTaskService service,
      DevelopmentTaskRunService runService,
      DevelopmentSqlLineagePreviewService lineagePreviewService,
      DevelopmentNodeService nodeService,
      CurrentUserProvider currentUserProvider) {
    this.service = service;
    this.runService = runService;
    this.lineagePreviewService = lineagePreviewService;
    this.nodeService = nodeService;
    this.currentUserProvider = currentUserProvider;
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
      @Valid @RequestBody SaveDraftRequest request,
      HttpServletRequest servletRequest) {
    DevelopmentTaskDraft saved = service.saveDraft(
        nodeId,
        request.taskType(),
        request.schemaVersion(),
        request.content(),
        request.configJson(),
        request.baseRevision());
    nodeService.recordUpdater(nodeId, operatorName(servletRequest));
    return Result.success(saved);
  }

  @Operation(summary = "运行当前编辑器任务")
  @PostMapping("/{nodeId}/run")
  public Result<DevelopmentTaskRunResult> run(
      @PathVariable("nodeId") Long nodeId,
      @Valid @RequestBody RunRequest request,
      HttpServletRequest servletRequest) {
    return Result.success(
        runService.run(
            nodeId,
            request.taskType(),
            request.schemaVersion(),
            request.content(),
            request.configJson(),
            operatorName(servletRequest)));
  }

  @Operation(summary = "预览当前 SQL 编辑器血缘")
  @PostMapping("/{nodeId}/lineage/preview")
  public Result<DevelopmentSqlLineagePreview> previewLineage(
      @PathVariable("nodeId") Long nodeId,
      @Valid @RequestBody LineagePreviewRequest request) {
    return Result.success(lineagePreviewService.preview(
        nodeId,
        request.taskType(),
        request.content(),
        request.configJson()));
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

  private String operatorName(HttpServletRequest request) {
    String operatorName = currentUserProvider.getCurrentUser(request);
    return operatorName == null ? "unknown" : operatorName;
  }
}
