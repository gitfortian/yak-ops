package io.yak.ops.dao.repository.modeling.impl;

import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.mapper.modeling.PlanningMapper;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Executes planning writes inside the owning business transaction. */
@Repository
public class PlanningRepositoryImpl implements PlanningRepository {
    @Resource
    private PlanningMapper mapper;

    @Override
    public List<PlanningNodeEntity> tree(String workspaceId) {
        return mapper.tree(workspaceId);
    }

    @Override
    public Optional<PlanningNodeEntity> find(String workspaceId, NodeKind kind, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, kind, id, false));
    }

    @Override
    public Optional<PlanningNodeEntity> lock(String workspaceId, NodeKind kind, String id) {
        return Optional.ofNullable(mapper.find(workspaceId, kind, id, true));
    }

    @Override
    public void add(PlanningNodeEntity node) {
        mapper.reserve(node);
        mapper.insert(node.getKind(), node);
    }

    @Override
    public boolean update(PlanningNodeEntity node, long revision, boolean codeChanged) {
        if (mapper.update(node.getKind(), node, revision) != 1) return false;
        if (codeChanged && mapper.changeCode(node) != 1)
            throw new IllegalStateException("Planning code reservation is missing");
        return true;
    }

    @Override
    public boolean delete(String workspaceId, NodeKind kind, String id, long revision) {
        if (mapper.delete(workspaceId, kind, id, revision) != 1) return false;
        if (mapper.release(workspaceId, kind, id) != 1)
            throw new IllegalStateException("Planning code reservation is missing");
        return true;
    }

    @Override
    public boolean hasChildren(String workspaceId, NodeKind kind, String id) {
        return mapper.child(workspaceId, kind, id) != null;
    }

    @Override
    public boolean occupied(String code, NodeKind kind, String id) {
        return mapper.occupied(code, kind == null ? "" : kind.name(), id == null ? "" : id) > 0;
    }
}
