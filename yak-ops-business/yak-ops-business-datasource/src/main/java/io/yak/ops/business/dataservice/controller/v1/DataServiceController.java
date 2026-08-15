package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.service.DataServiceAccessService;
import io.yak.ops.business.dataservice.service.DataServiceAccessService.ApiKeyInput;
import io.yak.ops.business.dataservice.service.DataServiceAccessService.ApiKeyUpdate;
import io.yak.ops.business.dataservice.service.DataServiceAccessService.ApiKeyView;
import io.yak.ops.business.dataservice.service.DataServiceAccessService.CreatedApiKey;
import io.yak.ops.business.dataservice.service.DataServiceRuntimeService.RuntimeSnapshot;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.QueryResponse;
import io.yak.ops.business.dataservice.service.DataServiceService.RuntimeConfigInput;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据服务管理、访问控制、Runtime 策略与调用接口。 */
@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
public class DataServiceController {

  private final DataServiceService dataServiceService;
  private final DataServiceAccessService accessService;

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

  @Operation(summary = "查询数据服务 Runtime 状态与指标")
  @GetMapping("/{id}/runtime")
  public Result<RuntimeSnapshot> runtimeStatus(@PathVariable("id") Long id) {
    return Result.success(dataServiceService.runtimeStatus(id));
  }

  @Operation(summary = "更新数据服务 Runtime 缓存与熔断策略")
  @PutMapping("/{id}/runtime")
  public Result<RuntimeSnapshot> updateRuntime(
      @PathVariable("id") Long id,
      @RequestBody RuntimeConfigInput input) {
    return Result.success(dataServiceService.updateRuntimeConfig(id, input));
  }

  @Operation(summary = "设置数据服务访问控制模式")
  @PutMapping("/{id}/auth-mode")
  public Result<String> setAuthMode(
      @PathVariable("id") Long id,
      @RequestParam("mode") String mode) {
    return Result.success(accessService.setAuthMode(id, mode).name());
  }

  @Operation(summary = "查询数据服务 API Key")
  @GetMapping("/{id}/keys")
  public Result<List<ApiKeyView>> listKeys(@PathVariable("id") Long id) {
    return Result.success(accessService.listKeys(id));
  }

  @Operation(summary = "创建数据服务 API Key（明文仅返回一次）")
  @PostMapping("/{id}/keys")
  public Result<CreatedApiKey> createKey(
      @PathVariable("id") Long id,
      @RequestBody ApiKeyInput input) {
    return Result.success(accessService.createKey(id, input));
  }

  @Operation(summary = "更新数据服务 API Key 配置")
  @PutMapping("/{id}/keys/{keyId}")
  public Result<ApiKeyView> updateKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId,
      @RequestBody ApiKeyUpdate input) {
    return Result.success(accessService.updateKey(id, keyId, input));
  }

  @Operation(summary = "启用或停用数据服务 API Key")
  @PutMapping("/{id}/keys/{keyId}/enabled")
  public Result<ApiKeyView> setKeyEnabled(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId,
      @RequestParam("enabled") boolean enabled) {
    return Result.success(accessService.setKeyEnabled(id, keyId, enabled));
  }

  @Operation(summary = "轮换数据服务 API Key（新明文仅返回一次）")
  @PostMapping("/{id}/keys/{keyId}/rotate")
  public Result<CreatedApiKey> rotateKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId) {
    return Result.success(accessService.rotateKey(id, keyId));
  }

  @Operation(summary = "删除数据服务 API Key")
  @DeleteMapping("/{id}/keys/{keyId}")
  public Result<Boolean> deleteKey(
      @PathVariable("id") Long id,
      @PathVariable("keyId") Long keyId) {
    accessService.deleteKey(id, keyId);
    return Result.success(Boolean.TRUE);
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
      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
      @RequestParam Map<String, String> parameters) {
    return Result.success(dataServiceService.invoke(servicePath, parameters, apiKey));
  }
}
