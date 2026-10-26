package io.yak.ops.business.modeling.metric.impl;

import io.yak.ops.business.modeling.metric.MetricAtomicService;
import io.yak.ops.business.modeling.metric.MetricCatalog;
import io.yak.ops.business.modeling.metric.MetricCompositionSync;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricAtomicVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardMeasureUnitRepository;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns atomic-metric writes and their caliber constraints.
 *
 * <p>The aggregate is part of the definition, so its compatibility with the logical type is the
 * same matrix the model-field surface applies: a metric that could not seed any legal field cannot
 * be saved. An unknown process or unit fails with the callers' shared resource error, like any
 * reference into another module.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MetricAtomicServiceImpl implements MetricAtomicService {
    @Resource
    private MetricAtomicRepository atomics;

    @Resource
    private MetricReferenceRepository references;

    @Resource
    private PlanningRepository planning;

    @Resource
    private StandardMeasureUnitRepository units;

    @Resource
    private MetricCatalog catalog;

    @Resource
    private MetricCompositionSync composition;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<MetricAtomicVO> page(
            String workspaceId, String actor, String processId, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = MetricCatalog.offset(pageNo, pageSize);
        var query = new MetricQuery(trim(processId), null, MetricCatalog.keyword(keyword), offset, pageSize);
        long total = atomics.count(workspaceId, query);
        return PageData.of(
                atomics.page(workspaceId, query).stream()
                        .map(MetricAtomicServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricAtomicVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(require(workspaceId, id));
    }

    @Override
    public MetricSavedVO create(String workspaceId, String actor, MetricAtomicInputDTO input) {
        catalog.authorize(workspaceId, actor);
        var ownerId = catalog.owner(workspaceId, actor, input == null ? null : input.getOwnerId());
        validate(workspaceId, input);
        var row = new MetricAtomicEntity();
        row.setWorkspaceId(workspaceId);
        copy(row, input);
        row.setOwnerId(ownerId);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            atomics.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(row.getId(), 1);
    }

    @Override
    public MetricSavedVO update(String workspaceId, String actor, String id, MetricAtomicUpdateDTO input) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw MetricCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        var ownerId = catalog.owner(workspaceId, actor, input.getOwnerId());
        validate(workspaceId, input);
        var previousCode = row.getEnCode();
        var previousName = row.getCnName();
        copy(row, input);
        row.setOwnerId(ownerId);
        row.initUpdate(actor);
        try {
            if (!atomics.update(row)) throw MetricCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        if (!row.getEnCode().equals(previousCode) || !row.getCnName().equals(previousName))
            composition.partEdited(
                    workspaceId, actor, MetricCompositionSync.Part.ATOMIC, id, row.getEnCode(), row.getCnName());
        return new MetricSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!atomics.delete(workspaceId, id, revision)) throw MetricCatalog.conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(MetricErrorCode.REFERENCED, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        catalog.authorize(workspaceId, actor);
        if (excludeId != null) require(workspaceId, excludeId);
        MetricCatalog.validCode(enCode);
        return !atomics.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return MetricReferencesVO.of(references.byAtomic(workspaceId, id));
    }

    private MetricAtomicEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw MetricCatalog.invalid();
        return atomics.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void validate(String workspaceId, MetricAtomicInputDTO input) {
        if (input == null) throw MetricCatalog.invalid();
        var processId = trim(input.getProcessId());
        if (processId == null) throw MetricCatalog.invalid();
        planning.find(workspaceId, NodeKind.PROCESS, processId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        MetricCatalog.validCode(input.getEnCode());
        MetricCatalog.validName(input.getCnName());
        if (input.getDataType() == null || input.getAggregateType() == null) throw MetricCatalog.invalid();
        if (!input.getAggregateType().supports(input.getDataType()))
            throw new BusinessException(MetricErrorCode.INVALID_ATOMIC_METRIC);
        var unitId = trim(input.getMeasureUnitId());
        if (unitId != null && units.find(workspaceId, unitId).isEmpty())
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        if (input.getDescription() != null && input.getDescription().strip().length() > 500)
            throw MetricCatalog.invalid();
    }

    private static void copy(MetricAtomicEntity row, MetricAtomicInputDTO input) {
        row.setProcessId(input.getProcessId().strip());
        row.setEnCode(input.getEnCode());
        row.setCnName(input.getCnName().strip());
        row.setDataType(input.getDataType());
        row.setAggregateType(input.getAggregateType());
        row.setMeasureUnitId(trim(input.getMeasureUnitId()));
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
    }

    private static MetricAtomicVO project(MetricAtomicEntity row) {
        return new MetricAtomicVO(
                row.getId(),
                row.getProcessId(),
                row.getProcessName(),
                row.getEnCode(),
                row.getCnName(),
                row.getDataType(),
                row.getAggregateType(),
                row.getMeasureUnitId(),
                row.getMeasureUnitEnCode(),
                row.getOwnerId(),
                row.getDescription(),
                row.getUpdateTime(),
                row.getRevision());
    }

    private static String trim(String value) {
        if (value == null) return null;
        var normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw MetricCatalog.invalid();
        if (current != expected) throw MetricCatalog.conflict();
    }
}
