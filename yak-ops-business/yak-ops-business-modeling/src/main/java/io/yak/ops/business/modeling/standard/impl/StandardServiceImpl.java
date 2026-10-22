package io.yak.ops.business.modeling.standard.impl;

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
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.standard.FieldQuery;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern LENGTH = Pattern.compile("[1-9][0-9]{0,9}");
    private static final Pattern DECIMAL = Pattern.compile("([1-9][0-9]?),([0-9]{1,2})");

    @Resource
    private StandardRepository nodes;

    @Resource
    private WorkspaceService workspaces;

    @Resource
    private UserRepository users;

    @Override
    public List<StandardCategoryVO> initialize(String workspaceId, String actor) {
        writeCatalog(workspaceId, actor);
        return categoryTree(nodes.categories(workspaceId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<StandardCategoryVO> tree(String workspaceId, String actor) {
        authorize(workspaceId, actor);
        return categoryTree(nodes.categories(workspaceId));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OwnerOptionVO> owners(String workspaceId, String actor) {
        authorize(workspaceId, actor);
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
        writeCatalog(workspaceId, actor);
        if (input == null) throw invalid();
        if (input.getParentId() != null) {
            var parent = category(workspaceId, input.getParentId());
            if (path(parent.getId(), categoryMap(nodes.categories(workspaceId))).size() >= 32)
                throw new BusinessException(StandardErrorCode.DEPTH_LIMIT);
        }
        var node = newCategory(workspaceId, actor, input.getParentId(), validName(input.getName()));
        nodes.insertCategory(node);
        return saved(node.getId(), 1);
    }

    @Override
    public StandardSavedVO renameCategory(String workspaceId, String actor, String id, CategoryUpdateDTO input) {
        writeCatalog(workspaceId, actor);
        var node = category(workspaceId, id);
        if (input == null) throw invalid();
        checkRevision(node.getRevision(), input.getRevision());
        node.setName(validName(input.getName()));
        node.initUpdate(actor);
        if (!nodes.updateCategory(node)) throw conflict();
        return saved(id, Math.addExact(node.getRevision(), 1));
    }

    @Override
    public void deleteCategory(String workspaceId, String actor, String id, long revision) {
        writeCatalog(workspaceId, actor);
        var node = category(workspaceId, id);
        checkRevision(node.getRevision(), revision);
        var rows = nodes.categories(workspaceId);
        Set<String> descendants = new HashSet<>();
        collectDescendants(id, rows, descendants);
        if (descendants.size() > 1 || nodes.hasFields(workspaceId, id))
            throw new BusinessException(StandardErrorCode.HAS_DEPENDENCIES);
        try {
            if (!nodes.deleteCategory(workspaceId, id, revision)) throw conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(StandardErrorCode.HAS_DEPENDENCIES, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<StandardFieldVO> page(
            String workspaceId, String actor, String categoryId, String keyword, int pageNo, int pageSize) {
        authorize(workspaceId, actor);
        if ((categoryId == null) == (keyword == null) || pageNo < 1 || pageSize < 1 || pageSize > 100) throw invalid();
        if (categoryId != null) category(workspaceId, categoryId);
        String escaped = null;
        if (keyword != null) {
            String value = keyword.strip();
            if (value.isEmpty() || value.length() > 128) throw invalid();
            escaped = "%"
                    + value.toLowerCase(Locale.ROOT)
                            .replace("!", "!!")
                            .replace("%", "!%")
                            .replace("_", "!_") + "%";
        }
        var query = new FieldQuery(categoryId, escaped, (pageNo - 1L) * pageSize, pageSize);
        long total = nodes.countFields(workspaceId, query);
        var rows = nodes.fields(workspaceId, query);
        return PageData.of(project(workspaceId, actor, rows), total, pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardFieldVO field(String workspaceId, String actor, String id) {
        authorize(workspaceId, actor);
        return project(workspaceId, actor, List.of(requireField(workspaceId, id)))
                .getFirst();
    }

    @Override
    public StandardSavedVO createField(String workspaceId, String actor, String categoryId, StandardInputDTO input) {
        writeCatalog(workspaceId, actor);
        category(workspaceId, categoryId);
        validate(workspaceId, input);
        var node = new StandardFieldEntity();
        node.setWorkspaceId(workspaceId);
        node.setCategoryId(categoryId);
        copy(node, input);
        node.setRevision(1);
        node.initCreate(actor);
        try {
            nodes.insertField(node);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return saved(node.getId(), 1);
    }

    @Override
    public StandardSavedVO updateField(String workspaceId, String actor, String id, StandardUpdateDTO input) {
        writeCatalog(workspaceId, actor);
        var node = requireField(workspaceId, id);
        if (input == null) throw invalid();
        checkRevision(node.getRevision(), input.getRevision());
        validate(workspaceId, input);
        copy(node, input);
        node.initUpdate(actor);
        try {
            if (!nodes.updateField(node)) throw conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(StandardErrorCode.DUPLICATE_CODE, failure);
        }
        return saved(id, Math.addExact(node.getRevision(), 1));
    }

    @Override
    public void deleteField(String workspaceId, String actor, String id, long revision) {
        writeCatalog(workspaceId, actor);
        var node = requireField(workspaceId, id);
        checkRevision(node.getRevision(), revision);
        try {
            if (!nodes.deleteField(workspaceId, id, revision)) throw conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(StandardErrorCode.REFERENCED, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        authorize(workspaceId, actor);
        if (excludeId != null) requireField(workspaceId, excludeId);
        validCode(enCode);
        return !nodes.occupied(enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StandardReferencesVO references(String workspaceId, String actor, String id) {
        authorize(workspaceId, actor);
        requireField(workspaceId, id);
        return StandardReferencesVO.notEnabled();
    }

    private void writeCatalog(String workspaceId, String actor) {
        authorize(workspaceId, actor);
        if (!nodes.lockCatalog(workspaceId, actor)) {
            for (String name : List.of("公共标准", "业务标准"))
                nodes.insertCategory(newCategory(workspaceId, actor, null, name));
            nodes.markInitialized(workspaceId);
        }
    }

    private static CategoryEntity newCategory(String workspaceId, String actor, String parentId, String name) {
        var node = new CategoryEntity();
        node.setWorkspaceId(workspaceId);
        node.setParentId(parentId);
        node.setName(name);
        node.setRevision(1);
        node.initCreate(actor);
        return node;
    }

    private void authorize(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    private CategoryEntity category(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw invalid();
        return nodes.category(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private StandardFieldEntity requireField(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw invalid();
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
        var categories = categoryMap(nodes.categories(workspaceId));
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
                            String.join(" / ", path(row.getCategoryId(), categories)),
                            row.getCnName(),
                            row.getEnCode(),
                            row.getDataType(),
                            row.getLengthPrecision(),
                            row.getIsNotNull(),
                            row.getSecurityLevel(),
                            row.getOwnerId(),
                            owner == null ? null : displayName(owner),
                            owner != null && owner.getStatus() == UserStatus.ACTIVE && members.contains(owner.getId()),
                            row.getBusinessCaliber(),
                            row.getUpdateTime(),
                            row.getRevision());
                })
                .toList();
    }

    private static Map<String, CategoryEntity> categoryMap(List<CategoryEntity> rows) {
        return rows.stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
    }

    private static List<String> path(String id, Map<String, CategoryEntity> rows) {
        List<String> names = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (id != null) {
            if (!visited.add(id) || visited.size() > 32) throw hierarchy();
            var row = rows.get(id);
            if (row == null) throw hierarchy();
            names.add(row.getName());
            id = row.getParentId();
        }
        Collections.reverse(names);
        return names;
    }

    private static List<StandardCategoryVO> categoryTree(List<CategoryEntity> rows) {
        Map<String, List<CategoryEntity>> children = new HashMap<>();
        for (var row : rows)
            children.computeIfAbsent(row.getParentId(), ignored -> new ArrayList<>())
                    .add(row);
        Set<String> visited = new HashSet<>();
        var roots = branch(null, children, visited, 1);
        if (visited.size() != rows.size()) throw hierarchy();
        return roots;
    }

    private static List<StandardCategoryVO> branch(
            String parent, Map<String, List<CategoryEntity>> children, Set<String> visited, int depth) {
        return children.getOrDefault(parent, List.of()).stream()
                .map(row -> {
                    if (depth > 32 || !visited.add(row.getId())) throw hierarchy();
                    return new StandardCategoryVO(
                            row.getId(),
                            row.getParentId(),
                            row.getName(),
                            row.getRevision(),
                            branch(row.getId(), children, visited, depth + 1));
                })
                .toList();
    }

    private static void collectDescendants(String id, List<CategoryEntity> rows, Set<String> visited) {
        if (!visited.add(id)) throw hierarchy();
        for (var row : rows) if (id.equals(row.getParentId())) collectDescendants(row.getId(), rows, visited);
    }

    private void validate(String workspaceId, StandardInputDTO input) {
        if (input == null
                || input.getDataType() == null
                || input.getSecurityLevel() == null
                || input.getIsNotNull() == null) throw invalid();
        validName(input.getCnName());
        validCode(input.getEnCode());
        if (input.getOwnerId() == null
                || input.getOwnerId().length() > 64
                || !workspaces.isActiveMember(workspaceId, input.getOwnerId()))
            throw new BusinessException(StandardErrorCode.INVALID_OWNER);
        if (input.getBusinessCaliber() != null && input.getBusinessCaliber().length() > 2000) throw invalid();
        String value = input.getLengthPrecision();
        if (value != null && value.isBlank()) {
            value = null;
            input.setLengthPrecision(null);
        }
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
    }

    private static void copy(StandardFieldEntity row, StandardInputDTO input) {
        row.setCnName(input.getCnName().strip());
        row.setEnCode(input.getEnCode());
        row.setDataType(input.getDataType());
        row.setLengthPrecision(input.getLengthPrecision());
        row.setIsNotNull(input.getIsNotNull());
        row.setSecurityLevel(input.getSecurityLevel());
        row.setOwnerId(input.getOwnerId());
        row.setBusinessCaliber(
                input.getBusinessCaliber() == null || input.getBusinessCaliber().isBlank()
                        ? null
                        : input.getBusinessCaliber().strip());
    }

    private static String validName(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > 128) throw invalid();
        return value.strip();
    }

    private static void validCode(String value) {
        if (value == null || !CODE.matcher(value).matches())
            throw new BusinessException(StandardErrorCode.INVALID_CODE);
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw invalid();
        if (current != expected) throw conflict();
    }

    private static StandardSavedVO saved(String id, long revision) {
        return new StandardSavedVO(id, revision);
    }

    private static String displayName(UserEntity user) {
        return user.getRealName() == null || user.getRealName().isBlank() ? user.getUserName() : user.getRealName();
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }

    private static BusinessException conflict() {
        return new BusinessException(StandardErrorCode.REVISION_CONFLICT);
    }

    private static IllegalStateException hierarchy() {
        return new IllegalStateException("Invalid standard category hierarchy");
    }
}
