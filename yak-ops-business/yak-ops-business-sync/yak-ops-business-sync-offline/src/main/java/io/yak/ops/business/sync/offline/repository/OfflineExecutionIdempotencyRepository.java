package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Small lookup adapter used to make workflow Attempt submission locally idempotent. */
@ConditionalOnOfflineSyncEnabled
@Repository
public class OfflineExecutionIdempotencyRepository {
  private final JdbcTemplate jdbc;
  private final OfflineJobExecutionDao executionDao;

  public OfflineExecutionIdempotencyRepository(
      @Qualifier("offlineSyncDataSource") DataSource dataSource,
      OfflineJobExecutionDao executionDao) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.executionDao = executionDao;
  }

  public Optional<OfflineJobExecutionPO> findByKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
    List<Long> ids = jdbc.queryForList(
        "SELECT id FROM yak_offline_job_execution WHERE idempotency_key=? LIMIT 1",
        Long.class,
        idempotencyKey.trim());
    if (ids.isEmpty()) return Optional.empty();
    return Optional.ofNullable(executionDao.selectById(ids.get(0)));
  }
}
