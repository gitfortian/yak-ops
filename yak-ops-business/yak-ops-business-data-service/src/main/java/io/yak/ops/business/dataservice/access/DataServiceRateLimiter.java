package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Process-local per-key fixed-window limiter. It does not represent persisted access policy truth. */
@Component
public class DataServiceRateLimiter {
  private final ConcurrentHashMap<Long, RateWindow> windows = new ConcurrentHashMap<>();
  private final AtomicLong checks = new AtomicLong();

  public void acquire(DataServiceApiKey key) {
    long currentMinute = System.currentTimeMillis() / 60_000L;
    RateWindow window = windows.compute(key.id(), (id, current) ->
        current == null || current.minute() != currentMinute
            ? new RateWindow(currentMinute, new AtomicInteger()) : current);
    int used = window.count().incrementAndGet();
    if ((checks.incrementAndGet() & 255L) == 0L) {
      windows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute - 1L);
    }
    if (used > key.rateLimitPerMinute()) {
      throw new DataServiceRateLimitException(
          "API Key 已超过每分钟 " + key.rateLimitPerMinute() + " 次调用限制",
          key.id(), key.name(), key.keyPrefix());
    }
  }

  public void invalidate(Long keyId) { if (keyId != null) windows.remove(keyId); }
  private record RateWindow(long minute, AtomicInteger count) {}
}
