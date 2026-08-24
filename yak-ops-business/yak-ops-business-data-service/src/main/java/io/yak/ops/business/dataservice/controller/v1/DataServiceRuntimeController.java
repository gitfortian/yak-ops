package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.dataservice.execution.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.observability.DataServiceCallLogReader;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimePolicyManager;
import io.yak.ops.business.dataservice.runtime.DataServiceRuntimeSnapshot;
import io.yak.ops.business.dataservice.runtime.RuntimePolicyInput;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
public class DataServiceRuntimeController {
  private final DataServiceRuntimePolicyManager runtimeManager;
  private final DataServiceInvoker invoker;
  private final DataServiceCallLogReader callLogReader;

  @Operation(summary = "查询数据服务 Runtime 状态与指标")
  @GetMapping("/{id}/runtime")
  public Result<DataServiceRuntimeSnapshot> runtimeStatus(@PathVariable("id") Long id) {
    return Result.success(runtimeManager.snapshot(id));
  }

  @Operation(summary = "更新数据服务 Runtime 缓存与熔断策略")
  @PutMapping("/{id}/runtime")
  public Result<DataServiceRuntimeSnapshot> updateRuntime(@PathVariable("id") Long id, @RequestBody RuntimePolicyInput input) {
    return Result.success(runtimeManager.update(id, input));
  }

  @Operation(summary = "测试 API 服务")
  @PostMapping("/{id}/test")
  public Result<DataServiceQueryResponse> test(@PathVariable("id") Long id,
      @RequestBody(required = false) Map<String, String> parameters) { return Result.success(invoker.test(id, parameters)); }

  @Operation(summary = "查询最近调用记录")
  @GetMapping("/logs/recent")
  public Result<List<InvocationRecord>> logs() { return Result.success(callLogReader.recent()); }

  @Operation(summary = "调用已发布的数据服务")
  @GetMapping("/runtime/{*servicePath}")
  public Result<DataServiceQueryResponse> invoke(@PathVariable("servicePath") String servicePath,
      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
      @RequestParam Map<String, String> parameters) {
    return Result.success(invoker.invoke(servicePath, parameters, apiKey));
  }
}
