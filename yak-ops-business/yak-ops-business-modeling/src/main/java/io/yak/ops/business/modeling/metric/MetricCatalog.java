package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Shared Workspace authorization and field rules for the five metric kinds.
 *
 * <p>Every kind answers to the same code, name and page rules, so they are defined once here
 * instead of five times. Metrics have no container tree, so authorization is plain membership and
 * writes take no catalog lock. The component owns no state and joins the caller's transaction.
 */
@Service
public class MetricCatalog {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_NAME = 128;
    private static final int MAX_KEYWORD = 128;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkspaceService workspaces;

    /**
     * Binds the catalog to its authorization boundary.
     * @param workspaces membership authorization
     */
    public MetricCatalog(WorkspaceService workspaces) {
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
     * Resolves the accountable owner of a metric record, defaulting to the caller.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param ownerId submitted owner, or null to use the caller
     * @return an active member of the Workspace
     */
    public String owner(String workspaceId, String actor, String ownerId) {
        var owner = ownerId == null || ownerId.isBlank() ? actor : ownerId.strip();
        if (owner == null || owner.isBlank() || owner.length() > 64 || !workspaces.isActiveMember(workspaceId, owner))
            throw new BusinessException(MetricErrorCode.INVALID_OWNER);
        return owner;
    }

    /**
     * Validates a Workspace-unique code shared by all five kinds.
     * @param value submitted code
     * @return the code unchanged
     */
    public static String validCode(String value) {
        if (value == null || !CODE.matcher(value).matches())
            throw new BusinessException(MetricErrorCode.INVALID_CODE);
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

    /** @return the shared parameter failure */
    public static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }

    /** @return the shared optimistic conflict failure */
    public static BusinessException conflict() {
        return new BusinessException(MetricErrorCode.REVISION_CONFLICT);
    }
}
