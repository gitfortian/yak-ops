package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import io.yak.ops.dao.mapper.modeling.DimensionMapper;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes concept dimension statements inside the owning business transaction. */
@Repository
public class DimensionRepositoryImpl implements DimensionRepository {
    @Resource
    private DimensionMapper mapper;

    @Override
    public List<DimensionEntity> dimensions(
            String workspaceId, DimensionScope scope, String domainId, String datamartId) {
        return mapper.dimensions(workspaceId, scope, domainId, datamartId);
    }

    @Override
    public Optional<DimensionEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public Optional<DimensionEntity> lock(String workspaceId, String id) {
        return Optional.ofNullable(mapper.lock(workspaceId, id));
    }

    @Override
    public long countCode(String workspaceId, String ownerScopeId, String code, String excludeId) {
        return mapper.countCode(workspaceId, ownerScopeId, code, excludeId);
    }

    @Override
    public boolean domainExists(String workspaceId, String id) {
        return mapper.domain(workspaceId, id) > 0;
    }

    @Override
    public boolean datamartExists(String workspaceId, String id) {
        return mapper.datamart(workspaceId, id) > 0;
    }

    @Override
    public void add(DimensionEntity row) {
        mapper.insert(row);
    }

    @Override
    public boolean update(DimensionEntity row) {
        return mapper.update(row) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }
}
