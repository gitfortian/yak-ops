package io.yak.ops.business.modeling.standard.impl;

import io.yak.ops.business.modeling.standard.StandardCatalog;
import io.yak.ops.business.modeling.standard.StandardService;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardCategoryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardFieldVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.standard.FieldQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardLookupRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

/** Owns short catalog-serialized writes and consistent Workspace-scoped reads. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class StandardServiceImpl implements StandardService {
    private static final Pattern LENGTH = Pattern.compile("[1-9][0-9]{0,9}");
    private static final Pattern DECIMAL = Pattern.compile("([1-9][0-9]?),([0-9]{1,2})");
    private static final int MAX_PARENT_LEVELS = 8;

    @Resource
    private StandardRepository nodes;

    @Resource
    private StandardLookupRepository lookups;

    @Resource
    private StandardMeasureUnitRepository units;

    @Resource
    private StandardReferenceRepository references;

    @Resource
    private StandardCatalog catalog;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private UserRepository users;

    @Override
    public List<StandardCategoryVO> initialize(String workspaceId, String actor) {
        catalog.write(workspaceId, actor);
        return categoryTree(nodes.categories(workspaceId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<StandardCategoryVO> tree(String workspaceId, String actor) {
        catalog.authorize(workspaceId, actor);
        return categoryTree(nodes.categories(workspaceId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OwnerOptionVO> owners(String workspaceId, String actor) {
        catalog.authorize(workspaceId, actor);
        var ids = List.copyOf(memberIds(workspaceId, actor));
        if (ids.isEmpty()) return List.of();
        return users.queryByIds(ids).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new OwnerOptionVO(user.getId(), displayName(user)))
                .sorted(java.util.Comparator.comparing(OwnerOptionVO::displayName)
                        .thenComparing(OwnerOptionVO::userId))
                .toList();
    }

    @Override
    public StandardSavedVO createCategory(String workspaceId, String actor, CategoryInputDTO input) {
        catalog.write(workspaceId, actor);
        if (input == null) throw StandardCatalog.invalid();
        var nodeType = input.getNodeType() == null ? StandardNodeType.DIRECTORY : input.getNodeType();
        if (nodeType == StandardNodeType.STANDARD_SET) {
            if (input.getStandardKind() == null) throw StandardCatalog.invalid();
        } else if (input.getStandardKind() != null) {
            throw StandardCatalog.invalid();
        }
        if (input.getParentId() != null) {
            var parent = catalog.category(workspaceId, input.getParentId());
            // A standard set holds records only, so it can never be somebody's parent.
            if (parent.getNodeType() == StandardNodeType.STANDARD_SET) throw StandardCatalog.invalid();
            if (StandardCatalog.path(parent.getId(), catalog.categories(workspaceId))
                            .size()
                    >= 32) throw new BusinessException(StandardErrorCode.DEPTH_LIMIT);
        }
        var node = newCategory(
                workspaceId, actor, input.getParentId(), StandardCatalog.validName(input.getName()), nodeType);
        node.setStandardKind(input.getStandardKind());
        nodes.insertCategory(node);
        return saved(node.getId(), 1);
    }

    @Override
    public StandardSavedVO renameCategory(String workspaceId, String actor, String id, CategoryUpdateDTO input) {
        catalog.write(workspaceId, actor);
        var node = catalog.category(workspaceId, id);
        if (input == null) throw StandardCatalog.invalid();
        checkRevision(node.getRevision(), input.getRevision());
        if (input.getStandardKind() != null) {
            if (node.getNodeType() != StandardNodeType.STANDARD_SET) throw StandardCatalog.invalid();
            if (node.getStandardKind() != input.getStandardKind()) {
                // The lock is derived from the record count, so an emptied set becomes switchable again.
                if (nodes.hasRecords(workspaceId, id))
                    throw new BusinessException(StandardErrorCode.STANDARD_SET_KIND_LOCKED);
                node.setStandardKind(input.getStandardKind());
            }
        }
        node.setName(StandardCatalog.validName(input.getName()));
        node.initUpdate(actor);
        if (!nodes.updateCategory(node)) throw StandardCatalog.conflict();
        return saved(id, Math.addExact(node.getRevision(), 1));
    }

    @Override
    public void deleteCategory(String workspaceId, String actor, String id, long revision) {
        catalog.write(workspaceId, actor);
        var node = catalog.category(workspaceId, id);
        checkRevision(node.getRevision(), revision);
        var rows = nodes.categories(workspaceId);
        Set<String> descendants = new HashSet<>();
        collectDescendants(id, rows, descendants);
        if (descendants.size() > 1 || nodes.hasRecords(workspaceId, id))
            throw new BusinessException(StandardErrorCode.HAS_DEPENDENCIES);
        try {
            if (!nodes.deleteCategory(workspaceId, id, revision)) throw StandardCatalog.conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(StandardErrorCode.HAS_DEPENDENCIES, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<StandardFieldVO> page(
            String workspaceId, String actor, String categoryId, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        if ((categoryId == null) == (keyword == null)) throw StandardCatalog.invalid();
        long offset = StandardCatalog.offset(pageNo, pageSize);
        if (categoryId != null) catalog.category(workspaceId, categoryId);
        var query = new FieldQuery(categoryId, StandardCatalog.keyword(keyword), offset, pageSize);
        long total = nodes.countFields(workspaceId, query);
        var rows = nodes.fields(workspaceId, query);
        return PageData.of(project(workspaceId, actor, rows), total, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardFieldVO field(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(workspaceId, actor, List.of(requireField(workspaceId, id)))
                .getFirst();
    }

    @Override
    public StandardSavedVO createField(String workspaceId, String actor, String categoryId, StandardInputDTO input) {
        catalog.write(workspaceId, actor);
        var node = catalog.category(workspaceId, categoryId);
        StandardCatalog.requireKind(node, StandardKind.FIELD);
        validate(workspaceId, null, input);
        var row = new StandardFieldEntity();
        row.setWorkspaceId(workspaceId);
        row.setCategoryId(categoryId);
        copy(row, input);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            nodes.insertField(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return saved(row.getId(), 1);
    }

    @Override
    public StandardSavedVO updateField(String workspaceId, String actor, String id, StandardUpdateDTO input) {
        catalog.write(workspaceId, actor);
        var node = requireField(workspaceId, id);
        if (input == null) throw StandardCatalog.invalid();
        checkRevision(node.getRevision(), input.getRevision());
        StandardCatalog.requireKind(catalog.category(workspaceId, node.getCategoryId()), StandardKind.FIELD);
        validate(workspaceId, id, input);
        copy(node, input);
        node.initUpdate(actor);
        try {
            if (!nodes.updateField(node)) throw StandardCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return saved(id, Math.addExact(node.getRevision(), 1));
    }

    @Override
    public void deleteField(String workspaceId, String actor, String id, long revision) {
        catalog.write(workspaceId, actor);
        var node = requireField(workspaceId, id);
        checkRevision(node.getRevision(), revision);
        try {
            if (!nodes.deleteField(workspaceId, id, revision)) throw StandardCatalog.conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(StandardErrorCode.REFERENCED, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        catalog.authorize(workspaceId, actor);
        if (excludeId != null) requireField(workspaceId, excludeId);
        StandardCatalog.validCode(enCode);
        return !nodes.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        requireField(workspaceId, id);
        return StandardReferencesVO.of(references.byStandard(workspaceId, id));
    }

    private static CategoryEntity newCategory(
            String workspaceId, String actor, String parentId, String name, StandardNodeType nodeType) {
        var node = new CategoryEntity();
        node.setWorkspaceId(workspaceId);
        node.setParentId(parentId);
        node.setName(name);
        node.setNodeType(nodeType);
        node.setRevision(1);
        node.initCreate(actor);
        return node;
    }

    private StandardFieldEntity requireField(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw StandardCatalog.invalid();
        return nodes.field(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private Set<String> memberIds(String workspaceId, String actor) {
        return workspaces.queryWorkspaceMembers(workspaceId, actor).stream()
                .map(member -> member.getUserId())
                .collect(Collectors.toSet());
    }

    private List<StandardFieldVO> project(String workspaceId, String actor, List<StandardFieldEntity> rows) {
        if (rows.isEmpty()) return List.of();
        var categories = catalog.categories(workspaceId);
        var ownerIds =
                rows.stream().map(StandardFieldEntity::getOwnerId).distinct().toList();
        var people =
                users.queryByIds(ownerIds).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        var members = memberIds(workspaceId, actor);
        return rows.stream()
                .map(row -> {
                    var owner = people.get(row.getOwnerId());
                    return new StandardFieldVO(
                            row.getId(),
                            row.getCategoryId(),
                            String.join(" / ", StandardCatalog.path(row.getCategoryId(), categories)),
                            row.getCnName(),
                            row.getEnCode(),
                            row.getDataType(),
                            row.getLengthPrecision(),
                            Boolean.TRUE.equals(row.getIsNotNull()),
                            row.getSecurityLevel(),
                            row.getOwnerId(),
                            owner == null ? null : displayName(owner),
                            owner != null && owner.getStatus() == UserStatus.ACTIVE && members.contains(owner.getId()),
                            row.getBusinessCaliber(),
                            row.getDefaultValue(),
                            row.getParentStandardId(),
                            row.getParentStandardEnCode(),
                            row.getLookupId(),
                            row.getLookupEnCode(),
                            row.getLookupCnName(),
                            row.getMeasureUnitId(),
                            row.getMeasureUnitEnCode(),
                            row.getMeasureUnitCnName(),
                            row.getUpdateTime(),
                            row.getRevision());
                })
                .toList();
    }

    private static List<StandardCategoryVO> categoryTree(List<CategoryEntity> rows) {
        Map<String, List<CategoryEntity>> children = new HashMap<>();
        for (var row : rows)
            children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>())
                    .add(row);
        Set<String> visited = new HashSet<>();
        var roots = branch(null, children, visited, 1);
        if (visited.size() != rows.size()) throw StandardCatalog.hierarchy();
        return roots;
    }

    private static List<StandardCategoryVO> branch(
            String parent, Map<String, List<CategoryEntity>> children, Set<String> visited, int depth) {
        return children.getOrDefault(parent, List.of()).stream()
                .map(row -> {
                    if (depth > 32 || !visited.add(row.getId())) throw StandardCatalog.hierarchy();
                    return new StandardCategoryVO(
                            row.getId(),
                            row.getParentId(),
                            row.getName(),
                            row.getNodeType() == null ? StandardNodeType.DIRECTORY : row.getNodeType(),
                            row.getStandardKind(),
                            row.getRecordCount(),
                            row.getRevision(),
                            branch(row.getId(), children, visited, depth + 1));
                })
                .toList();
    }

    private static void collectDescendants(String id, List<CategoryEntity> rows, Set<String> visited) {
        if (!visited.add(id)) throw StandardCatalog.hierarchy();
        for (var row : rows) if (id.equals(row.getParentId())) collectDescendants(row.getId(), rows, visited);
    }

    private void validate(String workspaceId, String id, StandardInputDTO input) {
        if (input == null
                || input.getDataType() == null
                || input.getSecurityLevel() == null
                || input.getIsNotNull() == null) throw StandardCatalog.invalid();
        StandardCatalog.validName(input.getCnName());
        StandardCatalog.validCode(input.getEnCode());
        if (input.getOwnerId() == null
                || input.getOwnerId().length() > 64
                || !workspaces.isActiveMember(workspaceId, input.getOwnerId()))
            throw new BusinessException(StandardErrorCode.INVALID_OWNER);
        if (input.getBusinessCaliber() != null && input.getBusinessCaliber().length() > 2000)
            throw StandardCatalog.invalid();
        String value = input.getLengthPrecision();
        if (value != null && value.isBlank()) {
            value = null;
            input.setLengthPrecision(null);
        }
        // A standard is a contract the publisher has to honour: PhysicalTypes refuses a DECIMAL
        // without a complete precision pair, so a standard short of one could be designed and never
        // published.
        if (input.getDataType() == DataType.DECIMAL && value == null)
            throw new BusinessException(StandardErrorCode.INVALID_PRECISION);
        if (value != null) {
            boolean valid = false;
            if (input.getDataType() == DataType.STRING)
                valid = LENGTH.matcher(value).matches() && Long.parseLong(value) <= 2147483647L;
            if (input.getDataType() == DataType.DECIMAL) {
                var match = DECIMAL.matcher(value);
                valid = match.matches()
                        && Integer.parseInt(match.group(1)) <= 38
                        && Integer.parseInt(match.group(2)) <= Integer.parseInt(match.group(1));
            }
            if (!valid) throw new BusinessException(StandardErrorCode.INVALID_PRECISION);
        }
        if (input.getDefaultValue() != null && input.getDefaultValue().length() > 2048) throw StandardCatalog.invalid();
        validateParent(workspaceId, id, trim(input.getParentStandardId()));
        var lookupId = trim(input.getLookupId());
        if (lookupId != null && lookups.find(workspaceId, lookupId).isEmpty())
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        var unitId = trim(input.getMeasureUnitId());
        if (unitId != null && units.find(workspaceId, unitId).isEmpty())
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
    }

    /**
     * Rejects a parent that is the record itself, outside the Workspace, cyclic or too deep.
     *
     * <p>The chain is at most eight levels including the record being written, so the parent's own
     * chain may hold seven.
     */
    private void validateParent(String workspaceId, String id, String parentId) {
        if (parentId == null) return;
        if (parentId.equals(id)) throw invalidParent();
        var parent = nodes.field(workspaceId, parentId).orElseThrow(StandardServiceImpl::invalidParent);
        int level = 1;
        var cursor = parent.getParentStandardId();
        Set<String> seen = new HashSet<>();
        seen.add(parent.getId());
        while (cursor != null) {
            if (cursor.equals(id) || !seen.add(cursor)) throw invalidParent();
            if (++level >= MAX_PARENT_LEVELS) throw invalidParent();
            var row = nodes.field(workspaceId, cursor).orElseThrow(StandardServiceImpl::invalidParent);
            cursor = row.getParentStandardId();
        }
    }

    private static BusinessException invalidParent() {
        return new BusinessException(StandardErrorCode.INVALID_PARENT_STANDARD);
    }

    private static void copy(StandardFieldEntity row, StandardInputDTO input) {
        row.setCnName(input.getCnName().strip());
        row.setEnCode(input.getEnCode());
        row.setDataType(input.getDataType());
        row.setLengthPrecision(input.getLengthPrecision());
        row.setIsNotNull(input.getIsNotNull());
        row.setSecurityLevel(input.getSecurityLevel());
        row.setOwnerId(input.getOwnerId());
        row.setBusinessCaliber(StandardCatalog.optionalText(input.getBusinessCaliber(), 2000));
        row.setDefaultValue(StandardCatalog.optionalText(input.getDefaultValue(), 2048));
        row.setParentStandardId(trim(input.getParentStandardId()));
        row.setLookupId(trim(input.getLookupId()));
        row.setMeasureUnitId(trim(input.getMeasureUnitId()));
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw StandardCatalog.invalid();
        if (current != expected) throw StandardCatalog.conflict();
    }

    private static StandardSavedVO saved(String id, long revision) {
        return new StandardSavedVO(id, revision);
    }

    private static String displayName(UserEntity user) {
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUserName() : user.getRealName();
    }

    private static String trim(String value) {
        if (value == null) return null;
        var normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
