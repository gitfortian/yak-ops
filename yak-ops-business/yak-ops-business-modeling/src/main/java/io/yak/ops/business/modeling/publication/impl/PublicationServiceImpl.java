package io.yak.ops.business.modeling.publication.impl;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.publication.MetadataCollector;
import io.yak.ops.business.modeling.publication.PhysicalAddress;
import io.yak.ops.business.modeling.publication.PhysicalColumnSpec;
import io.yak.ops.business.modeling.publication.PhysicalDdlGenerator;
import io.yak.ops.business.modeling.publication.PhysicalDialect;
import io.yak.ops.business.modeling.publication.PhysicalExecutionException;
import io.yak.ops.business.modeling.publication.PhysicalExecutor;
import io.yak.ops.business.modeling.publication.PhysicalScript;
import io.yak.ops.business.modeling.publication.PhysicalTableVerifier;
import io.yak.ops.business.modeling.publication.PublicationFailures;
import io.yak.ops.business.modeling.publication.PublicationService;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.modeling.PublicationTargetDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.ModelFieldVO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.bean.vo.modeling.PublicationPreviewVO;
import io.yak.ops.common.bean.vo.modeling.PublicationVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Default publication flow.
 *
 * <p>Order of operations is deliberate: the target and the script are validated before anything is
 * recorded, the pending record is committed before the engine is contacted, and the terminal state is
 * written from the observed outcome. That way a process that dies mid-execution leaves an auditable
 * {@code PENDING} or {@code UNKNOWN} row instead of no trace at all.
 */
@Service
public class PublicationServiceImpl implements PublicationService {

    /** Bounded history; the surface shows the newest records and does not page today. */
    private static final int HISTORY_LIMIT = 50;

    /** Same bound the catalog table listing accepts; the target table is matched exactly. */
    private static final int TABLE_LOOKUP_LIMIT = 200;

    @Resource
    private ModelService models;

    @Resource
    private LayerRepository layers;

    @Resource
    private DataSourceLookupService resources;

    @Resource
    private DataSourceCatalogService catalog;

    @Resource
    private PhysicalExecutor executor;

    @Resource
    private PublicationRepository publications;

    @Resource
    private MetadataCollector collector;

    @Override
    public PublicationPreviewVO preview(String workspaceId, String actor, String modelId, PublicationTargetDTO target) {
        var plan = prepare(workspaceId, actor, modelId, target);
        return new PublicationPreviewVO(
                plan.address().database(),
                plan.address().schema(),
                plan.model().name(),
                plan.script().script());
    }

    @Override
    public PublicationVO publish(String workspaceId, String actor, String modelId, PublicationTargetDTO target) {
        var plan = prepare(workspaceId, actor, modelId, target);
        var record = new ModelPublicationEntity();
        record.setId(IdUtils.nextId());
        record.setWorkspaceId(workspaceId);
        record.setModelId(plan.model().id());
        record.setDatasourceId(plan.datasourceId());
        record.setDatasourceRevision(plan.datasourceRevision());
        record.setPhysicalDatabase(plan.address().database());
        record.setPhysicalSchema(plan.address().schema());
        record.setPhysicalTable(plan.model().name());
        record.setStatus(PublicationStatus.PENDING.name());
        record.setDdl(plan.script().script());
        record.setCreatedTime(DateUtils.now());
        record.setCreateBy(actor);
        publications.add(record);
        execute(workspaceId, plan, record.getId());
        var saved = detail(workspaceId, record.getId());
        if (saved.status() == PublicationStatus.SUCCEEDED) {
            collectMetadata(workspaceId, actor, record.getId());
        }
        return detail(workspaceId, record.getId());
    }

    @Override
    public List<PublicationVO> history(String workspaceId, String modelId) {
        return publications.listByModel(workspaceId, modelId, HISTORY_LIMIT).stream()
                .map(PublicationServiceImpl::project)
                .toList();
    }

    @Override
    public PublicationVO detail(String workspaceId, String publicationId) {
        return project(require(workspaceId, publicationId));
    }

    @Override
    public PublicationVO reconcile(String workspaceId, String actor, String publicationId) {
        var record = require(workspaceId, publicationId);
        var status = PublicationStatus.from(record.getStatus());
        if (status != PublicationStatus.UNKNOWN) {
            throw new BusinessException("只有 UNKNOWN 状态的发布记录需要对账，当前状态为 " + record.getStatus());
        }
        var dialect = dialectOf(record);
        var observed = observedColumns(record.getDatasourceId(), addressOf(record), record.getPhysicalTable());
        if (observed.isEmpty()) {
            publications.reconcile(
                    workspaceId, publicationId, PublicationStatus.FAILED.name(), "对账时目标库中不存在该表", DateUtils.now());
            return detail(workspaceId, publicationId);
        }
        var mismatch = expectedColumns(workspaceId, actor, record.getModelId())
                .flatMap(columns -> PhysicalTableVerifier.verify(dialect, columns, observed.get()));
        var reconciled = mismatch.isPresent() ? PublicationStatus.FAILED : PublicationStatus.SUCCEEDED;
        publications.reconcile(workspaceId, publicationId, reconciled.name(), mismatch.orElse(null), DateUtils.now());
        if (reconciled == PublicationStatus.SUCCEEDED) {
            collectMetadata(workspaceId, actor, publicationId);
        }
        return detail(workspaceId, publicationId);
    }

