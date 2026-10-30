package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.mapper.modeling.ModelPublicationMapper;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Publication storage.
 *
 * <p>These writes run outside a business transaction on purpose: the {@code PENDING} row must be
 * committed before the engine is contacted, otherwise a crash would leave no trace of an attempt
 * that may already have created a table.
 */
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
    public boolean finish(
            String workspaceId,
            String id,
            String status,
            String error,
            Long durationMs,
            String executionLog,
            LocalDateTime finishedTime) {
        return mapper.finish(workspaceId, id, status, error, durationMs, executionLog, finishedTime) > 0;
    }

    @Override
    public boolean reconcile(String workspaceId, String id, String status, String error, LocalDateTime finishedTime) {
        return mapper.reconcile(workspaceId, id, status, error, finishedTime) > 0;
    }

    @Override
    public Map<String, String> latestStatusByModel(String workspaceId, Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) return Map.of();
        var statuses = new LinkedHashMap<String, String>();
        // Rows arrive newest first, so the first status seen for a model is the one to keep.
        for (var row : mapper.latestStatusByModel(workspaceId, modelIds)) {
            if (row.getModelId() != null && row.getStatus() != null) {
                statuses.putIfAbsent(row.getModelId(), row.getStatus());
            }
        }
        return Map.copyOf(statuses);
    }
}
