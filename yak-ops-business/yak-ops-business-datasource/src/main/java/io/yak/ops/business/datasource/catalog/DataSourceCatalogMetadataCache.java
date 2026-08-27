package io.yak.ops.business.datasource.catalog;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Small in-process TTL cache for stable datasource catalog metadata.
 *
 * <p>The datasource update timestamp is part of the key, so editing connection configuration naturally
 * moves subsequent reads to a new cache namespace without exposing connection secrets in cache keys.
 */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceCatalogMetadataCache {

  private static final int MAX_ENTRIES = 2048;

  private final ConcurrentMap<CacheKey, CacheEntry<?>> entries = new ConcurrentHashMap<>();

  public <T> T getOrLoad(CacheKey key, int ttlSeconds, Supplier<T> loader) {
    Objects.requireNonNull(key, "cache key must not be null");
    Objects.requireNonNull(loader, "cache loader must not be null");
    if (ttlSeconds <= 0) {
      return loader.get();
    }

    CacheEntry<?> entry =
        entries.compute(
            key,
            (ignored, current) -> {
              long now = System.nanoTime();
              if (current != null && current.expiresAtNanos() > now) {
                return current;
              }
              T value = loader.get();
              return new CacheEntry<>(
                  value,
                  now + TimeUnit.SECONDS.toNanos(Math.max(1L, ttlSeconds)));
            });

    if (entries.size() > MAX_ENTRIES) {
      purgeExpired(System.nanoTime());
    }

    @SuppressWarnings("unchecked")
    T value = (T) entry.value();
    return value;
  }

  public CacheKey key(
      DataSourceDefinition definition,
      String kind,
      Object... qualifiers) {
    Objects.requireNonNull(definition, "datasource definition must not be null");
    List<String> normalizedQualifiers =
        Arrays.stream(qualifiers == null ? new Object[0] : qualifiers)
            .map(value -> value == null ? "" : String.valueOf(value))
            .toList();
    return new CacheKey(
        definition.getId(),
        definition.getUpdateTime(),
        Objects.requireNonNull(kind, "cache kind must not be null"),
        normalizedQualifiers);
  }

  void clear() {
    entries.clear();
  }

  int size() {
    return entries.size();
  }

  private void purgeExpired(long now) {
    entries.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
  }

  public record CacheKey(
      Long dataSourceId,
      LocalDateTime dataSourceUpdateTime,
      String kind,
      List<String> qualifiers) {

    public CacheKey {
      qualifiers = qualifiers == null ? List.of() : List.copyOf(qualifiers);
    }
  }

  private record CacheEntry<T>(T value, long expiresAtNanos) {}
}
