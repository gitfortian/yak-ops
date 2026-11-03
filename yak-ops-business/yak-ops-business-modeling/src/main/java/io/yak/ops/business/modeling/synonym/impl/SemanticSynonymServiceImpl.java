package io.yak.ops.business.modeling.synonym.impl;

import io.yak.ops.business.modeling.synonym.SemanticSynonymService;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.SemanticSynonymTargetType;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingSynonymRepository;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces synonym sets per target inside one transaction. Model-field synonyms key on
 * {@code modelId:fieldCode} because field row ids are replaced on every model save; the composite
 * key is internal and never leaves the service.
 */
@Service
public class SemanticSynonymServiceImpl implements SemanticSynonymService {
    private static final int MAX_TERMS = 20;
    private static final int MAX_TERM_LENGTH = 64;
    private static final String MODEL_FIELD_TYPE = SemanticSynonymTargetType.MODEL_FIELD.name();

    @Resource
    private ModelRepository models;

    @Resource
    private ModelingSynonymRepository synonyms;

    @Resource
    private WorkspaceService workspaces;

    @Override
    @Transactional(readOnly = true)
    public List<String> listForModelField(String workspaceId, String actor, String modelId, String fieldCode) {
        requireActor(workspaceId, actor);
        requireField(workspaceId, modelId, fieldCode);
        return terms(synonyms.listByTarget(workspaceId, MODEL_FIELD_TYPE, fieldKey(modelId, fieldCode)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceForModelField(
            String workspaceId, String actor, String modelId, String fieldCode, List<String> terms) {
        requireActor(workspaceId, actor);
        requireField(workspaceId, modelId, fieldCode);
        List<String> normalized = normalize(terms);
        replaceInternal(workspaceId, actor, MODEL_FIELD_TYPE, fieldKey(modelId, fieldCode), normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> list(String workspaceId, String actor, SemanticSynonymTargetType targetType, String targetId) {
        requireActor(workspaceId, actor);
        requireTarget(workspaceId, targetType, targetId);
        return terms(synonyms.listByTarget(workspaceId, targetType.name(), targetId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replace(
            String workspaceId,
            String actor,
            SemanticSynonymTargetType targetType,
            String targetId,
            List<String> terms) {
        requireActor(workspaceId, actor);
        requireTarget(workspaceId, targetType, targetId);
        List<String> normalized = normalize(terms);
        replaceInternal(workspaceId, actor, targetType.name(), targetId, normalized);
    }

    private void replaceInternal(
            String workspaceId, String actor, String targetType, String targetId, List<String> terms) {
        synonyms.deleteByTarget(workspaceId, targetType, targetId);
        for (String term : terms) {
            ModelingSynonymEntity row = new ModelingSynonymEntity();
            row.initCreate(actor);
            row.setId(IdUtils.nextId());
            row.setWorkspaceId(workspaceId);
            row.setTargetType(targetType);
            row.setTargetId(targetId);
            row.setTerm(term);
            row.setRevision(1L);
            synonyms.add(row);
        }
    }

    private static List<String> terms(List<ModelingSynonymEntity> rows) {
        List<String> terms = new ArrayList<>(rows.size());
        for (ModelingSynonymEntity row : rows) {
            terms.add(row.getTerm());
        }
        return terms;
    }

    private static List<String> normalize(List<String> input) {
        if (input == null || input.isEmpty()) return List.of();
        if (input.size() > MAX_TERMS) throw new BusinessException(DimensionModelingErrorCode.SYNONYM_INVALID);
        List<String> terms = new ArrayList<>(input.size());
        Set<String> seen = new HashSet<>();
        for (String raw : input) {
            String term = raw == null ? null : raw.strip();
            // The column collation is case-insensitive; rejecting here keeps the failure a 400
            // instead of a unique-key violation surfaced as a 500.
            String key = term == null ? null : term.toLowerCase(Locale.ROOT);
            if (term == null || term.isEmpty() || term.length() > MAX_TERM_LENGTH || !seen.add(key))
                throw new BusinessException(DimensionModelingErrorCode.SYNONYM_INVALID);
            terms.add(term);
        }
        return terms;
    }

    private void requireTarget(String workspaceId, SemanticSynonymTargetType targetType, String targetId) {
        if (targetType == SemanticSynonymTargetType.MODEL_FIELD)
            throw new BusinessException(DimensionModelingErrorCode.SYNONYM_TARGET_INVALID);
        if (targetId == null || targetId.isBlank()) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        if (targetType == SemanticSynonymTargetType.STANDARD_FIELD && models.standard(workspaceId, targetId) == null)
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        // METRIC targets are not existence-checked here: metrics span three tables and the binding
        // the synonyms annotate is advisory. A wrong id simply carries no synonyms.
    }

    private void requireField(String workspaceId, String modelId, String fieldCode) {
        if (modelId == null || modelId.isBlank() || fieldCode == null || fieldCode.isBlank())
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        if (models.find(workspaceId, modelId).isEmpty())
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        for (ModelFieldEntity field : models.fields(modelId)) {
            if (fieldCode.equals(field.getEnCode())) return;
        }
        throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
    }

    private static String fieldKey(String modelId, String fieldCode) {
        return modelId + ":" + fieldCode;
    }

    private void requireActor(String workspaceId, String actor) {
        if (actor == null || actor.isBlank() || !workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(io.yak.ops.workspace.enums.WorkspaceErrorCode.ACCESS_DENIED);
    }
}
