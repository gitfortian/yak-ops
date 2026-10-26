package io.yak.ops.business.modeling.metric.impl;

import io.yak.ops.business.modeling.metric.MetricCatalog;
import io.yak.ops.business.modeling.metric.MetricCompositionSync;
import io.yak.ops.business.modeling.metric.MetricTimePeriodService;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricTimePeriodVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.metric.MetricTimePeriodEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import io.yak.ops.dao.repository.modeling.metric.MetricReferenceRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricTimePeriodRepository;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Owns time-period writes; the dictionary is flat and Workspace-scoped. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MetricTimePeriodServiceImpl implements MetricTimePeriodService {
    @Resource
    private MetricTimePeriodRepository periods;

    @Resource
    private MetricReferenceRepository references;

    @Resource
    private MetricCatalog catalog;

    @Resource
    private MetricCompositionSync composition;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<MetricTimePeriodVO> page(
            String workspaceId, String actor, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = MetricCatalog.offset(pageNo, pageSize);
        var query = new MetricQuery(null, null, MetricCatalog.keyword(keyword), offset, pageSize);
        long total = periods.count(workspaceId, query);
        return PageData.of(
                periods.page(workspaceId, query).stream()
                        .map(MetricTimePeriodServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricTimePeriodVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(require(workspaceId, id));
    }

    @Override
    public MetricSavedVO create(String workspaceId, String actor, MetricTimePeriodInputDTO input) {
        catalog.authorize(workspaceId, actor);
        validate(input);
        var row = new MetricTimePeriodEntity();
        row.setWorkspaceId(workspaceId);
        copy(row, input);
        row.setRevision(1);
        row.initCreate(actor);
        try {
            periods.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(row.getId(), 1);
    }

    @Override
    public MetricSavedVO update(String workspaceId, String actor, String id, MetricTimePeriodUpdateDTO input) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw MetricCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        validate(input);
        var previousCode = row.getEnCode();
        var previousName = row.getCnName();
        copy(row, input);
        row.initUpdate(actor);
        try {
            if (!periods.update(row)) throw MetricCatalog.conflict();
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        if (!row.getEnCode().equals(previousCode) || !row.getCnName().equals(previousName))
            composition.partEdited(
                    workspaceId, actor, MetricCompositionSync.Part.PERIOD, id, row.getEnCode(), row.getCnName());
        return new MetricSavedVO(id, Math.addExact(row.getRevision(), 1));
    }

    @Override
    public void delete(String workspaceId, String actor, String id, long revision) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        checkRevision(row.getRevision(), revision);
        try {
            if (!periods.delete(workspaceId, id, revision)) throw MetricCatalog.conflict();
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
        return !periods.occupied(workspaceId, enCode, excludeId);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricReferencesVO references(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        require(workspaceId, id);
        return MetricReferencesVO.of(references.byPeriod(workspaceId, id));
    }

    private MetricTimePeriodEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw MetricCatalog.invalid();
        return periods.find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private static void validate(MetricTimePeriodInputDTO input) {
        if (input == null) throw MetricCatalog.invalid();
        MetricCatalog.validCode(input.getEnCode());
        MetricCatalog.validName(input.getCnName());
        if (input.getDescription() != null && input.getDescription().strip().length() > 500)
            throw MetricCatalog.invalid();
    }

    private static void copy(MetricTimePeriodEntity row, MetricTimePeriodInputDTO input) {
        row.setEnCode(input.getEnCode());
        row.setCnName(input.getCnName().strip());
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
    }

    private static MetricTimePeriodVO project(MetricTimePeriodEntity row) {
        return new MetricTimePeriodVO(
                row.getId(),
                row.getEnCode(),
                row.getCnName(),
                row.getDescription(),
                row.getUpdateTime(),
                row.getRevision());
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw MetricCatalog.invalid();
        if (current != expected) throw MetricCatalog.conflict();
    }
}
