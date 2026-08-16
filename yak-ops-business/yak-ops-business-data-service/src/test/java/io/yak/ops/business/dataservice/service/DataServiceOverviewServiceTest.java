package io.yak.ops.business.dataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceOverviewServiceTest {

  @Test
  void overviewAggregatesStatusTrendHotApiAndFailures() {
    DataServiceApiMapper apiMapper = mock(DataServiceApiMapper.class);
    DataServiceCallLogMapper logMapper = mock(DataServiceCallLogMapper.class);
    DataServiceOverviewService service = new DataServiceOverviewService(apiMapper, logMapper);
    LocalDateTime now = LocalDateTime.of(2026, 8, 16, 17, 30);

    when(apiMapper.selectList(any())).thenReturn(List.of(
        api(1L, "订单查询", "/orders", true),
        api(2L, "用户查询", "/users", true),
        api(3L, "历史查询", "/history", false)));
    when(logMapper.selectList(any())).thenReturn(List.of(
        log(1L, 1L, "订单查询", "/orders", true, 100L, 5, null, now.minusHours(1)),
        log(2L, 1L, "订单查询", "/orders", false, 200L, 0, "datasource timeout", now.minusMinutes(55)),
        log(3L, 2L, "用户查询", "/users", true, 300L, 10, null, now.minusMinutes(10))));

    DataServiceOverviewService.Overview overview = service.overviewAt("24h", now);

    assertThat(overview.apiTotal()).isEqualTo(3);
    assertThat(overview.runningApis()).isEqualTo(2);
    assertThat(overview.stoppedApis()).isEqualTo(1);
    assertThat(overview.totalCalls()).isEqualTo(3);
    assertThat(overview.successCalls()).isEqualTo(2);
    assertThat(overview.failureCalls()).isEqualTo(1);
    assertThat(overview.successRate()).isEqualTo(66.7D);
    assertThat(overview.averageDurationMs()).isEqualTo(200);
    assertThat(overview.totalRows()).isEqualTo(15);
    assertThat(overview.trend()).hasSize(24);
    assertThat(overview.trend().stream().mapToLong(DataServiceOverviewService.TrendPoint::calls).sum())
        .isEqualTo(3);
    assertThat(overview.hotApis()).hasSize(2);
    assertThat(overview.hotApis().get(0).apiId()).isEqualTo(1L);
    assertThat(overview.hotApis().get(0).calls()).isEqualTo(2);
    assertThat(overview.recentFailures()).hasSize(1);
    assertThat(overview.recentFailures().get(0).errorMessage()).isEqualTo("datasource timeout");
  }

  @Test
  void overviewRejectsUnsupportedRange() {
    DataServiceOverviewService service = new DataServiceOverviewService(
        mock(DataServiceApiMapper.class),
        mock(DataServiceCallLogMapper.class));

    assertThatThrownBy(() -> service.overviewAt("90d", LocalDateTime.of(2026, 8, 16, 17, 30)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("24h、7d、30d");
  }

  private DataServiceApiPO api(Long id, String name, String path, boolean enabled) {
    DataServiceApiPO api = new DataServiceApiPO();
    api.setId(id);
    api.setName(name);
    api.setPath(path);
    api.setEnabled(enabled);
    return api;
  }

  private DataServiceCallLogPO log(
      Long id,
      Long apiId,
      String name,
      String path,
      boolean success,
      long duration,
      int rows,
      String error,
      LocalDateTime time) {
    DataServiceCallLogPO log = new DataServiceCallLogPO();
    log.setId(id);
    log.setApiId(apiId);
    log.setServiceName(name);
    log.setServicePath(path);
    log.setSuccess(success);
    log.setDurationMs(duration);
    log.setRowCount(rows);
    log.setErrorMessage(error);
    log.setCreateTime(time);
    return log;
  }
}
