package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.dao.mapper.DatasetOverviewMapper;
import io.yak.ops.business.dataset.dao.model.DatasetOverviewSummaryPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for bounded Dataset overview projections. */
@Repository
@RequiredArgsConstructor
public class DatasetOverviewRepositoryAdapter implements DatasetOverviewRepository {

  private final DatasetOverviewMapper mapper;

  @Override
  public Summary summarize(Instant from, Instant to) {
    DatasetOverviewSummaryPO value =
        mapper.selectSummary(Timestamp.from(from), Timestamp.from(to));
    if (value == null) return new Summary(0L, 0L);
    return new Summary(number(value.getDatasetCount()), number(value.getCreatedCount()));
  }

  @Override
  public List<Dataset> listRecent(int limit) {
    return mapper.selectRecent(normalizeLimit(limit)).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Dataset> listRecentOnline(int limit) {
    return mapper.selectRecentOnline(normalizeLimit(limit)).stream().map(this::toDomain).toList();
  }

  private Dataset toDomain(DatasetPO value) {
    return new Dataset(
        value.getId(),
        value.getName(),
        value.getDescription(),
        DatasetStatus.valueOf(value.getStatus()),
        value.getCurrentVersionId(),
        instant(value.getCreateTime()),
        instant(value.getUpdateTime()));
  }

  private int normalizeLimit(int limit) {
    return Math.max(1, Math.min(20, limit));
  }

  private long number(Long value) {
    return value == null ? 0L : Math.max(0L, value);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
