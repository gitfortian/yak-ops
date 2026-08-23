package io.yak.ops.business.sync.offline.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao.PageQuery;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** MyBatis 持久化模型与 ExecutionAttempt 兼容视图之间的适配器。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineJobExecutionRepositoryAdapter implements OfflineJobExecutionRepository {
  private final OfflineJobExecutionDao dao;

  @Override
  public Optional<OfflineJobExecution> findById(Long id) {
    return Optional.ofNullable(toDomain(dao.selectById(id)));
  }

  @Override
  public Optional<OfflineJobExecution> findByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(toDomain(dao.selectByIdempotencyKey(idempotencyKey)));
  }

  @Override
  public List<OfflineJobExecution> findByBatchId(Long batchId) {
    return dao.selectByBatchId(batchId).stream().map(this::toDomain).toList();
  }

  @Override
  public boolean insert(OfflineJobExecution execution) {
    OfflineJobExecutionPO po = toPO(execution);
    boolean inserted = dao.insert(po);
    if (inserted) execution.setId(po.getId());
    return inserted;
  }

  @Override
  public boolean update(OfflineJobExecution execution) {
    return dao.updateById(toPO(execution));
  }

  @Override
  public List<OfflineJobExecution> findActiveExecutions(int limit) {
    return dao.selectActiveExecutions(limit).stream().map(this::toDomain).toList();
  }

  @Override
  public List<OfflineJobExecution> findRetryCandidates(LocalDateTime now, int limit) {
    return dao.selectRetryCandidates(now, limit).stream().map(this::toDomain).toList();
  }

  @Override
  public boolean reserveRetry(Long executionId) {
    if (executionId == null || executionId <= 0L) {
      throw new IllegalArgumentException("ExecutionId 必须大于 0");
    }
    return dao.reserveRetry(executionId, LocalDateTime.now());
  }

  @Override
  public PageData<OfflineJobExecution> page(OfflineExecutionQuery query) {
    OfflineExecutionQuery q = query == null ? new OfflineExecutionQuery(1, 10, null, null) : query;
    IPage<OfflineJobExecutionPO> page = dao.selectPage(
        new PageQuery(q.current(), q.pageSize(), q.jobDefinitionId(), q.status()));
    return new PageData<>(
        page.getRecords().stream().map(this::toDomain).toList(),
        page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
  }

  private OfflineJobExecution toDomain(OfflineJobExecutionPO po) {
    if (po == null) return null;
    OfflineJobExecution value = new OfflineJobExecution();
    BeanUtils.copyProperties(po, value);
    return value;
  }

  private OfflineJobExecutionPO toPO(OfflineJobExecution value) {
    OfflineJobExecutionPO po = new OfflineJobExecutionPO();
    BeanUtils.copyProperties(value, po);
    return po;
  }
}
