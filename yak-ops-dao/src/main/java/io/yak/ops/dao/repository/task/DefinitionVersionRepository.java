package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import java.util.List;
import java.util.Optional;

/** Append-only immutable definition version persistence. */
public interface DefinitionVersionRepository {
    DefinitionVersionEntity append(DefinitionVersionEntity version);

    Optional<DefinitionVersionEntity> queryCurrent(String workspaceId, String definitionId, int version);

    List<DefinitionVersionEntity> queryByDefinition(String workspaceId, String definitionId);
}
