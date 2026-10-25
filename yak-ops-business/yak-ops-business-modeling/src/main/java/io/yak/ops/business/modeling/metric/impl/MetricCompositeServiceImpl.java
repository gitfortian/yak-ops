package io.yak.ops.business.modeling.metric.impl;

import io.yak.ops.business.modeling.metric.MetricCatalog;
import io.yak.ops.business.modeling.metric.MetricCompositeService;
import io.yak.ops.business.modeling.metric.MetricExpressionParser;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricCompositeVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricCompositeRefEntity;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricCompositeRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricQuery;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns composite-metric writes; the expression is parsed and reference-resolved, never evaluated.
 *
 * <p>References point at derived metrics only, so the graph stays a two-level DAG by construction
 * and no cycle check exists to get wrong. The stored expression is the submitted text; the stored
 * references are what the delete check and reference panel read.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class MetricCompositeServiceImpl implements MetricCompositeService {
    @Resource
    private MetricCompositeRepository composites;

    @Resource
    private MetricDerivedRepository derived;

    @Resource
    private MetricCatalog catalog;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageData<MetricCompositeVO> page(
            String workspaceId, String actor, String keyword, int pageNo, int pageSize) {
        catalog.authorize(workspaceId, actor);
        long offset = MetricCatalog.offset(pageNo, pageSize);
        var query = new MetricQuery(null, null, MetricCatalog.keyword(keyword), offset, pageSize);
        long total = composites.count(workspaceId, query);
        return PageData.of(
                composites.page(workspaceId, query).stream()
                        .map(MetricCompositeServiceImpl::project)
                        .toList(),
                total,
                pageNo,
                pageSize);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricCompositeVO detail(String workspaceId, String actor, String id) {
        catalog.authorize(workspaceId, actor);
        return project(require(workspaceId, id));
    }

    @Override
    public MetricSavedVO create(String workspaceId, String actor, MetricCompositeInputDTO input) {
        catalog.authorize(workspaceId, actor);
        var ownerId = catalog.owner(workspaceId, actor, input == null ? null : input.getOwnerId());
        validate(workspaceId, input);
        var row = new MetricCompositeEntity();
        row.setWorkspaceId(workspaceId);
        copy(row, input);
        row.setOwnerId(ownerId);
        row.setRevision(1);
        row.initCreate(actor);
        row.setReferences(resolveReferences(workspaceId, input.getExpression()));
        try {
            composites.insert(row);
        } catch (DuplicateKeyException failure) {
            throw new BusinessException(MetricErrorCode.DUPLICATE_CODE, failure);
        }
        return new MetricSavedVO(row.getId(), 1);
    }

    @Override
    public MetricSavedVO update(String workspaceId, String actor, String id, MetricCompositeUpdateDTO input) {
        catalog.authorize(workspaceId, actor);
        var row = require(workspaceId, id);
        if (input == null) throw MetricCatalog.invalid();
        checkRevision(row.getRevision(), input.getRevision());
        var ownerId = catalog.owner(workspaceId, actor, input.getOwnerId());
        validate(workspaceId, input);
        copy(row, input);
        row.setOwnerId(ownerId);
        row.initUpdate(actor);
        row.setReferences(resolveReferences(workspaceId, input.getExpression()));
        try {
            if (!composites.update(row)) throw MetricCatalog.conflict();
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
        if (!composites.delete(workspaceId, id, revision)) throw MetricCatalog.conflict();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public boolean available(String workspaceId, String actor, String enCode, String excludeId) {
        catalog.authorize(workspaceId, actor);
        if (excludeId != null) require(workspaceId, excludeId);
        MetricCatalog.validCode(enCode);
        return !composites.occupied(workspaceId, enCode, excludeId);
    }

    private MetricCompositeEntity require(String workspaceId, String id) {
        if (id == null || id.isBlank()) throw MetricCatalog.invalid();
        return composites
                .find(workspaceId, id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private void validate(String workspaceId, MetricCompositeInputDTO input) {
        if (input == null) throw MetricCatalog.invalid();
        MetricCatalog.validCode(input.getEnCode());
        MetricCatalog.validName(input.getCnName());
        if (input.getExpression() == null || input.getExpression().isBlank()) throw MetricCatalog.invalid();
        if (input.getExpression().length() > 2048) throw MetricCatalog.invalid();
        var codes = referencedCodes(input.getExpression());
        if (codes.isEmpty()) throw new BusinessException(MetricErrorCode.INVALID_METRIC_EXPRESSION);
        for (var code : codes) {
            derived
                    .byCode(workspaceId, code)
                    .orElseThrow(() -> new BusinessException(MetricErrorCode.INVALID_METRIC_EXPRESSION));
        }
        if (input.getDescription() != null && input.getDescription().length() > 500) throw MetricCatalog.invalid();
    }

    private List<MetricCompositeRefEntity> resolveReferences(String workspaceId, String expression) {
        var links = new ArrayList<MetricCompositeRefEntity>();
        for (var code : new LinkedHashSet<>(referencedCodes(expression))) {
            MetricDerivedEntity row = derived
                    .byCode(workspaceId, code)
                    .orElseThrow(() -> new BusinessException(MetricErrorCode.INVALID_METRIC_EXPRESSION));
            var reference = new MetricCompositeRefEntity();
            reference.setDerivedMetricId(row.getId());
            links.add(reference);
        }
        return List.copyOf(links);
    }

    private static List<String> referencedCodes(String expression) {
        try {
            return MetricExpressionParser.references(expression);
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(MetricErrorCode.INVALID_METRIC_EXPRESSION, failure);
        }
    }

    private static void copy(MetricCompositeEntity row, MetricCompositeInputDTO input) {
        row.setEnCode(input.getEnCode());
        row.setCnName(input.getCnName().strip());
        row.setExpression(input.getExpression().strip());
        row.setDescription(MetricCatalog.optionalText(input.getDescription(), 500));
    }

    private static MetricCompositeVO project(MetricCompositeEntity row) {
        return new MetricCompositeVO(
                row.getId(),
                row.getEnCode(),
                row.getCnName(),
                row.getExpression(),
                row.getReferences() == null
                        ? List.of()
                        : row.getReferences().stream()
                                .map(reference -> new MetricCompositeVO.Reference(
                                        reference.getDerivedMetricId(),
                                        reference.getDerivedEnCode(),
                                        reference.getDerivedCnName()))
                                .toList(),
                row.getOwnerId(),
                row.getDescription(),
                row.getUpdateTime(),
                row.getRevision());
    }

    private static void checkRevision(long current, Long expected) {
        if (expected == null || expected < 1) throw MetricCatalog.invalid();
        if (current != expected) throw MetricCatalog.conflict();
    }
}
