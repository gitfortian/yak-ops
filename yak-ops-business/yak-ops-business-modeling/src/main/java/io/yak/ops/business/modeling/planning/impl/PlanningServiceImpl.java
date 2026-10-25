package io.yak.ops.business.modeling.planning.impl;

import io.yak.ops.business.modeling.planning.PlanningService;
import io.yak.ops.common.bean.dto.modeling.DatamartInputDTO;
import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.SubjectInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDatamartDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateSubjectDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.LogicalModelSummaryVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningNodeVO;
import io.yak.ops.common.bean.vo.modeling.PlanningTreeVO;
import io.yak.ops.common.bean.vo.modeling.SavedNodeVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
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
    private ModelRepository models;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private UserRepository users;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanningTreeVO tree(String workspaceId, String actor) {
        requireActor(workspaceId, actor);
        var rows = nodes.tree(workspaceId);
        var members = members(workspaceId, actor);
        var ownerIds = rows.stream()
                .map(PlanningNodeEntity::getOwnerId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, UserEntity> people = ownerIds.isEmpty()
                ? Map.of()
                : users.queryByIds(ownerIds).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Map<String, List<PlanningNodeEntity>> children = new HashMap<>();
        for (var row : rows) {
            NodeKind parentKind = row.getKind().parentKind();
            if (parentKind != null) {
                children.computeIfAbsent(key(parentKind, row.getParentId()), ignored -> new ArrayList<>())
                        .add(row);
            }
        }
        return new PlanningTreeVO(
                roots(rows, NodeKind.CATEGORY).stream()
                        .map(row -> project(row, children, people, members))
                        .toList(),
                roots(rows, NodeKind.DATASET).stream()
                        .map(row -> project(row, children, people, members))
                        .toList());
    }

    private PlanningNodeVO project(
            PlanningNodeEntity row,
            Map<String, List<PlanningNodeEntity>> children,
            Map<String, UserEntity> people,
            Set<String> members) {
        var owner = row.getOwnerId() == null ? null : people.get(row.getOwnerId());
        boolean active = owner != null && owner.getStatus() == UserStatus.ACTIVE && members.contains(row.getOwnerId());
        var nested = children.getOrDefault(key(row.getKind(), row.getId()), List.of()).stream()
                .map(child -> project(child, children, people, members))
                .toList();
        return new PlanningNodeVO(
                row.getId(),
                row.getKind(),
                row.getParentId(),
                row.getName(),
                row.getEnName(),
                row.getCode(),
                row.getOwnerId(),
                owner == null ? null : displayName(owner),
                active,
                row.getDescription(),
                row.getRevision(),
                nested);
    }

    private static List<PlanningNodeEntity> roots(List<PlanningNodeEntity> rows, NodeKind kind) {
        return rows.stream().filter(row -> row.getKind() == kind).toList();
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
        return insert(workspaceId, actor, kind, parentId, fields(input));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO update(String workspaceId, String actor, NodeKind kind, String id, UpdateNodeDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        return replace(workspaceId, actor, kind, id, input.getRevision(), fields(input));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO createDatamart(String workspaceId, String actor, DatamartInputDTO input) {
        requireActor(workspaceId, actor);
        return insert(workspaceId, actor, NodeKind.DATASET, null, fields(input));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO updateDatamart(String workspaceId, String actor, String id, UpdateDatamartDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        return replace(workspaceId, actor, NodeKind.DATASET, id, input.getRevision(), fields(input));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO createSubject(String workspaceId, String actor, String datamartId, SubjectInputDTO input) {
        requireActor(workspaceId, actor);
        return insert(workspaceId, actor, NodeKind.SUBJECT, datamartId, fields(input));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SavedNodeVO updateSubject(String workspaceId, String actor, String id, UpdateSubjectDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        return replace(workspaceId, actor, NodeKind.SUBJECT, id, input.getRevision(), fields(input));
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
    public AssociatedModelsVO associatedModels(
            String workspaceId, String actor, String processId, int pageNo, int pageSize) {
        requireActor(workspaceId, actor);
        requireNode(workspaceId, NodeKind.PROCESS, processId);
        long offset = (long) (pageNo - 1) * pageSize;
        long total = models.countByProcess(workspaceId, processId);
        List<ModelEntity> rows = total == 0 || offset > Integer.MAX_VALUE
                ? List.of()
                : models.pageByProcess(workspaceId, processId, pageSize, (int) offset);
        var page = PageData.of(
                rows.stream()
                        .map(row -> new LogicalModelSummaryVO(row.getId(), row.getCnName(), row.getName()))
                        .toList(),
                total,
                pageNo,
                pageSize);
        return AssociatedModelsVO.available(PagingData.from(page));
    }

    /** Inserts one node and its code reservation after locking every ancestor. */
    private SavedNodeVO insert(String workspaceId, String actor, NodeKind kind, String parentId, NodeFields fields) {
        if (kind == null || (kind.parentKind() == null) != (parentId == null)) throw invalid();
        if (parentId != null) lockPath(workspaceId, kind.parentKind(), parentId);
        validateInput(workspaceId, kind, fields);
        var node = new PlanningNodeEntity();
        node.setWorkspaceId(workspaceId);
        node.setKind(kind);
        node.setParentId(parentId);
        copyFields(node, fields);
        node.setRevision(1);
        node.initCreate(actor);
        try {
            nodes.add(node);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        return new SavedNodeVO(node.getId(), kind, 1);
    }

    /** Replaces editable fields and the code of an already locked node under its revision guard. */
    private SavedNodeVO replace(
            String workspaceId, String actor, NodeKind kind, String id, long revision, NodeFields fields) {
        var node = lockPath(workspaceId, kind, id);
        requireRevision(node, revision);
        validateInput(workspaceId, kind, fields);
        boolean codeChanged = !fields.code().equals(node.getCode());
        copyFields(node, fields);
        node.initUpdate(actor);
        try {
            if (!nodes.update(node, revision, codeChanged))
                throw new BusinessException(ModelingErrorCode.REVISION_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(ModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        return new SavedNodeVO(id, kind, Math.addExact(revision, 1));
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

    private void validateInput(String workspaceId, NodeKind kind, NodeFields fields) {
        if (fields.name() == null
                || fields.name().strip().isEmpty()
                || fields.name().strip().length() > 128) throw invalid();
        if (kind.hasEnglishName()) {
            if (fields.enName() == null
                    || fields.enName().strip().isEmpty()
                    || fields.enName().strip().length() > 128) throw invalid();
        }
        if (fields.description() != null && fields.description().length() > 500) throw invalid();
        validateCode(fields.code());
        if (!kind.hasOwner()) return;
        var ownerId = fields.ownerId();
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 64) throw invalid();
        if (!workspaces.isActiveMember(workspaceId, ownerId))
            throw new BusinessException(ModelingErrorCode.INVALID_OWNER);
    }

    private static void validateCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) throw new BusinessException(ModelingErrorCode.INVALID_CODE);
    }

    private static void copyFields(PlanningNodeEntity node, NodeFields fields) {
        node.setName(fields.name().strip());
        node.setEnName(
                fields.enName() == null || fields.enName().isBlank()
                        ? null
                        : fields.enName().strip());
        node.setCode(fields.code());
        node.setOwnerId(fields.ownerId());
        node.setDescription(
                fields.description() == null || fields.description().isBlank()
                        ? null
                        : fields.description().strip());
    }

    private static void requireRevision(PlanningNodeEntity node, long revision) {
        if (node.getRevision() != revision) throw new BusinessException(ModelingErrorCode.REVISION_CONFLICT);
    }

    private static NodeFields fields(NodeInputDTO input) {
        if (input == null) throw invalid();
        return new NodeFields(input.getName(), null, input.getCode(), input.getOwnerId(), input.getDescription());
    }

    private static NodeFields fields(DatamartInputDTO input) {
        if (input == null) throw invalid();
        return new NodeFields(
                input.getName(), input.getEnName(), input.getCode(), input.getOwnerId(), input.getDescription());
    }

    private static NodeFields fields(SubjectInputDTO input) {
        if (input == null) throw invalid();
        return new NodeFields(input.getName(), input.getEnName(), input.getCode(), null, input.getDescription());
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

    /** Editable planning fields after DTO-specific projection; owner is null for subjects. */
    private record NodeFields(String name, String enName, String code, String ownerId, String description) {}
}
