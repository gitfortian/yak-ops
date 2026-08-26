package io.yak.ops.business.dataset.repository;

import io.yak.ops.business.dataset.Dataset;
import io.yak.ops.business.dataset.DatasetStatus;
import io.yak.ops.business.dataset.dao.mapper.DatasetOverviewMapper;
import io.yak.ops.business.dataset.dao.model.DatasetOverviewSummaryPO;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for bounded Dataset overview projections. */
@Repository
public class DatasetOverviewRepositoryAdapter implements DatasetOverviewRepository {

  private final DatasetOverviewMapper mapper;
  private final CurrentProject currentProject;

  @Autowired
  public DatasetOverviewRepositoryAdapter(
      DatasetOverviewMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  public DatasetOverviewRepositoryAdapter(DatasetOverviewMapper mapper) {
    this(mapper, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public Summary summarize(Instant from, Instant to) {
    Long projectId = currentProjectId();
    DatasetOverviewSummaryPO value =
        projectId == null
            ? mapper.selectSummary(Timestamp.from(from), Timestamp.from(to))
            : mapper.selectSummaryByProject(
                projectId, Timestamp.from(from), Timestamp.from(to));
    if (value == null) return new Summary(0L, 0L);
    return new Summary(number(value.getDatasetCount()), number(value.getCreatedCount()));
  }

  @Override
  public List<Dataset> listRecent(int limit) {
    int normalized = normalizeLimit(limit);
    Long projectId = currentProjectId();
    List<DatasetPO> rows =
        projectId == null
            ? mapper.selectRecent(normalized)
            : mapper.selectRecentByProject(projectId, normalized);
    return rows.stream().map(this::toDomain).toList();
  }

  @Override
  public List<Dataset> listRecentOnline(int limit) {
    int normalized = normalizeLimit(limit);
    Long projectId = currentProjectId();
    List<DatasetPO> rows =
        projectId == null
            ? mapper.selectRecentOnline(normalized)
            : mapper.selectRecentOnlineByProject(projectId, normalized);
    return rows.stream().map(this::toDomain).toList();
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
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