    @Override
    public MetadataSnapshotVO metadata(String workspaceId, String publicationId) {
        // A collection that ran and failed still wrote a row, so an absent snapshot means no
        // collection was ever attempted: that is a missing resource, not a collection failure.
        require(workspaceId, publicationId);
        return collector
                .snapshot(workspaceId, publicationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    @Override
    public MetadataSnapshotVO refreshMetadata(String workspaceId, String actor, String publicationId) {
        var record = require(workspaceId, publicationId);
        if (PublicationStatus.from(record.getStatus()) != PublicationStatus.SUCCEEDED) {
            throw new BusinessException("只有发布成功的记录可以重新采集元数据，当前状态为 " + record.getStatus());
        }
        try {
            return collector.collect(workspaceId, actor, record);
        } catch (RuntimeException failure) {
            // The previous snapshot survives: the collection transaction rolled back.
            throw new BusinessException(DimensionModelingErrorCode.METADATA_COLLECTION_FAILED, failure);
        }
    }

    private void execute(String workspaceId, Plan plan, String publicationId) {
        try {
            var observed = observedColumns(
                    plan.datasourceId(), plan.address(), plan.model().name());
            if (observed.isPresent()) {
                var mismatch = PhysicalTableVerifier.verify(plan.dialect(), plan.columns(), observed.get());
                if (mismatch.isPresent()) {
                    publications.finish(
                            workspaceId,
                            publicationId,
                            PublicationStatus.FAILED.name(),
                            mismatch.get(),
                            DateUtils.now());
                    return;
                }
                publications.finish(
                        workspaceId, publicationId, PublicationStatus.SUCCEEDED.name(), null, DateUtils.now());
                return;
            }
            executor.execute(workspaceId, plan.datasourceId(), plan.script().statements());
            publications.finish(workspaceId, publicationId, PublicationStatus.SUCCEEDED.name(), null, DateUtils.now());
        } catch (PhysicalExecutionException failure) {
            var cause = failure.getCause() == null ? failure : failure.getCause();
            var outcome = PublicationFailures.classify(cause);
            publications.finish(
                    workspaceId,
                    publicationId,
                    (outcome == PublicationFailures.Outcome.UNKNOWN
                                    ? PublicationStatus.UNKNOWN
                                    : PublicationStatus.FAILED)
                            .name(),
                    PublicationFailures.describe(cause),
                    DateUtils.now());
        } catch (RuntimeException failure) {
            // Reading the target failed, so no statement was submitted: this is a definite failure.
            publications.finish(
                    workspaceId,
                    publicationId,
                    PublicationStatus.FAILED.name(),
                    PublicationFailures.describe(failure),
                    DateUtils.now());
        }
    }

    private void collectMetadata(String workspaceId, String actor, String publicationId) {
        var record = publications.find(workspaceId, publicationId).orElse(null);
        if (record == null) return;
        try {
            collector.collect(workspaceId, actor, record);
        } catch (RuntimeException failure) {
            // The table exists; only the snapshot failed. The refresh endpoint retries it.
            collector.recordFailure(workspaceId, actor, publicationId, PublicationFailures.describe(failure));
        }
    }

    private Plan prepare(String workspaceId, String actor, String modelId, PublicationTargetDTO target) {
        var model = models.detail(workspaceId, actor, modelId);
        if (model.type() == null) throw new BusinessException(DimensionModelingErrorCode.INVALID_MODEL);
        var fields = model.fields() == null ? List.<ModelFieldVO>of() : model.fields();
        if (fields.isEmpty()) throw new BusinessException("模型没有字段，无法发布");
        var layer = requireLayer(workspaceId, model);
        var datasource = resources.dataSource(workspaceId, layer.getDataSourceId());
        var dialect = PhysicalDialect.of(datasource.type());
        if (dialect == null) {
            throw new BusinessException("数据源类型 " + datasource.type() + " 暂不支持物理发布");
        }
        var address = validateTarget(layer.getDataSourceId(), dialect, target);
        var columns = columnsOf(fields);
        try {
            var script = PhysicalDdlGenerator.generate(dialect, address, model.name(), columns);
            return new Plan(model, layer.getDataSourceId(), datasource.revision(), dialect, address, columns, script);
        } catch (IllegalArgumentException failure) {
            // A model that cannot be expressed physically is a request problem, not an engine one.
            throw new BusinessException(failure.getMessage());
        }
    }

    private LayerEntity requireLayer(String workspaceId, ModelVO model) {
        var layer = layers.layer(workspaceId, model.layerId())
                .orElseThrow(() -> new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE));
        if (!layer.isEnabled()
                || layer.getDataSourceId() == null
                || layer.getDataSourceId().isBlank()) {
            throw new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE);
        }
        return layer;
    }

