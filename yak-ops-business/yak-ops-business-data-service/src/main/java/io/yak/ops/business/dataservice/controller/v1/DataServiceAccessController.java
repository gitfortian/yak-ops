package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.access.ApiKeyInput;
import io.yak.ops.business.dataservice.access.ApiKeyUpdate;
import io.yak.ops.business.dataservice.access.ApiKeyView;
import io.yak.ops.business.dataservice.access.CreatedApiKey;
import io.yak.ops.business.dataservice.access.DataServiceApiKeyManager;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
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

@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
public class DataServiceAccessController {
  private final DataServiceApiKeyManager manager;

  @Operation(summary = "设置数据服务访问控制模式")
  @PutMapping("/{id}/auth-mode")
  public Result<String> setAuthMode(@PathVariable("id") Long id, @RequestParam("mode") String mode) {
    return Result.success(manager.setAuthMode(id, mode).name());
  }

  @Operation(summary = "查询数据服务 API Key")
  @GetMapping("/{id}/keys")
  public Result<List<ApiKeyView>> listKeys(@PathVariable("id") Long id) { return Result.success(manager.listKeys(id)); }

  @Operation(summary = "创建数据服务 API Key（明文仅返回一次）")
  @PostMapping("/{id}/keys")
  public Result<CreatedApiKey> createKey(@PathVariable("id") Long id, @RequestBody ApiKeyInput input) {
    return Result.success(manager.createKey(id, input));
  }

  @Operation(summary = "更新数据服务 API Key 配置")
  @PutMapping("/{id}/keys/{keyId}")
  public Result<ApiKeyView> updateKey(@PathVariable("id") Long id, @PathVariable("keyId") Long keyId,
      @RequestBody ApiKeyUpdate input) { return Result.success(manager.updateKey(id, keyId, input)); }

  @Operation(summary = "启用或停用数据服务 API Key")
  @PutMapping("/{id}/keys/{keyId}/enabled")
  public Result<ApiKeyView> setKeyEnabled(@PathVariable("id") Long id, @PathVariable("keyId") Long keyId,
      @RequestParam("enabled") boolean enabled) { return Result.success(manager.setKeyEnabled(id, keyId, enabled)); }

  @Operation(summary = "轮换数据服务 API Key（新明文仅返回一次）")
  @PostMapping("/{id}/keys/{keyId}/rotate")
  public Result<CreatedApiKey> rotateKey(@PathVariable("id") Long id, @PathVariable("keyId") Long keyId) {
    return Result.success(manager.rotateKey(id, keyId));
  }

  @Operation(summary = "删除数据服务 API Key")
  @DeleteMapping("/{id}/keys/{keyId}")
  public Result<Boolean> deleteKey(@PathVariable("id") Long id, @PathVariable("keyId") Long keyId) {
    manager.deleteKey(id, keyId); return Result.success(Boolean.TRUE);
  }
}
