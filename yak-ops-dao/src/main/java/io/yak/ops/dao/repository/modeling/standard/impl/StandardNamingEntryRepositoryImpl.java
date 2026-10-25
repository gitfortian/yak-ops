package io.yak.ops.dao.repository.modeling.standard.impl;

import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.mapper.modeling.standard.StandardNamingEntryMapper;
import io.yak.ops.dao.repository.modeling.standard.StandardNamingEntryRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound naming-dictionary persistence in the calling business transaction. */
@Repository
public class StandardNamingEntryRepositoryImpl implements StandardNamingEntryRepository {
    @Resource
    private StandardNamingEntryMapper mapper;

    @Override
    public List<StandardNamingEntryEntity> page(String workspaceId, StandardQuery query) {
        return mapper.page(workspaceId, query);
    }

    @Override
    public long count(String workspaceId, StandardQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<StandardNamingEntryEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public List<StandardNamingEntryEntity> entries(String workspaceId) {
        return mapper.entries(workspaceId);
    }

    @Override
    public void insert(StandardNamingEntryEntity node) {
        mapper.insert(node);
    }

    @Override
    public boolean update(StandardNamingEntryEntity node) {
        return mapper.update(node) == 1;
    }

    @Override
    public boolean delete(String workspaceId, String id, long revision) {
        return mapper.delete(workspaceId, id, revision) == 1;
    }

    @Override
    public boolean occupied(String workspaceId, String enCode, String excludeId) {
        return mapper.occupied(workspaceId, enCode, excludeId) > 0;
    }
}
