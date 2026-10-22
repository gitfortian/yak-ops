package io.yak.ops.business.modeling.impl;

import io.yak.ops.business.modeling.PlanningService;
import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningNodeVO;
import io.yak.ops.common.bean.vo.modeling.SavedNodeVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns planning transactions, parent-before-child locking and live member authorization. */
@Service
public class PlanningServiceImpl implements PlanningService {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    @Resource
    private PlanningRepository nodes;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private UserRepository users;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PlanningNodeVO> tree(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        var rows = nodes.tree(workspaceId);
        var members = members(workspaceId, actor);
        var ownerIds =
                rows.stream().map(PlanningNodeEntity::getOwnerId).distinct().toList();
        Map<String, UserEntity> people = ownerIds.isEmpty()
                ? Map.of()
                : users.queryByIds(ownerIds).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<String, List<PlanningNodeEntity>> children = new HashMap<>();
        for (var row : rows) {
            if (row.getKind() != NodeKind.CATEGORY) {
                children.computeIfAbsent(
                                key(row.getKind().parentKind(), row.getParentId()), ignored -> new ArrayList<>())
                        .add(row);
            }
        }
        return rows.stream()
                .filter(row -> row.getKind() == NodeKind.CATEGORY)
                .map(row -> project(row, children, people, members))
                .toList();
    }

    private PlanningNodeVO project(
            PlanningNodeEntity row,
            Map<String, List<PlanningNodeEntity>> children,
            Map<String, UserEntity> people,
            Set<String> members) {
        var owner = people.get(row.getOwnerId());
        boolean active = owner != null && owner.getStatus() == UserStatus.ACTIVE && members.contains(row.getOwnerId());
        var nested = children.getOrDefault(key(row.getKind(), row.getId()), List.of()).stream()
                .map(child -> project(child, children, people, members))
                .toList();
        return new PlanningNodeVO(
                row.getId(),
                row.getKind(),
                row.getParentId(),
                row.getName(),
                row.getCode(),
                row.getOwnerId(),
                owner == null ? null : displayName(owner),
                active,
                row.getDescription(),
                row.getRevision(),
                nested);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerOptionVO> owners(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        var ids = List.copyOf(members(workspaceId, actor));
        if (ids.isEmpty()) return List.of();
        return users.queryByIds(ids).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new OwnerOptionVO(user.getId(), displayName(user)))
                .sorted(java.util.Comparator.comparing(OwnerOptionVO::displayName)
                        .thenComparing(OwnerOptionVO::userId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean available(String workspaceId, String actor, String code, NodeKind kind, String id) {
        requireActor(workspaceId, actor);
        if ((kind == null) != (id == null)) throw invalid();
        if (id != null) requireNode(workspaceId, kind, id);
        validateCode(code);
        return !nodes.occupied(code, kind, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO create(String workspaceId, String actor, NodeKind kind, String parentId, NodeInputDTO input) {
        requireActor(workspaceId, actor);
        if (kind == null || (kind == NodeKind.CATEGORY) != (parentId == null)) throw invalid();
        if (parentId != null) lockPath(workspaceId, kind.parentKind(), parentId);
        validateInput(workspaceId, input);
        var node = new PlanningNodeEntity();
        node.setWorkspaceId(workspaceId);
        node.setKind(kind);
        node.setParentId(parentId);
        copyInput(node, input);
        node.setRevision(1);
        node.initCreate(actor);
        try {
            nodes.add(node);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        return new SavedNodeVO(node.getId(), kind, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO update(String workspaceId, String actor, NodeKind kind, String id, UpdateNodeDTO input) {
        requireActor(workspaceId, actor);
        var node = lockPath(workspaceId, kind, id);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        requireRevision(node, input.getRevision());
        validateInput(workspaceId, input);
        boolean codeChanged = !input.getCode().equals(node.getCode());
        copyInput(node, input);
        node.initUpdate(actor);
        try {
            if (!nodes.update(node, input.getRevision(), codeChanged))
                throw new BusinessException(ModelingErrorCode.REVISION_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        return new SavedNodeVO(id, kind, Math.addExact(input.getRevision(), 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String actor, NodeKind kind, String id, long revision) {
        requireActor(workspaceId, actor);
        if (revision < 1) throw invalid();
        var node = lockPath(workspaceId, kind, id);
        requireRevision(node, revision);
        if (nodes.hasChildren(workspaceId, kind, id)) throw new BusinessException(ModelingErrorCode.HAS_DEPENDENCIES);
        try {
            if (!nodes.delete(workspaceId, kind, id, revision))
                throw new BusinessException(ModelingErrorCode.REVISION_CONFLICT);
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessException(ModelingErrorCode.HAS_DEPENDENCIES, conflict);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AssociatedModelsVO associatedModels(String workspaceId, String actor, String processId) {
        requireActor(workspaceId, actor);
        requireNode(workspaceId, NodeKind.PROCESS, processId);
        return AssociatedModelsVO.notEnabled();
    }

    private PlanningNodeEntity lockPath(String workspaceId, NodeKind kind, String id) {
        var observed = requireNode(workspaceId, kind, id);
        if (kind.parentKind() != null) lockPath(workspaceId, kind.parentKind(), observed.getParentId());
        return nodes.lock(workspaceId, kind, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private PlanningNodeEntity requireNode(String workspaceId, NodeKind kind, String id) {
        if (kind == null || id == null || id.isBlank()) throw invalid();
        return nodes.find(workspaceId, kind, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireActor(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    private Set<String> members(String workspaceId, String actor) {
        return workspaces.queryWorkspaceMembers(workspaceId, actor).stream()
                .map(io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO::getUserId)
                .collect(Collectors.toSet());
    }

    private void validateInput(String workspaceId, NodeInputDTO input) {
        if (input == null
                || input.getName() == null
                || input.getName().strip().isEmpty()
                || input.getName().strip().length() > 128
                || input.getOwnerId() == null
                || input.getOwnerId().isBlank()
                || input.getOwnerId().length() > 64
                || (input.getDescription() != null && input.getDescription().length() > 500)) throw invalid();
        validateCode(input.getCode());
        if (!workspaces.isActiveMember(workspaceId, input.getOwnerId()))
            throw new BusinessException(ModelingErrorCode.INVALID_OWNER);
    }

    private static void validateCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) throw new BusinessException(ModelingErrorCode.INVALID_CODE);
    }

    private static void copyInput(PlanningNodeEntity node, NodeInputDTO input) {
        node.setName(input.getName().strip());
        node.setCode(input.getCode());
        node.setOwnerId(input.getOwnerId());
        node.setDescription(
                input.getDescription() == null || input.getDescription().isBlank()
                        ? null
                        : input.getDescription().strip());
    }

    private static void requireRevision(PlanningNodeEntity node, long revision) {
        if (node.getRevision() != revision) throw new BusinessException(ModelingErrorCode.REVISION_CONFLICT);
    }

    private static String displayName(UserEntity user) {
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUserName() : user.getRealName();
    }

    private static String key(NodeKind kind, String id) {
        return kind.name() + ":" + id;
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }
}
