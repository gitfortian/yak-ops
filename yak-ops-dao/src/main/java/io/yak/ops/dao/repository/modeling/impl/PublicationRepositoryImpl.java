package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.mapper.modeling.ModelPublicationMapper;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes publication statements inside the owning business transaction. */
@Repository
public class PublicationRepositoryImpl implements PublicationRepository {
    @Resource
    private ModelPublicationMapper mapper;

    @Override
    public void add(ModelPublicationEntity row) {
        mapper.insert(row);
    }

    @Override
    public Optional<ModelPublicationEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public List<ModelPublicationEntity> listByModel(String workspaceId, String modelId, int limit) {
        return mapper.listByModel(workspaceId, modelId, limit);
    }

    @Override
    public boolean finish(String workspaceId, String id, String status, String error, LocalDateTime finishedTime) {
        return mapper.finish(workspaceId, id, status, error, finishedTime) > 0;
    }

    @Override
    public boolean reconcile(String workspaceId, String id, String status, String error, LocalDateTime finishedTime) {
        return mapper.reconcile(workspaceId, id, status, error, finishedTime) > 0;
    }
}
