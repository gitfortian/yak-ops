package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.mapper.modeling.ModelingRelationMapper;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes semantic relation statements inside the owning business transaction. */
@Repository
public class ModelingRelationRepositoryImpl implements ModelingRelationRepository {
    @Resource
    private ModelingRelationMapper mapper;

    @Override
    public Optional<ModelingRelationEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public Optional<ModelingRelationEntity> lock(String workspaceId, String id) {
        return Optional.ofNullable(mapper.lock(workspaceId, id));
    }

    @Override
    public long countName(String workspaceId, String name, String excludeId) {
        return mapper.countName(workspaceId, name, excludeId);
    }

    @Override
    public List<ModelingRelationEntity> listByModel(String workspaceId, String modelId) {
        return mapper.listByModel(workspaceId, modelId);
    }

    @Override
    public long countByModel(String workspaceId, String modelId) {
        return mapper.countByModel(workspaceId, modelId);
    }

    @Override
    public void add(ModelingRelationEntity row) {
        mapper.insert(row);
    }

    @Override
    public boolean update(ModelingRelationEntity row) {
        return mapper.update(row) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public List<ModelingRelationColumnEntity> columns(String relationId) {
        return mapper.columns(relationId);
    }

    @Override
    public void deleteColumns(String relationId) {
        mapper.deleteColumns(relationId);
    }

    @Override
    public void addColumn(ModelingRelationColumnEntity row) {
        mapper.insertColumn(row);
    }

    @Override
    public List<RelationKeyScanRow> scanUnstandardizedKeys(String workspaceId) {
        return mapper.scanUnstandardizedKeys(workspaceId);
    }
}
