package io.yak.ops.business.dataservice.runtime;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceRuntimePolicyManager {

  private final DataServiceReader reader;
  private final DataServiceRepository repository;
  private final LocalDataServiceRuntime runtime;

  public DataServiceRuntimeSnapshot snapshot(Long apiId) {
    DataServiceDefinition definition = reader.require(apiId);
    return runtime.snapshot(definition.id(), definition.runtimePolicy());
  }

  @Transactional
  public DataServiceRuntimeSnapshot update(Long apiId, RuntimePolicyInput input) {
    DataServiceDefinition definition = reader.require(apiId);
    RuntimePolicy policy = normalize(input);
    definition.updateRuntimePolicy(policy, LocalDateTime.now());
    DataServiceDefinition saved = repository.save(definition);
    runtime.invalidate(apiId);
    return runtime.snapshot(saved.id(), saved.runtimePolicy());
  }

  public void invalidate(Long apiId) { runtime.invalidate(apiId); }
  public void remove(Long apiId) { runtime.remove(apiId); }

  private RuntimePolicy normalize(RuntimePolicyInput input) {
    if (input == null) throw new IllegalArgumentException("Runtime 配置不能为空");
    return new RuntimePolicy(
        Boolean.TRUE.equals(input.cacheEnabled()),
        range(input.cacheTtlSeconds(), 60, 1, 3_600, "缓存 TTL"),
        range(input.cacheMaxEntries(), 200, 1, 5_000, "缓存最大条目数"),
        Boolean.TRUE.equals(input.circuitBreakerEnabled()),
        range(input.failureThreshold(), 5, 1, 20, "熔断失败阈值"),
        range(input.recoverySeconds(), 30, 1, 300, "熔断恢复时间"));
  }

  private int range(Integer value, int fallback, int min, int max, String name) {
    int normalized = value == null ? fallback : value;
    if (normalized < min || normalized > max) {
      throw new IllegalArgumentException(name + " 必须在 " + min + "~" + max + " 之间");
    }
    return normalized;
  }
}
