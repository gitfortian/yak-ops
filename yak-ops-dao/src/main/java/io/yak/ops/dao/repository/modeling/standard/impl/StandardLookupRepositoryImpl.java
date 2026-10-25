package io.yak.ops.dao.repository.modeling.standard.impl;

import io.yak.ops.dao.entity.modeling.standard.StandardLookupEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupValueEntity;
import io.yak.ops.dao.mapper.modeling.standard.StandardLookupMapper;
import io.yak.ops.dao.repository.modeling.standard.StandardLookupRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardQuery;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes bound lookup persistence in the calling business transaction. */
@Repository
public class StandardLookupRepositoryImpl implements StandardLookupRepository {
    @Resource
    private StandardLookupMapper mapper;

    @Override
    public List<StandardLookupEntity> page(String workspaceId, StandardQuery query) {
        return mapper.page(workspaceId, query);
    }

    @Override
    public long count(String workspaceId, StandardQuery query) {
        return mapper.count(workspaceId, query);
    }

    @Override
    public Optional<StandardLookupEntity> find(String workspaceId, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, id));
    }

    @Override
    public List<StandardLookupValueEntity> values(String lookupId) {
        return mapper.values(lookupId);
    }

    @Override
    public void insert(StandardLookupEntity node) {
        mapper.insert(node);
    }

    @Override
    public boolean update(StandardLookupEntity node) {
        return mapper.update(node) == 1;
    }

    @Override
    public void replaceValues(String lookupId, List<StandardLookupValueEntity> rows) {
        mapper.deleteValues(lookupId);
        if (!rows.isEmpty()) mapper.insertValues(rows);
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
