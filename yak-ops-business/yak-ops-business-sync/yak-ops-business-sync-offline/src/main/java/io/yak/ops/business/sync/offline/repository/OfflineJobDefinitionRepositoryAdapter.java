package io.yak.ops.business.sync.offline.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao.PageQuery;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflinePage;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** MyBatis 持久化模型与任务定义领域模型之间的适配器。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineJobDefinitionRepositoryAdapter implements OfflineJobDefinitionRepository {
  private final OfflineJobDefinitionDao dao;

  @Override
  public void lock(Long id) {
    if (dao.lockById(id) == null) {
      throw new IllegalArgumentException("离线同步任务不存在：" + id);
    }
  }

  @Override
  public Optional<OfflineJobDefinition> findById(Long id) {
    return Optional.ofNullable(toDomain(dao.selectById(id)));
  }

  @Override
  public boolean insert(OfflineJobDefinition definition) {
    return dao.insert(toPO(definition));
  }

  @Override
  public boolean update(OfflineJobDefinition definition) {
    return dao.updateById(toPO(definition));
  }

  @Override
  public boolean delete(Long id) {
    return dao.deleteById(id);
  }

  @Override
  public boolean existsByName(String jobName, Long excludeId) {
    return dao.existsByName(jobName, excludeId);
  }

  @Override
  public OfflinePage<OfflineJobDefinition> page(OfflineDefinitionQuery query) {
    OfflineDefinitionQuery q = query == null
        ? new OfflineDefinitionQuery(1, 10, null, null, null, null, null, null, null, null, null)
        : query;
    IPage<OfflineJobDefinitionPO> page = dao.selectPage(
        new PageQuery(
            q.current(), q.pageSize(), q.id(), q.jobName(), q.status(),
            q.sourceType(), q.sinkType(), q.sourceTable(), q.sinkTable(),
            q.createTimeStart(), q.createTimeEnd()));
    List<OfflineJobDefinition> records = page.getRecords().stream().map(this::toDomain).toList();
    return new OfflinePage<>(records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
  }

  private OfflineJobDefinition toDomain(OfflineJobDefinitionPO po) {
    if (po == null) return null;
    OfflineJobDefinition value = new OfflineJobDefinition();
    BeanUtils.copyProperties(po, value);
    return value;
  }

  private OfflineJobDefinitionPO toPO(OfflineJobDefinition value) {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    BeanUtils.copyProperties(value, po);
    return po;
  }
}
