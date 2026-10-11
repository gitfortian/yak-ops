package io.yak.ops.dao.repository.datasource;

import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.Optional;

/**
 * Defines Workspace-scoped datasource persistence operations without exposing MyBatis details.
 */
public interface DataSourceEntityRepository extends BaseRepository<DataSourceEntity> {

    /** Queries datasource pages using filters scoped to the requested Workspace. */
    PageData<DataSourceEntity> queryPage(String workspaceId, DataSourcePageQuery query);

    /** Loads one datasource by identifier within the requested Workspace. */
    Optional<DataSourceEntity> queryById(String workspaceId, String id);

    /** Updates a datasource only within its owning Workspace. */
    DataSourceEntity update(String workspaceId, DataSourceEntity entity);

    /** Deletes a datasource only within its owning Workspace. */
    int deleteById(String workspaceId, String id);

    /** Checks name uniqueness within a Workspace, optionally excluding the current datasource. */
    boolean existsByName(String workspaceId, String name, String excludeId);
}
