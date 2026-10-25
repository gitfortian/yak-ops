package io.yak.ops.business.modeling.standard;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Shared Workspace authorization, catalog locking and field rules for the four standard kinds.
 *
 * <p>Every kind lives under the same catalog lock and answers to the same code, name and page
 * rules, so they are defined once here instead of four times. The component owns no state and
 * joins the caller's transaction.
 */
@Service
public class StandardCatalog {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_NAME = 128;
    private static final int MAX_KEYWORD = 128;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DEPTH = 32;

    private final StandardRepository nodes;
    private final WorkspaceService workspaces;

    /**
     * Binds the catalog to its persistence and authorization boundaries.
     * @param nodes Workspace-scoped standard persistence
     * @param workspaces membership authorization
     */
    public StandardCatalog(StandardRepository nodes, WorkspaceService workspaces) {
        this.nodes = nodes;
        this.workspaces = workspaces;
    }

    /**
     * Rejects a caller who is not an active member of the Workspace.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     */
    public void authorize(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    /**
     * Authorizes the caller and takes the catalog write lock, seeding the default roots once.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     */
    public void write(String workspaceId, String actor) {
        authorize(workspaceId, actor);
        if (nodes.lockCatalog(workspaceId, actor)) return;
        for (String name : List.of("公共标准", "业务标准")) nodes.insertCategory(defaultCategory(workspaceId, actor, name));
        nodes.markInitialized(workspaceId);
    }

    /**
     * Reads one node or reports it missing.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the node
     */
    public CategoryEntity category(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw invalid();
        return nodes.category(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    /**
     * Reads every node once, for path projection and hierarchy checks.
     * @param workspaceId authorized current Workspace
     * @return nodes keyed by identity
     */
    public Map<String, CategoryEntity> categories(String workspaceId) {
        return nodes.categories(workspaceId).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
    }

    /**
     * Builds the display path of one node.
     * @param id node identity
     * @param rows every node of the Workspace
     * @return root-first names
     */
    public static List<String> path(String id, Map<String, CategoryEntity> rows) {
        List<String> names = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (id != null) {
            if (!visited.add(id) || visited.size() > MAX_DEPTH) throw hierarchy();
            var row = rows.get(id);
            if (row == null) throw hierarchy();
            names.add(row.getName());
            id = row.getParentId();
        }
        Collections.reverse(names);
        return List.copyOf(names);
    }

    /**
     * Refuses a record of the wrong kind for a standard set; a directory accepts both kinds.
     * @param node owning node
     * @param kind kind of the record about to be written
     */
    public static void requireKind(CategoryEntity node, StandardKind kind) {
        if (node.getNodeType() == StandardNodeType.STANDARD_SET && node.getStandardKind() != kind) throw invalid();
    }

    /**
     * Validates a Workspace-unique code shared by all four kinds.
     * @param value submitted code
     * @return the code unchanged
     */
    public static String validCode(String value) {
        if (value == null || !CODE.matcher(value).matches())
            throw new BusinessException(StandardErrorCode.INVALID_CODE);
        return value;
    }

    /**
     * Validates a display name.
     * @param value submitted name
     * @return the stripped name
     */
    public static String validName(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > MAX_NAME) throw invalid();
        return value.strip();
    }

    /**
     * Normalizes an optional description.
     * @param value submitted text, or null
     * @param max maximum accepted length
     * @return stripped text, or null when blank
     */
    public static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.strip();
        if (normalized.length() > max) throw invalid();
        return normalized;
    }

    /**
     * Builds the escaped {@code LIKE} pattern of a global search.
     * @param keyword submitted text, or null for no filter
     * @return escaped pattern, or null
     */
    public static String keyword(String keyword) {
        if (keyword == null) return null;
        var value = keyword.strip();
        if (value.isEmpty() || value.length() > MAX_KEYWORD) throw invalid();
        return "%"
                + value.toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_") + "%";
    }

    /**
     * Validates a one-based page request.
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the zero-based offset
     */
    public static long offset(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw invalid();
        long offset = (pageNo - 1L) * pageSize;
        if (offset > Integer.MAX_VALUE) throw invalid();
        return offset;
    }

    /**
     * Creates one of the two default root directories.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param name display name
     * @return an unsaved root node
     */
    public static CategoryEntity defaultCategory(String workspaceId, String actor, String name) {
        var node = new CategoryEntity();
        node.setWorkspaceId(workspaceId);
        node.setName(name);
        node.setNodeType(StandardNodeType.DIRECTORY);
        node.setRevision(1);
        node.initCreate(actor);
        return node;
    }

    /** @return the shared parameter failure */
    public static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }

    /** @return the shared optimistic conflict failure */
    public static BusinessException conflict() {
        return new BusinessException(StandardErrorCode.REVISION_CONFLICT);
    }

    /** @return an unusable stored hierarchy failure */
    public static IllegalStateException hierarchy() {
        return new IllegalStateException("Invalid standard category hierarchy");
    }
}
