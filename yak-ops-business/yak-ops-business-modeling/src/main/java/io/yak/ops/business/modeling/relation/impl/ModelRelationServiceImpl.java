package io.yak.ops.business.modeling.relation.impl;

import io.yak.ops.business.modeling.relation.ModelRelationService;
import io.yak.ops.common.bean.dto.modeling.CreateModelRelationDTO;
import io.yak.ops.common.bean.dto.modeling.RelationKeyPairDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelGrainDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelRelationDTO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationKeyVO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationProposalVO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.RelationJoinType;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingRelationRepository;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns semantic relations between Workspace models. A relation is many-to-one, keys are ordered
 * pairs of field {@code en_code} values resolved at read time, and every key field must carry a
 * data standard: the platform's claim is that a business term resolves to one field definition,
 * and a join that ignores that claim is the failure this service exists to prevent.
 */
@Service
public class ModelRelationServiceImpl implements ModelRelationService {
    private static final int MAX_KEY_PAIRS = 8;
    private static final int MAX_PROPOSALS = 3;
    private static final String STATUS_READY = "READY";
    private static final String STATUS_KEY_UNSTANDARDIZED = "KEY_UNSTANDARDIZED";
    private static final String STATUS_FIELD_REMOVED = "FIELD_REMOVED";

    @Resource
    private ModelRepository models;

    @Resource
    private ModelingRelationRepository relations;

