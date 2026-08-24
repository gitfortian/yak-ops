package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceAuthorizer {
  private final DataServiceApiKeyRepository repository;
  private final ApiKeySecretGenerator secrets;
  private final DataServiceRateLimiter rateLimiter;

  public AccessContext authorize(DataServiceDefinition definition, String rawKey) {
    if (definition.authMode() == AuthMode.NONE) return AccessContext.publicAccess();
    if (rawKey == null || rawKey.isBlank()) throw new DataServiceUnauthorizedException("缺少 X-API-Key 请求头");
    DataServiceApiKey key = repository.findByHash(definition.id(), secrets.hash(rawKey.trim()))
        .orElseThrow(() -> new DataServiceUnauthorizedException("API Key 无效或已停用"));
    LocalDateTime now = LocalDateTime.now();
    if (!key.enabled()) throw new DataServiceUnauthorizedException("API Key 无效或已停用");
    if (key.expired(now)) throw new DataServiceUnauthorizedException("API Key 已过期");
    rateLimiter.acquire(key);
    key.markUsed(now);
    repository.save(key);
    return new AccessContext("API_KEY", key.id(), key.name(), key.keyPrefix());
  }
}
