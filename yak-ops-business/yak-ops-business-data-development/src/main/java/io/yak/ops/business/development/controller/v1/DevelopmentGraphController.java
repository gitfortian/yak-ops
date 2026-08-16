package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.development.domain.DevelopmentGraph;
import io.yak.ops.business.development.domain.DevelopmentGraph.Edge;
import io.yak.ops.business.development.domain.DevelopmentGraph.NodeLayout;
import io.yak.ops.business.development.service.DevelopmentGraphService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Project-scoped data-development DAG layout and topology endpoints. */
@Tag(name = "数据开发 DAG 接口")
@RestController
@RequestMapping("/api/v1/data-development/graph")
public class DevelopmentGraphController {

  private final DevelopmentGraphService service;

  public DevelopmentGraphController(DevelopmentGraphService service) {
    this.service = service;
  }

  @Operation(summary = "读取数据开发 DAG")
  @GetMapping
  public Result<DevelopmentGraph> get(@RequestParam(required = false) Long projectId) {
    return Result.success(service.get(projectId));
  }

  @Operation(summary = "保存数据开发 DAG")
  @PutMapping
  public Result<DevelopmentGraph> save(@Valid @RequestBody SaveRequest request) {
    return Result.success(service.save(request.projectId(), request.nodes(), request.edges()));
  }

  public record SaveRequest(
      @Min(0) Long projectId,
      @NotNull @Size(max = 2000) List<NodeLayout> nodes,
      @NotNull @Size(max = 4000) List<Edge> edges) {
  }
}
