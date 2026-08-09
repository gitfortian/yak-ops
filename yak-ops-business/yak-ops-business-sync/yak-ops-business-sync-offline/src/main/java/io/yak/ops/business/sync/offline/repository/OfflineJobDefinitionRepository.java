package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflinePage;
import java.util.Optional;

/** 离线同步任务定义领域仓储。 */
public interface OfflineJobDefinitionRepository {
  void lock(Long id);
  Optional<OfflineJobDefinition> findById(Long id);
  Optional<OfflineJobDefinition> findForViewById(Long id);
  boolean insert(OfflineJobDefinition definition);
  boolean update(OfflineJobDefinition definition);
  boolean delete(Long id);
  boolean existsByName(String jobName, Long excludeId);
  OfflinePage<OfflineJobDefinition> page(OfflineDefinitionQuery query);
}
