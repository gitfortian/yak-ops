package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.QueryResponse;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Map;
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

/** 数据服务第一阶段接口：SELECT SQL -> GET REST API。 */
@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
public class DataServiceController {

  private final DataServiceService dataServiceService;

  @Operation(summary = "查询 API 服务列表")
  @GetMapping
  public Result<List<ApiView>> list() {
    return Result.success(dataServiceService.list());
  }

  @Operation(summary = "查询 API 服务详情")
  @GetMapping("/{id}")
  public Result<ApiView> detail(@PathVariable("id") Long id) {
    return Result.success(dataServiceService.get(id));
  }

  @Operation(summary = "创建 API 服务")
  @PostMapping
  public Result<ApiView> create(@RequestBody ApiInput input) {
    return Result.success(dataServiceService.save(null, input));
  }

  @Operation(summary = "更新 API 服务")
  @PutMapping("/{id}")
  public Result<ApiView> update(@PathVariable("id") Long id, @RequestBody ApiInput input) {
    return Result.success(dataServiceService.save(id, input));
  }

  @Operation(summary = "删除 API 服务")
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    dataServiceService.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "启用或停用 API 服务")
  @PutMapping("/{id}/enabled")
  public Result<ApiView> setEnabled(
      @PathVariable("id") Long id,
      @RequestParam("enabled") boolean enabled) {
    return Result.success(dataServiceService.setEnabled(id, enabled));
  }

  @Operation(summary = "测试 API 服务")
  @PostMapping("/{id}/test")
  public Result<QueryResponse> test(
      @PathVariable("id") Long id,
      @RequestBody(required = false) Map<String, String> parameters) {
    return Result.success(dataServiceService.test(id, parameters));
  }

  @Operation(summary = "查询最近调用记录")
  @GetMapping("/logs/recent")
  public Result<List<DataServiceCallLogPO>> logs() {
    return Result.success(dataServiceService.logs());
  }

  @Operation(summary = "调用已发布的数据服务")
  @GetMapping("/runtime/{*servicePath}")
  public Result<QueryResponse> invoke(
      @PathVariable("servicePath") String servicePath,
      @RequestParam Map<String, String> parameters) {
    return Result.success(dataServiceService.invoke(servicePath, parameters));
  }
}
