package io.yak.ops.business.modeling.metric.impl;

import io.yak.ops.business.modeling.metric.MetricCatalog;
import io.yak.ops.business.modeling.metric.MetricCompositionSync;
import io.yak.ops.business.modeling.metric.MetricModifierService;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricModifierVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.metric.MetricModifierEntity;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricModifierRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns modifier writes; modifiers anchor to one data domain of the Workspace. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MetricModifierServiceImpl implements MetricModifierService {
    @Resource
    private MetricModifierRepository modifiers;

    @Resource
    private MetricReferenceRepository references;

    @Resource
    private PlanningRepository planning;

    @Resource
    private MetricCatalog catalog;

    @Resource
    private MetricCompositionSync composition;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<MetricModifierVO> page(
            String workspaceId, String actor, String domainId, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = MetricCatalog.offset(pageNo, pageSize);
        var query = new MetricQuery(null, trim(domainId), MetricCatalog.keyword(keyword), offset, pageSize);
        long total = modifiers.count(workspaceId, query);
        return PageData.of(
                modifiers.page(workspaceId, query).stream()
                        .map(MetricModifierServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricModifierVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(require(workspaceId, id));
    }

    @Override
    public MetricSavedVO create(String workspaceId, String actor, MetricModifierInputDTO input) {
        catalog.authorize(workspaceId, actor);
        validate(workspaceId, input);
        var row = new MetricModifierEntity();
        row.setWorkspaceId(workspaceId);
        copy(row, input);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            modifiers.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(row.getId(), 1);
    }

    @Override
    public MetricSavedVO update(String workspaceId, String actor, String id, MetricModifierUpdateDTO input) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw MetricCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        validate(workspaceId, input);
        var previousCode = row.getEnCode();
        var previousName = row.getCnName();
        copy(row, input);
        row.initUpdate(actor);
        try {
            if (!modifiers.update(row)) throw MetricCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        if (!row.getEnCode().equals(previousCode) || !row.getCnName().equals(previousName))
            composition.partEdited(
                    workspaceId, actor, MetricCompositionSync.Part.MODIFIER, id, row.getEnCode(), row.getCnName());
        return new MetricSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!modifiers.delete(workspaceId, id, revision)) throw MetricCatalog.conflict();
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
        return !modifiers.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return MetricReferencesVO.of(references.byModifier(workspaceId, id));
    }

    private MetricModifierEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw MetricCatalog.invalid();
        return modifiers
                .find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void validate(String workspaceId, MetricModifierInputDTO input) {
        if (input == null) throw MetricCatalog.invalid();
        var domainId = trim(input.getDomainId());
        if (domainId == null) throw MetricCatalog.invalid();
        planning.find(workspaceId, NodeKind.DOMAIN, domainId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        MetricCatalog.validCode(input.getEnCode());
        MetricCatalog.validName(input.getCnName());
        if (input.getDescription() != null && input.getDescription().strip().length() > 500)
            throw MetricCatalog.invalid();
    }

    private static void copy(MetricModifierEntity row, MetricModifierInputDTO input) {
        row.setDomainId(input.getDomainId().strip());
        row.setEnCode(input.getEnCode());
        row.setCnName(input.getCnName().strip());
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
    }

    private static MetricModifierVO project(MetricModifierEntity row) {
        return new MetricModifierVO(
                row.getId(),
                row.getDomainId(),
                row.getDomainName(),
                row.getEnCode(),
                row.getCnName(),
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
