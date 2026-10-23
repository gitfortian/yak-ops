package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.mapper.modeling.ModelMapper;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.StandardInheritance;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes DWD/DIM statements inside the owning business transaction. */
@Repository
public class ModelRepositoryImpl implements ModelRepository {
    @Resource
    private ModelMapper mapper;

    @Override
    public List<ModelEntity> page(
            String workspaceId,
            String type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword,
            int limit,
            int offset) {
        return mapper.page(workspaceId, type, layerId, ownerId, domainId, datamartId, keyword, limit, offset);
    }

    @Override
    public long count(
            String workspaceId,
            String type,
            String layerId,
            String ownerId,
            String domainId,
            String datamartId,
            String keyword) {
        return mapper.count(workspaceId, type, layerId, ownerId, domainId, datamartId, keyword);
    }

    @Override
    public Optional<ModelEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public Optional<ModelEntity> lock(String workspaceId, String id) {
        return Optional.ofNullable(mapper.lock(workspaceId, id));
    }

    @Override
    public boolean occupied(String workspaceId, String name, String excludeId) {
        return mapper.countName(workspaceId, name, excludeId) > 0;
    }

    @Override
    public void add(ModelEntity row) {
        mapper.insert(row);
    }

    @Override
    public boolean update(ModelEntity row) {
        return mapper.update(row) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public List<ModelFieldEntity> fields(String modelId) {
        return mapper.fields(modelId);
    }

    @Override
    public void deleteFields(String modelId) {
        mapper.deleteFields(modelId);
    }

    @Override
    public void addField(ModelFieldEntity row) {
        mapper.insertField(row);
    }

    @Override
    public StandardInheritance standard(String workspaceId, String id) {
        var projection = mapper.standard(workspaceId, id);
        if (projection == null) return null;
        return new StandardInheritance(
                projection.getEnCode(),
                projection.getDataType() == null ? null : DataType.valueOf(projection.getDataType()),
                projection.getLengthPrecision());
    }
}