    private PhysicalAddress validateTarget(String datasourceId, PhysicalDialect dialect, PublicationTargetDTO target) {
        var address = PhysicalAddress.of(target.database(), target.schema());
        if (address.database() == null || address.database().isBlank()) {
            throw new BusinessException("请选择目标数据库");
        }
        if (dialect.requiresSchema() && address.schema() == null) {
            throw new BusinessException("该引擎要求显式选择目标 Schema");
        }
        if (!dialect.requiresSchema() && address.schema() != null) {
            throw new BusinessException("该引擎没有独立 Schema，请勿选择目标 Schema");
        }
        if (!catalog.listDatabases(datasourceId).contains(address.database())) {
            throw new BusinessException("数据源中不存在目标数据库 " + address.database());
        }
        if (dialect.requiresSchema()
                && !catalog.listSchemas(datasourceId, address.database()).contains(address.schema())) {
            throw new BusinessException("数据库 " + address.database() + " 下不存在 Schema " + address.schema());
        }
        return address;
    }

    private Optional<List<DataSourceCatalogColumnVO>> observedColumns(
            String datasourceId, PhysicalAddress address, String table) {
        var query = new DataSourceCatalogQueryDTO();
        query.setDatabase(address.database());
        query.setSchema(address.schema());
        query.setKeyword(table);
        query.setLimit(TABLE_LOOKUP_LIMIT);
        var wanted = table.trim().toLowerCase(Locale.ROOT);
        var present = catalog.listTables(datasourceId, query).stream()
                .anyMatch(candidate -> candidate.getName() != null
                        && candidate.getName().trim().toLowerCase(Locale.ROOT).equals(wanted));
        if (!present) return Optional.empty();
        var path = new DataSourceTablePathDTO();
        path.setDatabase(address.database());
        path.setSchema(address.schema());
        path.setTable(table);
        return Optional.of(catalog.listColumns(datasourceId, path));
    }

    private Optional<List<PhysicalColumnSpec>> expectedColumns(String workspaceId, String actor, String modelId) {
        if (modelId == null) return Optional.empty();
        try {
            var model = models.detail(workspaceId, actor, modelId);
            var fields = model.fields() == null ? List.<ModelFieldVO>of() : model.fields();
            return fields.isEmpty() ? Optional.empty() : Optional.of(columnsOf(fields));
        } catch (BusinessException missing) {
            // The model was deleted after publication; reconciliation can only verify existence.
            return Optional.empty();
        }
    }

    private PhysicalDialect dialectOf(ModelPublicationEntity record) {
        var datasource = resources.dataSource(record.getWorkspaceId(), record.getDatasourceId());
        var dialect = PhysicalDialect.of(datasource.type());
        if (dialect == null) {
            throw new BusinessException("数据源类型 " + datasource.type() + " 暂不支持物理发布");
        }
        return dialect;
    }

    private static PhysicalAddress addressOf(ModelPublicationEntity record) {
        return PhysicalAddress.of(record.getPhysicalDatabase(), record.getPhysicalSchema());
    }

    private static List<PhysicalColumnSpec> columnsOf(List<ModelFieldVO> fields) {
        var ordered = fields.stream()
                .sorted(Comparator.comparingInt(ModelFieldVO::ordinal))
                .toList();
        var columns = new ArrayList<PhysicalColumnSpec>(ordered.size());
        var keyPosition = 0;
        for (var field : ordered) {
            var position = field.isPrimaryKey() ? ++keyPosition : null;
            columns.add(new PhysicalColumnSpec(
                    field.enCode(),
                    field.dataType(),
                    field.lengthPrecision(),
                    field.isNotNull(),
                    position,
                    field.cnName()));
        }
        return List.copyOf(columns);
    }

    private ModelPublicationEntity require(String workspaceId, String publicationId) {
        return publications
                .find(workspaceId, publicationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private static PublicationVO project(ModelPublicationEntity record) {
        return new PublicationVO(
                record.getId(),
                record.getModelId(),
                record.getDatasourceId(),
                record.getDatasourceRevision(),
                record.getPhysicalDatabase(),
                record.getPhysicalSchema(),
                record.getPhysicalTable(),
                PublicationStatus.from(record.getStatus()),
                record.getDdl(),
                record.getError(),
                record.getCreatedTime(),
                record.getFinishedTime(),
                record.getCreateBy());
    }

    /** Everything one publication needs, validated before anything is written or executed. */
    private record Plan(
            ModelVO model,
            String datasourceId,
            long datasourceRevision,
            PhysicalDialect dialect,
            PhysicalAddress address,
            List<PhysicalColumnSpec> columns,
            PhysicalScript script) {}
}
