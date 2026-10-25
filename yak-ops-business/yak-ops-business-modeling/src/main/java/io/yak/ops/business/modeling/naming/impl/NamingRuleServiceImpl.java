package io.yak.ops.business.modeling.naming.impl;

import io.yak.ops.business.modeling.naming.NamingRuleMatcher;
import io.yak.ops.business.modeling.naming.NamingRuleService;
import io.yak.ops.common.bean.dto.modeling.NamingRuleUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.LayerNamingRuleVO;
import io.yak.ops.common.bean.vo.modeling.NamingViolationVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.ModelNamingRow;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardNamingEntryRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Applies the layer naming rule and keeps the recorded root word truthful. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class NamingRuleServiceImpl implements NamingRuleService {
    @Resource
    private LayerRepository layers;

    @Resource
    private ModelRepository models;

    @Resource
    private StandardNamingEntryRepository entries;

    @Resource
    private WorkspaceService workspaces;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LayerNamingRuleVO rule(String workspaceId, String actor, String layerId) {
        requireActor(workspaceId, actor);
        var layer = require(workspaceId, layerId);
        var dictionary = entries.entries(workspaceId);
        var violations = models.namingRows(workspaceId, layerId).stream()
                .filter(row -> resolve(dictionary, row, layer).isEmpty())
                .map(row -> new NamingViolationVO(row.id(), row.name(), row.cnName(), row.type(), row.processCode()))
                .toList();
        return new LayerNamingRuleVO(layer.getId(), layer.isNamingRuleEnabled(), layer.getRevision(), violations);
    }

    @Override
    public LayerNamingRuleVO update(String workspaceId, String actor, String layerId, NamingRuleUpdateDTO input) {
        requireActor(workspaceId, actor);
        var layer = require(workspaceId, layerId);
        if (input == null || input.getRevision() == null || input.getRevision() < 1)
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        if (layer.getRevision() != input.getRevision()) throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        boolean enabled = Boolean.TRUE.equals(input.getEnabled());
        if (enabled) enable(workspaceId, layer, input.isAcknowledgeViolations());
        if (!layers.updateNamingRule(workspaceId, layerId, enabled, input.getRevision(), actor))
            throw new BusinessException(LayerErrorCode.REVISION_CONFLICT);
        return rule(workspaceId, actor, layerId);
    }

    @Override
    public String rootWord(String workspaceId, String suffix, String processCode) {
        return resolve(entries.entries(workspaceId), suffix, processCode)
                .orElseThrow(() -> new BusinessException(DimensionModelingErrorCode.NAMING_RULE_VIOLATION))
                .getId();
    }

    /**
     * Validates and applies enablement.
     *
     * <p>The fit check uses the shortest dictionary entry: with the root word free per model, a
     * process that cannot fit even the shortest head could never be named at all, so the layer must
     * not switch to a state it cannot satisfy. Conforming models then record their matched entry.
     */
    private void enable(String workspaceId, LayerEntity layer, boolean acknowledged) {
        var dictionary = entries.entries(workspaceId);
        var rows = models.namingRows(workspaceId, layer.getId());
        var shortest = dictionary.stream()
                .map(StandardNamingEntryEntity::getEnCode)
                .min(Comparator.comparingInt(String::length))
                .orElseThrow(() -> new BusinessException(LayerErrorCode.INVALID_NAMING_RULE));
        for (var row : rows)
            if (row.processCode() != null && !NamingRuleMatcher.fits(shortest, row.processCode()))
                throw new BusinessException(LayerErrorCode.INVALID_NAMING_RULE);
        if (rows.stream().anyMatch(row -> resolve(dictionary, row, layer).isEmpty()) && !acknowledged)
            throw new BusinessException(LayerErrorCode.INVALID_NAMING_RULE);
        for (var row : rows)
            resolve(dictionary, row, layer)
                    .filter(entry -> !entry.getId().equals(row.namingRootWordId()))
                    .ifPresent(entry -> models.backfillNamingRoot(workspaceId, row.id(), entry.getId()));
    }

    private static Optional<StandardNamingEntryEntity> resolve(
            List<StandardNamingEntryEntity> dictionary, ModelNamingRow row, LayerEntity layer) {
        return resolve(dictionary, suffix(row.name(), layer.getEnCode()), row.processCode());
    }

    private static Optional<StandardNamingEntryEntity> resolve(
            List<StandardNamingEntryEntity> dictionary, String suffix, String processCode) {
        var roots =
                dictionary.stream().map(StandardNamingEntryEntity::getEnCode).toList();
        return NamingRuleMatcher.match(suffix, processCode, roots)
                .flatMap(root -> dictionary.stream()
                        .filter(entry -> entry.getEnCode().equals(root))
                        .findFirst());
    }

    private static String suffix(String name, String layerCode) {
        var prefix = layerCode.toLowerCase(Locale.ROOT) + "_";
        return name != null && name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    private LayerEntity require(String workspaceId, String layerId) {
        if (layerId == null || layerId.isBlank()) throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        return layers.layer(workspaceId, layerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void requireActor(String workspaceId, String actor) {
        if (!workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(WorkspaceErrorCode.ACCESS_DENIED);
    }
}
