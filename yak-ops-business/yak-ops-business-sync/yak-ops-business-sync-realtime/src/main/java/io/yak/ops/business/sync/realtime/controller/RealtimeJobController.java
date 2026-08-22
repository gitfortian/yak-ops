package io.yak.ops.business.sync.realtime.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.service.RealtimeEventStreamService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "实时同步")
@RestController
@RequestMapping("/api/v1/realtime-sync")
@RequiresPermission(RealtimePermissionCode.READ)
public class RealtimeJobController {

  private final RealtimeJobService service;
  private final RealtimeJobLifecycleCoordinator lifecycleCoordinator;
  private final RealtimeJobListQuery listQuery;
  private final RealtimeEventStreamService eventStream;

  public RealtimeJobController(
      RealtimeJobService service,
      RealtimeJobLifecycleCoordinator lifecycleCoordinator,
      RealtimeJobListQuery listQuery,
      RealtimeEventStreamService eventStream) {
    this.service = service;
    this.lifecycleCoordinator = lifecycleCoordinator;
    this.listQuery = listQuery;
    this.eventStream = eventStream;
  }

  public record SaveRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 1000) String description,
      @Valid CdcPipelineSpec spec) {}

  public record CreateRequest(
      @NotBlank @Size(max = 200) String name, @Size(max = 1000) String description) {}

  @Operation(summary = "创建实时同步基础任务")
  @PostMapping
  @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> create(@Valid @RequestBody CreateRequest request) {
    return Result.success(service.create(request.name(), request.description()));
  }

  @Operation(summary = "新建实时同步草稿")
  @PostMapping("/draft")
  @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> draft(@Valid @RequestBody SaveRequest request) {
    return Result.success(
        service.save(null, request.name(), request.description(), request.spec()));
  }

  @Operation(summary = "保存实时同步草稿")
  @PutMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Long> save(@PathVariable long id, @Valid @RequestBody SaveRequest request) {
    return Result.success(service.save(id, request.name(), request.description(), request.spec()));
  }

  @Operation(summary = "实时同步任务详情")
  @GetMapping("/{id}")
  public Result<RealtimeJobView> detail(@PathVariable long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "实时同步任务分页")
  @GetMapping
  public Result<RealtimeJobPage> page(
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String releaseState,
      @RequestParam(required = false) String stateGroup) {
    return Result.success(listQuery.page(pageNo, pageSize, keyword, id, releaseState, stateGroup));
  }

  @Operation(summary = "订阅实时同步任务状态")
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    return eventStream.subscribe();
  }

  @Operation(summary = "发布当前定义版本")
  @PostMapping("/{id}/publish")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> publish(@PathVariable long id) {
    service.publish(id);
    return Result.success(true);
  }

  @Operation(summary = "使用 Flink CDC 配置校验当前定义")
  @PostMapping("/{id}/validate")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<RealtimeEngineGateway.ValidationResult> validate(@PathVariable long id) {
    return Result.success(service.validate(id));
  }

  @Operation(summary = "启动实时同步任务")
  @PostMapping("/{id}/start")
  @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeJobView.Deployment> start(
      @PathVariable long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return Result.success(service.start(id, key));
  }

  @Operation(summary = "停止实时同步任务")
  @PostMapping("/{id}/stop")
  @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<Boolean> stop(@PathVariable long id) {
    service.stop(id);
    return Result.success(true);
  }

  @Operation(summary = "重启实时同步任务")
  @PostMapping("/{id}/restart")
  @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeJobView.Deployment> restart(
      @PathVariable long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return Result.success(service.restart(id, key));
  }

  @Operation(summary = "立即对账实时同步任务")
  @PostMapping("/{id}/reconcile")
  @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeJobView> reconcile(@PathVariable long id) {
    return Result.success(lifecycleCoordinator.reconcile(id));
  }

  @Operation(summary = "删除已停止的实时同步任务")
  @DeleteMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.DELETE)
  public Result<Boolean> delete(@PathVariable long id) {
    lifecycleCoordinator.assertSafeToDelete(id);
    service.delete(id);
    return Result.success(true);
  }

  @Operation(summary = "查询任务状态事件")
  @GetMapping("/{id}/events")
  public Result<List<RealtimeJobEventView>> events(@PathVariable long id) {
    return Result.success(service.events(id));
  }

  @Operation(summary = "查询 Flink CDC 运行能力")
  @GetMapping("/runtime/capabilities")
  public Result<JsonNode> capabilities() {
    return Result.success(service.capabilities());
  }

  @Operation(summary = "查询当前任务提交日志和 Flink 异常")
  @GetMapping("/{id}/logs")
  public Result<Map<String, String>> logs(
      @PathVariable long id, @RequestParam(defaultValue = "200") int tail) {
    return Result.success(Map.of("logs", service.logs(id, tail)));
  }

  @Operation(summary = "查询当前任务 Checkpoint 统计")
  @GetMapping("/{id}/checkpoints")
  public Result<JsonNode> checkpoints(@PathVariable long id) {
    return Result.success(service.checkpoints(id));
  }

  @Operation(summary = "查询当前任务 Flink 指标")
  @GetMapping("/{id}/metrics")
  public Result<JsonNode> metrics(@PathVariable long id) {
    return Result.success(service.metrics(id));
  }
}
