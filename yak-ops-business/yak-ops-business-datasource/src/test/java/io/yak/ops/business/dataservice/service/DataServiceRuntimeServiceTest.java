package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.service.DataServiceRuntimeService.RuntimeSnapshot;
import io.yak.ops.business.dataservice.service.DataServiceService.QueryResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataServiceRuntimeServiceTest {

  @Test
  void cacheReusesSuccessfulQueryAndTracksHitRate() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T13:00:00Z"));
    DataServiceRuntimeService service = new DataServiceRuntimeService(clock);
    DataServiceApiPO api = api(1L);
    api.setCacheEnabled(true);
    api.setCircuitBreakerEnabled(false);
    AtomicInteger loads = new AtomicInteger();

    QueryResponse first = service.execute(api, "same-query", () -> {
      loads.incrementAndGet();
      return response();
    });
    QueryResponse second = service.execute(api, "same-query", () -> {
      loads.incrementAndGet();
      return response();
    });

    assertThat(first.rowCount()).isEqualTo(1);
    assertThat(second.durationMs()).isZero();
    assertThat(loads).hasValue(1);
    RuntimeSnapshot snapshot = service.snapshot(api);
    assertThat(snapshot.totalCalls()).isEqualTo(2);
    assertThat(snapshot.cacheHits()).isEqualTo(1);
    assertThat(snapshot.cacheEntries()).isEqualTo(1);
    assertThat(snapshot.cacheHitRate()).isEqualTo(0.5D);
  }

  @Test
  void circuitOpensAfterConsecutiveDatasourceFailures() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T13:00:00Z"));
    DataServiceRuntimeService service = new DataServiceRuntimeService(clock);
    DataServiceApiPO api = api(2L);
    api.setCircuitBreakerEnabled(true);
    api.setCircuitFailureThreshold(2);
    api.setCircuitRecoverySeconds(30);

    assertThatThrownBy(() -> service.execute(api, "a", () -> failure("db down")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.execute(api, "b", () -> failure("db down")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> service.execute(api, "c", DataServiceRuntimeServiceTest::response))
        .isInstanceOf(DataServiceCircuitOpenException.class)
        .hasMessageContaining("熔断");

    RuntimeSnapshot snapshot = service.snapshot(api);
    assertThat(snapshot.circuitState()).isEqualTo("OPEN");
    assertThat(snapshot.failureCalls()).isEqualTo(3);
    assertThat(snapshot.circuitRejected()).isEqualTo(1);
  }

  @Test
  void halfOpenProbeClosesCircuitAfterRecoveryWindow() {
    MutableClock clock = new MutableClock(Instant.parse("2026-08-15T13:00:00Z"));
    DataServiceRuntimeService service = new DataServiceRuntimeService(clock);
    DataServiceApiPO api = api(3L);
    api.setCircuitBreakerEnabled(true);
    api.setCircuitFailureThreshold(1);
    api.setCircuitRecoverySeconds(10);

    assertThatThrownBy(() -> service.execute(api, "a", () -> failure("db down")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.snapshot(api).circuitState()).isEqualTo("OPEN");

    clock.advanceSeconds(11);
    QueryResponse recovered = service.execute(api, "b", DataServiceRuntimeServiceTest::response);

    assertThat(recovered.rowCount()).isEqualTo(1);
    assertThat(service.snapshot(api).circuitState()).isEqualTo("CLOSED");
  }

  private static DataServiceApiPO api(Long id) {
    DataServiceApiPO api = new DataServiceApiPO();
    api.setId(id);
    api.setCacheEnabled(false);
    api.setCacheTtlSeconds(60);
    api.setCacheMaxEntries(10);
    api.setCircuitBreakerEnabled(false);
    api.setCircuitFailureThreshold(5);
    api.setCircuitRecoverySeconds(30);
    return api;
  }

  private static QueryResponse response() {
    return new QueryResponse(
        List.of("id"),
        List.of(Map.of("id", 1)),
        false,
        1,
        15L);
  }

  private static QueryResponse failure(String message) {
    throw new IllegalStateException(message);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
