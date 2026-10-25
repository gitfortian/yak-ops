package io.yak.ops.business.modeling.metric.impl;

import io.yak.ops.business.modeling.metric.DerivedComposition;
import io.yak.ops.business.modeling.metric.MetricCatalog;
import io.yak.ops.business.modeling.metric.MetricDerivedService;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricDerivedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedModifierEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricModifierRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricTimePeriodRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns derived-metric writes; the composed code and name are generated from the parts.
 *
 * <p>A derived metric has no name of its own: the parts are the only editable input, the server
 * composes and stores both values, and identical parts always collide instead of duplicating a
 * caliber under two names. Editing the parts recomposes and re-checks; stored fields that already
 * imported the metric are never rewritten.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MetricDerivedServiceImpl implements MetricDerivedService {
    @Resource
    private MetricDerivedRepository derived;

    @Resource
    private MetricAtomicRepository atomics;

    @Resource
    private MetricTimePeriodRepository periods;

    @Resource
    private MetricModifierRepository modifiers;

    @Resource
    private MetricReferenceRepository references;

    @Resource
    private MetricCatalog catalog;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<MetricDerivedVO> page(
            String workspaceId, String actor, String processId, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = MetricCatalog.offset(pageNo, pageSize);
        var query = new MetricQuery(trim(processId), null, MetricCatalog.keyword(keyword), offset, pageSize);
        long total = derived.count(workspaceId, query);
        return PageData.of(
                derived.page(workspaceId, query).stream()
                        .map(MetricDerivedServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricDerivedVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(require(workspaceId, id));
    }

    @Override
    public MetricSavedVO create(String workspaceId, String actor, MetricDerivedInputDTO input) {
        catalog.authorize(workspaceId, actor);
        var parts = resolveParts(workspaceId, input);
        var ownerId = catalog.owner(workspaceId, actor, input == null ? null : input.getOwnerId());
        var row = new MetricDerivedEntity();
        row.setWorkspaceId(workspaceId);
        row.setEnCode(parts.composed().enCode());
        row.setCnName(parts.composed().cnName());
        row.setAtomicMetricId(parts.atomic().getId());
        row.setPeriodId(parts.period().getId());
        row.setOwnerId(ownerId);
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
        row.setRevision(1);
        row.initCreate(actor);
        row.setModifiers(parts.links());
        try {
            derived.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(row.getId(), 1);
    }

    @Override
    public MetricSavedVO update(String workspaceId, String actor, String id, MetricDerivedUpdateDTO input) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw MetricCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        var parts = resolveParts(workspaceId, input);
        var ownerId = catalog.owner(workspaceId, actor, input.getOwnerId());
        row.setEnCode(parts.composed().enCode());
        row.setCnName(parts.composed().cnName());
        row.setAtomicMetricId(parts.atomic().getId());
        row.setPeriodId(parts.period().getId());
        row.setOwnerId(ownerId);
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
        row.initUpdate(actor);
        row.setModifiers(parts.links());
        try {
            if (!derived.update(row)) throw MetricCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!derived.delete(workspaceId, id, revision)) throw MetricCatalog.conflict();
        } catch (DataIntegrityViolationException failure) {
            throw new BusinessException(MetricErrorCode.REFERENCED, failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(
            String workspaceId,
            String actor,
            String atomicMetricId,
            String periodId,
            List<String> modifierIds,
            String excludeId) {
        catalog.authorize(workspaceId, actor);
        if (excludeId != null) require(workspaceId, excludeId);
        var parts = resolveParts(workspaceId, partInput(atomicMetricId, periodId, modifierIds));
        if (excludeId == null)
            return !derived.occupied(workspaceId, parts.composed().enCode(), null);
        return !derived.occupied(workspaceId, parts.composed().enCode(), excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return MetricReferencesVO.of(references.byDerived(workspaceId, id));
    }

    private record Parts(
            MetricAtomicEntity atomic,
            MetricTimePeriodEntity period,
            List<MetricDerivedModifierEntity> links,
            DerivedComposition.Composed composed) {}

    private Parts resolveParts(String workspaceId, MetricDerivedInputDTO input) {
        if (input == null) throw MetricCatalog.invalid();
        var atomicId = trim(input.getAtomicMetricId());
        var periodId = trim(input.getPeriodId());
        if (atomicId == null || periodId == null) throw MetricCatalog.invalid();
        var atomic = atomics.find(workspaceId, atomicId)
                .orElseThrow(() -> new BusinessException(MetricErrorCode.INVALID_DERIVED_METRIC));
        var period = periods.find(workspaceId, periodId)
                .orElseThrow(() -> new BusinessException(MetricErrorCode.INVALID_DERIVED_METRIC));
        var submitted = input.getModifierIds() == null ? List.<String>of() : input.getModifierIds();
        var seen = new HashSet<String>();
        var links = new ArrayList<MetricDerivedModifierEntity>();
        var modifierCodes = new ArrayList<String>();
        var modifierNames = new ArrayList<String>();
        for (var raw : submitted) {
            var modifierId = trim(raw);
            if (modifierId == null || !seen.add(modifierId))
                throw new BusinessException(MetricErrorCode.INVALID_DERIVED_METRIC);
            var modifier = requireModifier(workspaceId, modifierId);
            links.add(link(modifierId));
            modifierCodes.add(modifier.getEnCode());
            modifierNames.add(modifier.getCnName());
        }
        var composed = DerivedComposition.compose(
                period.getEnCode(),
                modifierCodes,
                atomic.getEnCode(),
                period.getCnName(),
                modifierNames,
                atomic.getCnName());
        if (!DerivedComposition.fits(composed)) throw new BusinessException(MetricErrorCode.INVALID_DERIVED_METRIC);
        return new Parts(atomic, period, List.copyOf(links), composed);
    }

    private MetricModifierEntity requireModifier(String workspaceId, String modifierId) {
        return modifiers
                .find(workspaceId, modifierId)
                .orElseThrow(() -> new BusinessException(MetricErrorCode.INVALID_DERIVED_METRIC));
    }

    private MetricDerivedEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw MetricCatalog.invalid();
        return derived.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private MetricDerivedInputDTO partInput(String atomicMetricId, String periodId, List<String> modifierIds) {
        var input = new MetricDerivedInputDTO();
        input.setAtomicMetricId(atomicMetricId);
        input.setPeriodId(periodId);
        input.setModifierIds(modifierIds);
        return input;
    }

    private static MetricDerivedModifierEntity link(String modifierId) {
        var link = new MetricDerivedModifierEntity();
        link.setModifierId(modifierId);
        return link;
    }

    private static MetricDerivedVO project(MetricDerivedEntity row) {
        return new MetricDerivedVO(
                row.getId(),
                row.getEnCode(),
                row.getCnName(),
                row.getAtomicMetricId(),
                row.getAtomicEnCode(),
                row.getAtomicCnName(),
                row.getPeriodId(),
                row.getPeriodEnCode(),
                row.getPeriodCnName(),
                row.getModifiers() == null
                        ? List.of()
                        : row.getModifiers().stream()
                                .map(part -> new MetricDerivedVO.Part(
                                        part.getModifierId(), part.getModifierEnCode(), part.getModifierCnName()))
                                .toList(),
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
