package io.yak.ops.dao.repository.task;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.time.LocalDateTime;
import java.util.Optional;

/** Single writer of generic Task Definition identity and publication state. */
public interface DefinitionRepository extends BaseRepository<DefinitionEntity> {
    Optional<DefinitionEntity> queryById(String workspaceId, String id);

    Optional<DefinitionEntity> queryByIdForUpdate(String workspaceId, String id);

    PageData<DefinitionEntity> queryPage(DefinitionPageQuery query);

    DefinitionEntity update(String workspaceId, DefinitionEntity entity);

    boolean softDelete(String workspaceId, String id, LocalDateTime at, String operator);

    boolean existsByName(String workspaceId, String name, String excludeId);
}
