package io.yak.ops.business.modeling.dimension.impl;

import io.yak.ops.business.modeling.dimension.DimensionService;
import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.bean.vo.modeling.DimensionVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns concept dimension transactions, scope rules and per-scope abbreviation uniqueness. */
@Service
public class DimensionServiceImpl implements DimensionService {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    @Resource
    private DimensionRepository dimensions;

    @Resource
    private WorkspaceService workspaces;

    @Override
    @Transactional(readOnly = true)
    public List<DimensionVO> list(
            String workspaceId, String actor, DimensionScope scope, String domainId, String datamartId) {
        requireActor(workspaceId, actor);
        if (domainId != null && !dimensions.domainExists(workspaceId, domainId))
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        if (datamartId != null && !dimensions.datamartExists(workspaceId, datamartId))
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return dimensions.dimensions(workspaceId, scope, domainId, datamartId).stream()
                .map(DimensionServiceImpl::project)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionVO create(String workspaceId, String actor, DimensionInputDTO input) {
        requireActor(workspaceId, actor);
        var row = new DimensionEntity();
        row.setWorkspaceId(workspaceId);
        row.setRevision(1);
        row.initCreate(actor);
        applyOwnership(row, workspaceId, input);
        copyFields(row, input);
        requireUniqueCode(workspaceId, row.ownerScope(), row.getCode(), null);
        try {
            dimensions.add(row);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        return project(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionVO update(String workspaceId, String actor, String id, UpdateDimensionDTO input) {
        requireActor(workspaceId, actor);
        if (input == null || input.getRevision() == null || input.getRevision() < 1) throw invalid();
        var row = lockRow(workspaceId, id);
        if (row.getRevision() != input.getRevision())
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        requireSameOwnership(row, input);
        copyFields(row, input);
        requireUniqueCode(workspaceId, row.ownerScope(), row.getCode(), id);
        row.initUpdate(actor);
        try {
            if (!dimensions.update(row)) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_CODE, conflict);
        }
        row.setRevision(Math.addExact(input.getRevision(), 1));
        return project(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String actor, String id, long revision) {
        requireActor(workspaceId, actor);
        if (revision < 1) throw invalid();
        var row = lockRow(workspaceId, id);
        if (row.getRevision() != revision) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        try {
            if (!dimensions.delete(workspaceId, id, revision))
                throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        } catch (org.springframework.dao.DataIntegrityViolationException referenced) {
            throw new BusinessException(DimensionModelingErrorCode.HAS_DEPENDENCIES, referenced);
        }
    }

    /**
     * Resolves the owning scope and checks it belongs to the Workspace.
     *
     * <p>The identifier of the non-owning scope is ignored rather than rejected: a client that still
     * carries a stale value after switching scope keeps working, and the stored row can only ever
     * reference one scope thanks to the database constraint.
     */
    private void applyOwnership(DimensionEntity row, String workspaceId, DimensionInputDTO input) {
        if (input == null || input.getScope() == null)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_SCOPE);
        var scope = input.getScope();
        row.setScope(scope);
        if (scope == DimensionScope.PUBLIC) {
            var domainId = requireIdentifier(input.getDomainId());
            if (!dimensions.domainExists(workspaceId, domainId))
                throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
            row.setDomainId(domainId);
            row.setDatamartId(null);
        } else {
            var datamartId = requireIdentifier(input.getDatamartId());
            if (!dimensions.datamartExists(workspaceId, datamartId))
                throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
            row.setDatamartId(datamartId);
            row.setDomainId(null);
        }
    }

    private static void requireSameOwnership(DimensionEntity row, DimensionInputDTO input) {
        if (input.getScope() != row.getScope()) throw new BusinessException(DimensionModelingErrorCode.INVALID_SCOPE);
        String requested = input.getScope() == DimensionScope.PUBLIC ? input.getDomainId() : input.getDatamartId();
        String current = row.ownerScope();
        if (requested == null || !requested.strip().equals(current))
            throw new BusinessException(DimensionModelingErrorCode.INVALID_SCOPE);
    }

    private void requireUniqueCode(String workspaceId, String ownerScopeId, String code, String excludeId) {
        if (dimensions.countCode(workspaceId, ownerScopeId, code, excludeId) > 0)
            throw new BusinessException(DimensionModelingErrorCode.DUPLICATE_CODE);
    }

    private DimensionEntity lockRow(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw invalid();
        return dimensions
                .lock(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireActor(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }

    private static void copyFields(DimensionEntity row, DimensionInputDTO input) {
        row.setName(requireName(input.getName(), 128));
        row.setEnName(requireName(input.getEnName(), 128));
        row.setCode(requireCode(input.getCode()));
        row.setDescription(optionalDescription(input.getDescription()));
    }

    private static String requireName(String value, int max) {
        if (value == null) throw invalid();
        var stripped = value.strip();
        if (stripped.isEmpty() || stripped.length() > max) throw invalid();
        return stripped;
    }

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 64)
            throw new BusinessException(DimensionModelingErrorCode.INVALID_SCOPE);
        return value.strip();
    }

    private static String requireCode(String value) {
        if (value == null || !CODE.matcher(value).matches())
            throw new BusinessException(DimensionModelingErrorCode.INVALID_CODE);
        return value;
    }

    private static String optionalDescription(String value) {
        if (value == null || value.isBlank()) return null;
        var stripped = value.strip();
        if (stripped.length() > 500) throw invalid();
        return stripped;
    }

    private static DimensionVO project(DimensionEntity row) {
        return new DimensionVO(
                row.getId(),
                row.getScope(),
                row.getDomainId(),
                row.getDomainName(),
                row.getDatamartId(),
                row.getDatamartName(),
                row.getName(),
                row.getEnName(),
                row.getCode(),
                row.getDescription(),
                row.getRevision());
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
    }
}
