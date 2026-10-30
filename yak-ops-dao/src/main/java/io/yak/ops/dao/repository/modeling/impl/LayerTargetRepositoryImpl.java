package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import io.yak.ops.dao.mapper.modeling.LayerTargetMapper;
import io.yak.ops.dao.repository.modeling.LayerTargetRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes layer target statements inside the owning business transaction. */
@Repository
public class LayerTargetRepositoryImpl implements LayerTargetRepository {
    @Resource
    private LayerTargetMapper mapper;

    @Override
    public Optional<LayerTargetEntity> target(String workspaceId, String layerId, String environment) {
        return Optional.ofNullable(mapper.find(workspaceId, layerId, environment));
    }

    @Override
    public List<LayerTargetEntity> listByLayer(String workspaceId, String layerId) {
        return mapper.listByLayer(workspaceId, layerId);
    }

    @Override
    public void replace(String workspaceId, String layerId, List<LayerTargetEntity> rows) {
        mapper.deleteByLayer(workspaceId, layerId);
        for (var row : rows) {
            mapper.insert(row);
        }
    }
}
