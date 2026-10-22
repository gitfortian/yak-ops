package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.mapper.modeling.LayerMapper;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes layer statements inside the owning business transaction. */
@Repository
public class LayerRepositoryImpl implements LayerRepository {
    @Resource
    private LayerMapper mapper;

    @Override
    public List<LayerEntity> layers(String workspaceId) {
        return mapper.layers(workspaceId);
    }

    @Override
    public Optional<LayerEntity> layer(String workspaceId, String id) {
        return Optional.ofNullable(mapper.layer(workspaceId, id));
    }

    @Override
    public long builtinCount(String workspaceId) {
        return mapper.builtinCount(workspaceId);
    }

    @Override
    public void seed(List<LayerEntity> rows) {
        mapper.seed(rows);
    }

    @Override
    public void add(LayerEntity row) {
        mapper.insert(row);
    }

    @Override
    public boolean update(LayerEntity row) {
        return mapper.update(row) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean occupied(String workspaceId, String enCode, String excludeId) {
        return mapper.occupied(workspaceId, enCode, excludeId) > 0;
    }

    @Override
    public List<DataSourceEntity> datasources(String workspaceId) {
        return mapper.datasources(workspaceId);
    }

    @Override
    public boolean datasourceExists(String workspaceId, String id) {
        return mapper.datasource(workspaceId, id) > 0;
    }
}
