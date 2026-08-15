package io.yak.ops.business.dataservice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiKeyMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiKeyPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** API Key lifecycle, runtime authorization and per-key fixed-window rate limiting. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceAccessService {

  private static final int DEFAULT_RATE_LIMIT = 60;
  private static final int MAX_RATE_LIMIT = 100_000;
  private static final String KEY_PREFIX = "yak_ds_";

  private final DataServiceApiMapper apiMapper;
  private final DataServiceApiKeyMapper apiKeyMapper;
  private final SecureRandom secureRandom = new SecureRandom();
  private final ConcurrentHashMap<Long, RateWindow> rateWindows = new ConcurrentHashMap<>();
  private final AtomicLong rateChecks = new AtomicLong();

  public List<ApiKeyView> listKeys(Long apiId) {
    requireApi(apiId);
    return apiKeyMapper.selectList(
            Wrappers.<DataServiceApiKeyPO>lambdaQuery()
                .eq(DataServiceApiKeyPO::getApiId, apiId)
                .orderByDesc(DataServiceApiKeyPO::getCreateTime)
                .orderByDesc(DataServiceApiKeyPO::getId))
        .stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public AuthMode setAuthMode(Long apiId, String authMode) {
    DataServiceApiPO api = requireApi(apiId);
    AuthMode mode = normalizeMode(authMode);
    if (mode == AuthMode.API_KEY && validKeys(apiId).isEmpty()) {
      throw new IllegalArgumentException("启用 API Key 鉴权前请至少创建一个有效 Key");
    }
    api.setAuthMode(mode.name());
    api.setUpdateTime(LocalDateTime.now());
    apiMapper.updateById(api);
    return mode;
  }

  @Transactional
  public CreatedApiKey createKey(Long apiId, ApiKeyInput input) {
    requireApi(apiId);
    if (input == null || !StringUtils.hasText(input.name())) {
      throw new IllegalArgumentException("Key 名称不能为空");
    }
    String name = input.name().trim();
    if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
    int rateLimit = normalizeRateLimit(input.rateLimitPerMinute());
    LocalDateTime expiresAt = normalizeExpiration(input.expiresAt());
    SecretMaterial secret = createSecret();
    LocalDateTime now = LocalDateTime.now();

    DataServiceApiKeyPO key = new DataServiceApiKeyPO();
    key.setApiId(apiId);
    key.setName(name);
    key.setKeyPrefix(secret.prefix());
    key.setKeyHash(secret.hash());
    key.setEnabled(Boolean.TRUE);
    key.setRateLimitPerMinute(rateLimit);
    key.setExpiresAt(expiresAt);
    key.setCreateTime(now);
    key.setUpdateTime(now);
    apiKeyMapper.insert(key);
    return new CreatedApiKey(toView(key), secret.rawKey());
  }

  @Transactional
  public CreatedApiKey rotateKey(Long apiId, Long keyId) {
    DataServiceApiKeyPO key = requireKey(apiId, keyId);
    SecretMaterial secret = createSecret();
    key.setKeyPrefix(secret.prefix());
    key.setKeyHash(secret.hash());
    key.setEnabled(Boolean.TRUE);
    key.setLastUsedAt(null);
    key.setUpdateTime(LocalDateTime.now());
    apiKeyMapper.updateById(key);
    rateWindows.remove(keyId);
    return new CreatedApiKey(toView(key), secret.rawKey());
  }

  @Transactional
  public ApiKeyView updateKey(Long apiId, Long keyId, ApiKeyUpdate input) {
    DataServiceApiKeyPO key = requireKey(apiId, keyId);
    if (input == null) throw new IllegalArgumentException("Key 配置不能为空");
    if (StringUtils.hasText(input.name())) {
      String name = input.name().trim();
      if (name.length() > 128) throw new IllegalArgumentException("Key 名称不能超过 128 个字符");
      key.setName(name);
    }
    if (input.rateLimitPerMinute() != null) {
      key.setRateLimitPerMinute(normalizeRateLimit(input.rateLimitPerMinute()));
    }
    if (input.expiresAtSet()) {
      key.setExpiresAt(normalizeExpiration(input.expiresAt()));
    }
    key.setUpdateTime(LocalDateTime.now());
    apiKeyMapper.updateById(key);
    return toView(key);
  }

  @Transactional
  public ApiKeyView setKeyEnabled(Long apiId, Long keyId, boolean enabled) {
    DataServiceApiKeyPO key = requireKey(apiId, keyId);
    if (!enabled && Boolean.TRUE.equals(key.getEnabled()) && !isExpired(key)) {
      ensureAnotherValidKey(apiId, keyId);
    }
    key.setEnabled(enabled);
    key.setUpdateTime(LocalDateTime.now());
    apiKeyMapper.updateById(key);
    if (!enabled) rateWindows.remove(keyId);
    return toView(key);
  }

  @Transactional
  public void deleteKey(Long apiId, Long keyId) {
    DataServiceApiKeyPO key = requireKey(apiId, keyId);
    if (Boolean.TRUE.equals(key.getEnabled()) && !isExpired(key)) {
      ensureAnotherValidKey(apiId, keyId);
    }
    apiKeyMapper.deleteById(keyId);
    rateWindows.remove(keyId);
  }

  @Transactional
  public void deleteKeysForApi(Long apiId) {
    if (apiId == null) return;
    List<DataServiceApiKeyPO> keys = apiKeyMapper.selectList(
        Wrappers.<DataServiceApiKeyPO>lambdaQuery().eq(DataServiceApiKeyPO::getApiId, apiId));
    apiKeyMapper.delete(
        Wrappers.<DataServiceApiKeyPO>lambdaQuery().eq(DataServiceApiKeyPO::getApiId, apiId));
    keys.forEach(key -> rateWindows.remove(key.getId()));
  }

  public AccessContext authorize(DataServiceApiPO api, String rawKey) {
    AuthMode mode = normalizeMode(api == null ? null : api.getAuthMode());
    if (mode == AuthMode.NONE) return AccessContext.publicAccess();
    if (!StringUtils.hasText(rawKey)) {
      throw new DataServiceUnauthorizedException("缺少 X-API-Key 请求头");
    }

    String hash = hash(rawKey.trim());
    DataServiceApiKeyPO key = apiKeyMapper.selectOne(
        Wrappers.<DataServiceApiKeyPO>lambdaQuery()
            .eq(DataServiceApiKeyPO::getApiId, api.getId())
            .eq(DataServiceApiKeyPO::getKeyHash, hash)
            .last("LIMIT 1"));
    if (key == null || !Boolean.TRUE.equals(key.getEnabled())) {
      throw new DataServiceUnauthorizedException("API Key 无效或已停用");
    }
    if (isExpired(key)) {
      throw new DataServiceUnauthorizedException("API Key 已过期");
    }

    acquireRateLimit(key);
    key.setLastUsedAt(LocalDateTime.now());
    key.setUpdateTime(LocalDateTime.now());
    apiKeyMapper.updateById(key);
    return new AccessContext("API_KEY", key.getId(), key.getName(), key.getKeyPrefix());
  }

  private void acquireRateLimit(DataServiceApiKeyPO key) {
    int limit = normalizeRateLimit(key.getRateLimitPerMinute());
    long currentMinute = System.currentTimeMillis() / 60_000L;
    RateWindow window = rateWindows.compute(
        key.getId(),
        (id, current) -> current == null || current.minute() != currentMinute
            ? new RateWindow(currentMinute, new AtomicInteger())
            : current);
    int used = window.count().incrementAndGet();
    if ((rateChecks.incrementAndGet() & 255L) == 0L) {
      rateWindows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute - 1L);
    }
    if (used > limit) {
      throw new DataServiceRateLimitException(
          "API Key 已超过每分钟 " + limit + " 次调用限制",
          key.getId(),
          key.getName(),
          key.getKeyPrefix());
    }
  }

  private void ensureAnotherValidKey(Long apiId, Long excludedKeyId) {
    DataServiceApiPO api = requireApi(apiId);
    if (normalizeMode(api.getAuthMode()) != AuthMode.API_KEY) return;
    boolean hasAnother = validKeys(apiId).stream()
        .anyMatch(key -> !key.getId().equals(excludedKeyId));
    if (!hasAnother) {
      throw new IllegalArgumentException("API Key 鉴权已启用，不能移除最后一个有效 Key");
    }
  }

  private List<DataServiceApiKeyPO> validKeys(Long apiId) {
    return apiKeyMapper.selectList(
            Wrappers.<DataServiceApiKeyPO>lambdaQuery()
                .eq(DataServiceApiKeyPO::getApiId, apiId)
                .eq(DataServiceApiKeyPO::getEnabled, true))
        .stream()
        .filter(key -> !isExpired(key))
        .toList();
  }

  private DataServiceApiPO requireApi(Long apiId) {
    DataServiceApiPO api = apiId == null ? null : apiMapper.selectById(apiId);
    if (api == null) throw new IllegalArgumentException("数据服务不存在：" + apiId);
    return api;
  }

  private DataServiceApiKeyPO requireKey(Long apiId, Long keyId) {
    if (keyId == null) throw new IllegalArgumentException("API Key ID 不能为空");
    DataServiceApiKeyPO key = apiKeyMapper.selectById(keyId);
    if (key == null || apiId == null || !apiId.equals(key.getApiId())) {
      throw new IllegalArgumentException("API Key 不存在：" + keyId);
    }
    return key;
  }

  private AuthMode normalizeMode(String value) {
    if (!StringUtils.hasText(value)) return AuthMode.NONE;
    try {
      return AuthMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知数据服务鉴权模式：" + value, exception);
    }
  }

  private int normalizeRateLimit(Integer value) {
    int result = value == null ? DEFAULT_RATE_LIMIT : value;
    if (result < 1 || result > MAX_RATE_LIMIT) {
      throw new IllegalArgumentException("每分钟调用限制必须在 1~100000 之间");
    }
    return result;
  }

  private LocalDateTime normalizeExpiration(LocalDateTime value) {
    if (value != null && !value.isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("API Key 过期时间必须晚于当前时间");
    }
    return value;
  }

  private boolean isExpired(DataServiceApiKeyPO key) {
    return key.getExpiresAt() != null && !key.getExpiresAt().isAfter(LocalDateTime.now());
  }

  private SecretMaterial createSecret() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String raw = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String prefix = raw.substring(0, Math.min(16, raw.length()));
    return new SecretMaterial(raw, prefix, hash(raw));
  }

  private String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private ApiKeyView toView(DataServiceApiKeyPO key) {
    return new ApiKeyView(
        key.getId(), key.getApiId(), key.getName(), key.getKeyPrefix(),
        Boolean.TRUE.equals(key.getEnabled()), key.getRateLimitPerMinute(), key.getExpiresAt(),
        key.getLastUsedAt(), key.getCreateTime(), key.getUpdateTime());
  }

  public enum AuthMode {
    NONE,
    API_KEY
  }

  public record ApiKeyInput(
      String name,
      Integer rateLimitPerMinute,
      LocalDateTime expiresAt) {}

  /** expiresAtSet distinguishes "leave unchanged" from "clear expiration". */
  public record ApiKeyUpdate(
      String name,
      Integer rateLimitPerMinute,
      LocalDateTime expiresAt,
      boolean expiresAtSet) {}

  public record ApiKeyView(
      Long id,
      Long apiId,
      String name,
      String keyPrefix,
      Boolean enabled,
      Integer rateLimitPerMinute,
      LocalDateTime expiresAt,
      LocalDateTime lastUsedAt,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  /** secret is intentionally returned only by create/rotate endpoints. */
  public record CreatedApiKey(ApiKeyView key, String secret) {}

  public record AccessContext(
      String callerType,
      Long apiKeyId,
      String apiKeyName,
      String apiKeyPrefix) {

    public static AccessContext publicAccess() {
      return new AccessContext("PUBLIC", null, null, null);
    }

    public static AccessContext console() {
      return new AccessContext("CONSOLE", null, null, null);
    }

    public static AccessContext rejectedApiKey() {
      return new AccessContext("API_KEY", null, null, null);
    }
  }

  private record SecretMaterial(String rawKey, String prefix, String hash) {}

  private record RateWindow(long minute, AtomicInteger count) {}
}