    @Resource
    private WorkspaceService workspaces;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ModelRelationVO> list(String workspaceId, String actor, String modelId) {
        requireActor(workspaceId, actor);
        requireModel(workspaceId, modelId);
        List<ModelingRelationEntity> rows = relations.listByModel(workspaceId, modelId);
        Map<String, List<ModelFieldEntity>> fieldCache = new HashMap<>();
        List<ModelRelationVO> result = new ArrayList<>(rows.size());
        for (ModelingRelationEntity row : rows) {
            result.add(project(workspaceId, row, fieldCache));
        }
        result.sort(Comparator.comparing(ModelRelationServiceImpl::unhealthy)
                .thenComparing(Comparator.comparing(ModelRelationVO::revision).reversed()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRelationVO create(String workspaceId, String actor, String modelId, CreateModelRelationDTO input) {
        requireActor(workspaceId, actor);
        ModelEntity from = requireModel(workspaceId, modelId);
        ModelEntity to = requireModel(workspaceId, input.getTargetModelId());
        validateDirections(from, to);
        String name = resolveName(workspaceId, input.getName(), from, to, null);
        RelationJoinType joinType = resolveJoinType(input.getJoinType());
        String relationId = IdUtils.nextId();
        List<ModelingRelationColumnEntity> columns =
                validateKeys(workspaceId, actor, from, to, input.getKeys(), relationId);
        ModelingRelationEntity row = new ModelingRelationEntity();
        row.initCreate(actor);
        row.setId(relationId);
        row.setWorkspaceId(workspaceId);
        row.setName(name);
        row.setFromModelId(from.getId());
        row.setToModelId(to.getId());
        row.setJoinType(joinType.name());
        row.setDescription(optionalText(input.getDescription()));
        row.setRevision(1L);
        try {
            relations.add(row);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_NAME, conflict);
        }
        for (ModelingRelationColumnEntity column : columns) {
            relations.addColumn(column);
        }
        return project(workspaceId, row, new HashMap<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelRelationVO update(String workspaceId, String actor, String relationId, UpdateModelRelationDTO input) {
        requireActor(workspaceId, actor);
        ModelingRelationEntity row = lock(workspaceId, relationId);
        if (row.getRevision() != input.getRevision())
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        ModelEntity from = requireModel(workspaceId, row.getFromModelId());
        ModelEntity to = requireModel(workspaceId, row.getToModelId());
        String name = trim(input.getName());
        if (name == null) throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_NAME);
        if (relations.countName(workspaceId, name, relationId) > 0)
            throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_NAME);
        RelationJoinType joinType = resolveJoinType(input.getJoinType());
        List<ModelingRelationColumnEntity> columns =
                validateKeys(workspaceId, actor, from, to, input.getKeys(), relationId);
        row.initUpdate(actor);
        row.setName(name);
        row.setJoinType(joinType.name());
        row.setDescription(optionalText(input.getDescription()));
        try {
            if (!relations.update(row)) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_NAME, conflict);
        }
        relations.deleteColumns(relationId);
        for (ModelingRelationColumnEntity column : columns) {
            relations.addColumn(column);
        }
        return project(workspaceId, relations.find(workspaceId, relationId).orElse(row), new HashMap<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String workspaceId, String actor, String relationId, long revision) {
        requireActor(workspaceId, actor);
        ModelingRelationEntity row = lock(workspaceId, relationId);
        if (row.getRevision() != revision) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        if (!relations.delete(workspaceId, relationId, revision))
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelRelationProposalVO> proposals(
            String workspaceId, String actor, String modelId, String targetModelId) {
        requireActor(workspaceId, actor);
        ModelEntity from = requireModel(workspaceId, modelId);
        ModelEntity to = requireModel(workspaceId, targetModelId);
        validateDirections(from, to);
        List<ModelFieldEntity> fromFields = boundFields(models.fields(from.getId()));
        List<ModelFieldEntity> toFields = boundFields(models.fields(to.getId()));
        List<ModelRelationProposalVO> candidates = new ArrayList<>();
        for (ModelFieldEntity fromField : fromFields) {
            for (ModelFieldEntity toField : toFields) {
                boolean sameStandard = fromField.getStandardId().equals(toField.getStandardId());
                boolean sameCode = fromField.getEnCode().equals(toField.getEnCode());
                boolean sameName = fromField.getCnName().equals(toField.getCnName());
                if (!sameStandard && !sameCode && !sameName) continue;
                boolean typesCompatible = fromField.getDataType() == toField.getDataType();
                String reason = typesCompatible ? null : "两侧字段类型不一致";
                candidates.add(new ModelRelationProposalVO(
                        fromField.getEnCode(),
                        toField.getEnCode(),
                        fromField.getStandardEnCode(),
                        toField.getStandardEnCode(),
                        sameStandard,
                        typesCompatible,
                        typesCompatible,
                        reason));
            }
        }
        candidates.sort(Comparator.comparing(ModelRelationProposalVO::sameStandard)
                .reversed()
                .thenComparing(proposal -> sameCode(proposal) ? 0 : 1)
                .thenComparing(ModelRelationProposalVO::fromFieldCode));
        if (candidates.size() > MAX_PROPOSALS) return new ArrayList<>(candidates.subList(0, MAX_PROPOSALS));
        return candidates;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGrain(String workspaceId, String actor, String modelId, UpdateModelGrainDTO input) {
        requireActor(workspaceId, actor);
        ModelEntity row = models.lock(workspaceId, modelId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        if (ModelType.from(row.getType()) == ModelType.DIM)
            throw new BusinessException(DimensionModelingErrorCode.GRAIN_NOT_APPLICABLE);
        if (row.getRevision() != input.getRevision())
            throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
        String grain =
                input.getGrainNote() == null ? null : input.getGrainNote().strip();
        if (grain != null && grain.isEmpty()) grain = null;
        if (grain != null && grain.length() > 200)
            throw new BusinessException(DimensionModelingErrorCode.GRAIN_TOO_LONG);
        row.initUpdate(actor);
        row.setGrainNote(grain);
        if (!models.update(row)) throw new BusinessException(DimensionModelingErrorCode.REVISION_CONFLICT);
    }

    private List<ModelingRelationColumnEntity> validateKeys(
            String workspaceId,
            String actor,
            ModelEntity from,
            ModelEntity to,
            List<RelationKeyPairDTO> input,
            String relationId) {
        if (input == null || input.isEmpty())
            throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_EMPTY);
        if (input.size() > MAX_KEY_PAIRS) throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_TOO_MANY);
        List<ModelFieldEntity> fromFields = models.fields(from.getId());
        List<ModelFieldEntity> toFields = models.fields(to.getId());
        Map<String, ModelFieldEntity> fromByCode = byCode(fromFields);
        Map<String, ModelFieldEntity> toByCode = byCode(toFields);
        Set<String> seenFromCodes = new HashSet<>();
        List<ModelingRelationColumnEntity> columns = new ArrayList<>(input.size());
        for (int index = 0; index < input.size(); index++) {
            RelationKeyPairDTO pair = input.get(index);
            if (pair == null) throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_EMPTY);
            String fromCode = trim(pair.getFromFieldCode());
            String toCode = trim(pair.getToFieldCode());
            if (fromCode == null || toCode == null)
                throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_EMPTY);
            if (!seenFromCodes.add(fromCode)) throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
            ModelFieldEntity fromField = fromByCode.get(fromCode);
            if (fromField == null) throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_FIELD_MISSING);
            if (fromField.getStandardId() == null)
                throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_FIELD_UNSTANDARDIZED);
            ModelFieldEntity toField = toByCode.get(toCode);
            if (toField == null) throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_FIELD_MISSING);
            if (toField.getStandardId() == null)
                throw new BusinessException(DimensionModelingErrorCode.RELATION_KEY_FIELD_UNSTANDARDIZED);
            ModelingRelationColumnEntity column = new ModelingRelationColumnEntity();
            column.initCreate(actor);
            column.setId(IdUtils.nextId());
            column.setWorkspaceId(workspaceId);
            column.setRelationId(relationId);
            column.setRevision(1L);
            column.setOrdinal(index);
            column.setFromFieldCode(fromCode);
            column.setToFieldCode(toCode);
            columns.add(column);
        }
        rejectDuplicateKeySet(workspaceId, from.getId(), to.getId(), columns, relationId);
        return columns;
    }

    private void rejectDuplicateKeySet(
            String workspaceId,
            String fromModelId,
            String toModelId,
            List<ModelingRelationColumnEntity> columns,
            String excludeRelationId) {
        String fingerprint = fingerprint(columns);
        for (ModelingRelationEntity existing : relations.listByModel(workspaceId, fromModelId)) {
            if (!fromModelId.equals(existing.getFromModelId()) || !toModelId.equals(existing.getToModelId())) continue;
            if (existing.getId().equals(excludeRelationId)) continue;
            List<ModelingRelationColumnEntity> existingColumns = relations.columns(existing.getId());
            if (fingerprint.equals(fingerprint(existingColumns)))
                throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_KEY_SET);
        }
    }

    private static String fingerprint(List<ModelingRelationColumnEntity> columns) {
        List<String> pairs = new ArrayList<>(columns.size());
        for (ModelingRelationColumnEntity column : columns) {
            pairs.add(column.getFromFieldCode() + "=" + column.getToFieldCode());
        }
        pairs.sort(Comparator.naturalOrder());
        return String.join("|", pairs);
    }

    private ModelRelationVO project(
            String workspaceId, ModelingRelationEntity row, Map<String, List<ModelFieldEntity>> fieldCache) {
        ModelEntity from = requireModel(workspaceId, row.getFromModelId());
        ModelEntity to = requireModel(workspaceId, row.getToModelId());
        List<ModelFieldEntity> fromFields = fields(workspaceId, from, fieldCache);
        List<ModelFieldEntity> toFields = fields(workspaceId, to, fieldCache);
        Map<String, ModelFieldEntity> fromByCode = byCode(fromFields);
        Map<String, ModelFieldEntity> toByCode = byCode(toFields);
        boolean fieldRemoved = false;
        boolean unstandardized = false;
        List<ModelRelationKeyVO> keys = new ArrayList<>();
        for (ModelingRelationColumnEntity column : relations.columns(row.getId())) {
            ModelFieldEntity fromField = fromByCode.get(column.getFromFieldCode());
            ModelFieldEntity toField = toByCode.get(column.getToFieldCode());
            boolean resolved = fromField != null && toField != null;
            boolean bound = resolved && fromField.getStandardId() != null && toField.getStandardId() != null;
            if (!resolved) fieldRemoved = true;
            if (resolved && !bound) unstandardized = true;
            keys.add(new ModelRelationKeyVO(
                    column.getOrdinal(),
                    column.getFromFieldCode(),
                    column.getToFieldCode(),
                    fromField == null ? null : fromField.getCnName(),
                    toField == null ? null : toField.getCnName(),
                    resolved,
                    bound));
        }
        String status = fieldRemoved ? STATUS_FIELD_REMOVED : unstandardized ? STATUS_KEY_UNSTANDARDIZED : STATUS_READY;
        return new ModelRelationVO(
                row.getId(),
                row.getName(),
                row.getFromModelId(),
                from.getCnName(),
                from.getType(),
                row.getToModelId(),
                to.getCnName(),
                to.getType(),
                row.getJoinType(),
                row.getDescription(),
                keys,
                status,
                row.getRevision());
    }

    private List<ModelFieldEntity> fields(
            String workspaceId, ModelEntity model, Map<String, List<ModelFieldEntity>> fieldCache) {
        return fieldCache.computeIfAbsent(model.getId(), key -> models.fields(key));
    }

    private void validateDirections(ModelEntity from, ModelEntity to) {
        if (from.getId().equals(to.getId())) throw new BusinessException(DimensionModelingErrorCode.RELATION_SELF);
        if (ModelType.from(from.getType()) == ModelType.DIM)
            throw new BusinessException(DimensionModelingErrorCode.RELATION_FROM_IS_DIM);
        if (ModelType.from(to.getType()) != ModelType.DIM)
            throw new BusinessException(DimensionModelingErrorCode.RELATION_TARGET_NOT_DIM);
    }

    private String resolveName(
            String workspaceId, String requested, ModelEntity from, ModelEntity to, String excludeId) {
        String base = trim(requested);
        if (base != null) {
            if (relations.countName(workspaceId, base, excludeId) > 0)
                throw new BusinessException(DimensionModelingErrorCode.RELATION_DUPLICATE_NAME);
            return base;
        }
        base = from.getName() + "_to_" + to.getName();
        if (base.length() > 64) base = base.substring(0, 64);
        String candidate = base;
        int suffix = 2;
        while (relations.countName(workspaceId, candidate, excludeId) > 0) {
            String marker = "-" + suffix;
            candidate = base.length() + marker.length() > 64
                    ? base.substring(0, 64 - marker.length()) + marker
                    : base + marker;
            suffix++;
        }
        return candidate;
    }

    private static RelationJoinType resolveJoinType(String input) {
        if (input == null || input.isBlank()) return RelationJoinType.LEFT;
        try {
            return RelationJoinType.valueOf(input.strip());
        } catch (IllegalArgumentException unknown) {
            throw new BusinessException(DimensionModelingErrorCode.INVALID_FIELD);
        }
    }

    private static boolean unhealthy(ModelRelationVO relation) {
        return !STATUS_READY.equals(relation.status());
    }

    private static boolean sameCode(ModelRelationProposalVO proposal) {
        return proposal.fromFieldCode().equals(proposal.toFieldCode());
    }

    private static Map<String, ModelFieldEntity> byCode(List<ModelFieldEntity> fields) {
        Map<String, ModelFieldEntity> byCode = new LinkedHashMap<>();
        for (ModelFieldEntity field : fields) {
            byCode.put(field.getEnCode(), field);
        }
        return byCode;
    }

    private static List<ModelFieldEntity> boundFields(List<ModelFieldEntity> fields) {
        List<ModelFieldEntity> bound = new ArrayList<>();
        for (ModelFieldEntity field : fields) {
            if (field.getStandardId() != null) bound.add(field);
        }
        return bound;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String optionalText(String value) {
        String normalized = trim(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private ModelEntity requireModel(String workspaceId, String modelId) {
        if (modelId == null || modelId.isBlank()) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return models.find(workspaceId, modelId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private ModelingRelationEntity lock(String workspaceId, String relationId) {
        if (relationId == null || relationId.isBlank())
            throw new BusinessException(DimensionModelingErrorCode.RELATION_NOT_FOUND);
        return relations
                .lock(workspaceId, relationId)
                .orElseThrow(() -> new BusinessException(DimensionModelingErrorCode.RELATION_NOT_FOUND));
    }

    private void requireActor(String workspaceId, String actor) {
        if (actor == null || actor.isBlank() || !workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(io.yak.ops.workspace.enums.WorkspaceErrorCode.ACCESS_DENIED);
    }
}
