package io.yak.ops.business.quality.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.quality.repository.QualityOverviewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityOverviewReaderTest {

  @Test
  void shouldBuildOverviewFromPersistedQualityEvidence() {
    LocalDateTime issueTime = LocalDateTime.now().minusHours(1);
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(4L, 12L, 8L, 2L, 10L, 8L, 2L),
        List.of(
            new QualityOverviewRepository.DimensionSummary("完整性", 4L, 3L, 1L),
            new QualityOverviewRepository.DimensionSummary("唯一性", 6L, 5L, 1L)),
        List.of(
            new QualityOverviewRepository.IssueSummary(
                91L,
                "quality-20260826-1",
                11L,
                "订单完整性监控",
                "warehouse.dwd_order",
                "dwd_order",
                "订单号不能为空",
                "完整性",
                "order_id",
                "NOT_PASSED",
                issueTime)));

    QualityOverviewReader.Overview overview = new QualityOverviewReader(repository).overview();

    assertThat(overview.rangeStart()).isEqualTo(LocalDate.now().minusDays(6));
    assertThat(overview.rangeEnd()).isEqualTo(LocalDate.now());
    assertThat(overview.passRate()).isEqualTo(80D);
    assertThat(overview.monitoredTableCount()).isEqualTo(4L);
    assertThat(overview.enabledRuleCount()).isEqualTo(12L);
    assertThat(overview.todayExecutionCount()).isEqualTo(8L);
    assertThat(overview.todayIssueTableCount()).isEqualTo(2L);
    assertThat(overview.recentIssueCount()).isEqualTo(2L);
    assertThat(overview.dimensions())
        .extracting(QualityOverviewReader.DimensionHealth::passRate)
        .containsExactly(75D, 83.33D);
    assertThat(overview.recentIssues())
        .singleElement()
        .satisfies(issue -> {
          assertThat(issue.executionNo()).isEqualTo("quality-20260826-1");
          assertThat(issue.ruleName()).isEqualTo("订单号不能为空");
          assertThat(issue.dimension()).isEqualTo("完整性");
          assertThat(issue.checkResult()).isEqualTo("NOT_PASSED");
          assertThat(issue.queuedAt()).isEqualTo(issueTime);
        });
  }

  @Test
  void shouldKeepPassRateUnavailableWhenNoRulesWereExecuted() {
    QualityOverviewRepository repository = new StubRepository(
        new QualityOverviewRepository.OverviewStats(0L, 0L, 0L, 0L, 0L, 0L, 0L),
        List.of(),
        List.of());

    QualityOverviewReader.Overview overview = new QualityOverviewReader(repository).overview();

    assertThat(overview.passRate()).isNull();
    assertThat(overview.monitoredTableCount()).isZero();
    assertThat(overview.todayExecutionCount()).isZero();
    assertThat(overview.dimensions()).isEmpty();
    assertThat(overview.recentIssues()).isEmpty();
  }

  private record StubRepository(
      OverviewStats stats,
      List<DimensionSummary> dimensions,
      List<IssueSummary> recentIssues) implements QualityOverviewRepository {

    @Override
    public OverviewStats stats(
        LocalDateTime todayStart,
        LocalDateTime todayEnd,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd) {
      return stats;
    }

    @Override
    public List<DimensionSummary> dimensions(
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd) {
      return dimensions;
    }

    @Override
    public List<IssueSummary> recentIssues(
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        int limit) {
      return recentIssues.stream().limit(limit).toList();
    }
  }
}
