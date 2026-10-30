package io.yak.ops.business.modeling.publication.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.publication.MetadataCollector;
import io.yak.ops.business.modeling.publication.ModelPublicationService;
import io.yak.ops.business.modeling.publication.PhysicalAddress;
import io.yak.ops.business.modeling.publication.PhysicalColumnSpec;
import io.yak.ops.business.modeling.publication.PhysicalDdlGenerator;
import io.yak.ops.business.modeling.publication.PhysicalDialect;
import io.yak.ops.business.modeling.publication.PhysicalExecutionException;
import io.yak.ops.business.modeling.publication.PhysicalExecutor;
import io.yak.ops.business.modeling.publication.PhysicalScript;
import io.yak.ops.business.modeling.publication.PhysicalTableProperties;
import io.yak.ops.business.modeling.publication.PhysicalTableVerifier;
import io.yak.ops.business.modeling.publication.PublicationFailures;
import io.yak.ops.business.modeling.publication.SchemaDiff;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.dto.modeling.PublicationTargetDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.ModelFieldVO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.bean.vo.modeling.PublicationPreviewVO;
import io.yak.ops.common.bean.vo.modeling.PublicationVO;
import io.yak.ops.common.bean.vo.modeling.SchemaChangeVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.PublicationEnvironment;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.publication.PublicationErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.LayerTargetRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
 *
 * <p>The target address is never typed by the user: the model's layer binds one datasource and
 * database per environment ({@code DEVELOP} / {@code PROD}), and the request names the environment
 * only. Before anything runs, the live table structure is diffed against the model — a missing
 * table becomes a {@code CREATE}, a real difference becomes an {@code ALTER}, and an equal table
 * answers {@code NONE} instead of pretending to publish.
 */
@Service
public class ModelPublicationServiceImpl implements ModelPublicationService {

    /** Bounded history; the surface shows the newest records and does not page today. */
    private static final int HISTORY_LIMIT = 50;

    /** Same bound the catalog table listing accepts; the target table is matched exactly. */
    private static final int TABLE_LOOKUP_LIMIT = 200;

    private static final DateTimeFormatter LOG_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final ObjectMapper CHANGES = new ObjectMapper();

    @Resource
    private ModelService models;

    @Resource
    private LayerRepository layers;

    @Resource
    private LayerTargetRepository layerTargets;

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
                plan.operation().name(),
                changesToVo(plan.changes()),
                plan.script() == null ? null : plan.script().script(),
                plan.dropsData());
    }

    @Override
    public PublicationVO publish(String workspaceId, String actor, String modelId, PublicationTargetDTO target) {
        var plan = prepare(workspaceId, actor, modelId, target);
        if (plan.operation() == Operation.NONE) {
            throw new BusinessException(PublicationErrorCode.INVALID_PUBLICATION_STATE, "物理表与模型结构一致，无需发布");
        }
        if (plan.dropsData() && !Boolean.TRUE.equals(target.acknowledgeDataLoss())) {
            throw new BusinessException(PublicationErrorCode.DANGEROUS_OPERATION_UNCONFIRMED);
        }
        var record = new ModelPublicationEntity();
        record.setId(IdUtils.nextId());
        record.setWorkspaceId(workspaceId);
        record.setModelId(plan.model().id());
        record.setDatasourceId(plan.datasourceId());
        record.setDatasourceRevision(plan.datasourceRevision());
        record.setEnvironment(plan.environment().name());
        record.setOperation(plan.operation().name());
        record.setPhysicalDatabase(plan.address().database());
        record.setPhysicalSchema(plan.address().schema());
        // Stored as the catalog spells it, so every later lookup (verify, reconcile, metadata)
        // asks for the name the engine actually reports instead of the model's lower-case form.
        record.setPhysicalTable(plan.dialect().catalogName(plan.model().name()));
        record.setStatus(PublicationStatus.PENDING.name());
        record.setDdl(plan.script().script());
        record.setChangesJson(changesJson(plan.changes()));
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
                .map(ModelPublicationServiceImpl::project)
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
        // PENDING means the row was written but the terminal state never was: a process that died
        // mid-execution leaves that residue, and re-reading the target is the only way to settle it.
        if (status != PublicationStatus.UNKNOWN && status != PublicationStatus.PENDING) {
            throw new BusinessException(
                    PublicationErrorCode.INVALID_PUBLICATION_STATE,
                    "只有 PENDING 或 UNKNOWN 状态的发布记录需要对账，当前状态为 " + record.getStatus());
        }
        var dialect = dialectOf(record);
        var observed = observedColumns(
                record.getDatasourceId(), addressOf(record), dialect.catalogName(record.getPhysicalTable()));
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
            throw new BusinessException(
                    PublicationErrorCode.INVALID_PUBLICATION_STATE, "只有发布成功的记录可以重新采集元数据，当前状态为 " + record.getStatus());
        }
        try {
            return collector.collect(workspaceId, actor, record);
        } catch (RuntimeException failure) {
            // The previous snapshot survives: the collection transaction rolled back.
            throw new BusinessException(DimensionModelingErrorCode.METADATA_COLLECTION_FAILED, failure);
        }
    }

    private void execute(String workspaceId, Plan plan, String publicationId) {
        var started = System.currentTimeMillis();
        var log = new StringBuilder();
        var clock = LocalTime.now();
        log.append('[')
                .append(LOG_CLOCK.format(clock))
                .append("] 目标 ")
                .append(plan.address().database())
                .append(
                        plan.address().schema() == null
                                ? ""
                                : "." + plan.address().schema())
                .append('.')
                .append(plan.dialect().catalogName(plan.model().name()))
                .append("（")
                .append(plan.operation())
                .append("）\n");
        var counts = changeCounts(plan.changes());
        log.append('[')
                .append(LOG_CLOCK.format(LocalTime.now()))
                .append("] 结构对比：")
                .append(counts)
                .append('\n');
        try {
            var results = executor.execute(
                    workspaceId, plan.datasourceId(), plan.script().statements());
            for (int index = 0; index < results.size(); index++) {
                var result = results.get(index);
                log.append('[')
                        .append(LOG_CLOCK.format(LocalTime.now()))
                        .append("] 执行 ")
                        .append(index + 1)
                        .append('/')
                        .append(results.size())
                        .append("（")
                        .append(result.durationMs())
                        .append(" ms）：")
                        .append(singleLine(result.statement()))
                        .append('\n');
            }
            var duration = System.currentTimeMillis() - started;
            log.append('[')
                    .append(LOG_CLOCK.format(LocalTime.now()))
                    .append("] 发布成功，共 ")
                    .append(results.size())
                    .append(" 条语句，耗时 ")
                    .append(duration)
                    .append(" ms");
            publications.finish(
                    workspaceId,
                    publicationId,
                    PublicationStatus.SUCCEEDED.name(),
                    null,
                    duration,
                    log.toString(),
                    DateUtils.now());
        } catch (PhysicalExecutionException failure) {
            var cause = failure.getCause() == null ? failure : failure.getCause();
            var outcome = PublicationFailures.classify(cause);
            var status = outcome == PublicationFailures.Outcome.UNKNOWN
                    ? PublicationStatus.UNKNOWN
                    : PublicationStatus.FAILED;
            var completed = failure.completed();
            for (int index = 0; index < completed.size(); index++) {
                var result = completed.get(index);
                log.append('[')
                        .append(LOG_CLOCK.format(LocalTime.now()))
                        .append("] 执行 ")
                        .append(index + 1)
                        .append("（")
                        .append(result.durationMs())
                        .append(" ms）：")
                        .append(singleLine(result.statement()))
                        .append('\n');
            }
            if (failure.failedStatement() != null) {
                log.append('[')
                        .append(LOG_CLOCK.format(LocalTime.now()))
                        .append("] 执行失败：")
                        .append(singleLine(failure.failedStatement()))
                        .append('\n');
            }
            log.append('[')
                    .append(LOG_CLOCK.format(LocalTime.now()))
                    .append("] 发布失败（")
                    .append(status)
                    .append("），已耗时 ")
                    .append(System.currentTimeMillis() - started)
                    .append(" ms\n")
                    .append(stackTraceOf(cause));
            publications.finish(
                    workspaceId,
                    publicationId,
                    status.name(),
                    PublicationFailures.describe(cause),
                    System.currentTimeMillis() - started,
                    log.toString(),
                    DateUtils.now());
        } catch (RuntimeException failure) {
            // Reading the target failed, so no statement was submitted: this is a definite failure.
            var duration = System.currentTimeMillis() - started;
            log.append('[')
                    .append(LOG_CLOCK.format(LocalTime.now()))
                    .append("] 发布失败（FAILED），已耗时 ")
                    .append(duration)
                    .append(" ms\n")
                    .append(stackTraceOf(failure));
            publications.finish(
                    workspaceId,
                    publicationId,
                    PublicationStatus.FAILED.name(),
                    PublicationFailures.describe(failure),
                    duration,
                    log.toString(),
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
        if (fields.isEmpty()) throw new BusinessException(PublicationErrorCode.UNSUPPORTED_PUBLICATION, "模型没有字段，无法发布");
        var environment = target == null || target.environment() == null
                ? null
                : PublicationEnvironment.from(target.environment());
        if (environment == null) throw new BusinessException("请选择发布环境（开发或生产）");
        var layer = requireLayer(workspaceId, model);
        var bound = layerTargets
                .target(workspaceId, layer.getId(), environment.name())
                .orElseThrow(() -> new BusinessException(
                        PublicationErrorCode.TARGET_NOT_BOUND,
                        "分层「" + layer.getCnName() + "」未绑定"
                                + (environment == PublicationEnvironment.DEVELOP ? "开发" : "生产")
                                + "环境的发布目标，请先在数仓分层中配置"));
        var datasource = resources.dataSource(workspaceId, bound.getDataSourceId());
        var dialect = PhysicalDialect.of(datasource.type());
        if (dialect == null) {
            throw new BusinessException(
                    PublicationErrorCode.UNSUPPORTED_PUBLICATION, "数据源类型 " + datasource.type() + " 暂不支持物理发布");
        }
        var properties = propertiesOf(model, dialect);
        var address = validateTarget(bound.getDataSourceId(), dialect, bound);
        var columns = columnsOf(fields);
        var observed = observedColumns(bound.getDataSourceId(), address, dialect.catalogName(model.name()));
        Operation operation;
        List<SchemaDiff> changes = List.of();
        PhysicalScript script;
        if (observed.isEmpty()) {
            operation = Operation.CREATE;
            script = generate(dialect, address, model, columns, properties);
        } else {
            changes = SchemaDiff.diff(dialect, columns, observed.get());
            if (changes.isEmpty()) {
                operation = Operation.NONE;
                script = null;
            } else {
                operation = Operation.ALTER;
                script = alter(dialect, address, model, columns, changes);
            }
        }
        return new Plan(
                model,
                layer,
                environment,
                bound.getDataSourceId(),
                datasource.revision(),
                dialect,
                address,
                properties,
                columns,
                changes,
                operation,
                script);
    }

    private LayerEntity requireLayer(String workspaceId, ModelVO model) {
        var layer = layers.layer(workspaceId, model.layerId())
                .orElseThrow(() -> new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE));
        if (!layer.isEnabled()) throw new BusinessException(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE);
        return layer;
    }

    private PhysicalTableProperties propertiesOf(ModelVO model, PhysicalDialect dialect) {
        try {
            return PhysicalTableProperties.parse(model.physicalProperties(), dialect);
        } catch (IllegalArgumentException failure) {
            // Properties come from the saved model; a mismatch is a request problem, not engine one.
            throw new BusinessException(failure.getMessage());
        }
    }

    private PhysicalScript generate(
            PhysicalDialect dialect,
            PhysicalAddress address,
            ModelVO model,
            List<PhysicalColumnSpec> columns,
            PhysicalTableProperties properties) {
        try {
            return PhysicalDdlGenerator.generate(dialect, address, model.name(), columns, properties);
        } catch (IllegalArgumentException failure) {
            // A model that cannot be expressed physically is a request problem, not an engine one.
            throw new BusinessException(failure.getMessage());
        }
    }

    private PhysicalScript alter(
            PhysicalDialect dialect,
            PhysicalAddress address,
            ModelVO model,
            List<PhysicalColumnSpec> columns,
            List<SchemaDiff> changes) {
        try {
            return PhysicalDdlGenerator.generateAlter(dialect, address, model.name(), columns, changes);
        } catch (IllegalArgumentException failure) {
            throw new BusinessException(failure.getMessage());
        }
    }

    private PhysicalAddress validateTarget(String datasourceId, PhysicalDialect dialect, LayerTargetEntity bound) {
        var address = PhysicalAddress.of(bound.getPhysicalDatabase(), bound.getPhysicalSchema());
        if (address.database() == null || address.database().isBlank()) {
            throw new BusinessException("该分层的发布目标未配置目标数据库");
        }
        if (dialect.requiresSchema() && address.schema() == null) {
            throw new BusinessException("该引擎要求显式配置目标 Schema，请先在数仓分层中补全");
        }
        if (!dialect.requiresSchema() && address.schema() != null) {
            throw new BusinessException("该引擎没有独立 Schema，请修正分层发布目标的 Schema 配置");
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
            throw new BusinessException(
                    PublicationErrorCode.UNSUPPORTED_PUBLICATION, "数据源类型 " + datasource.type() + " 暂不支持物理发布");
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

    private static List<SchemaChangeVO> changesToVo(List<SchemaDiff> changes) {
        return changes.stream()
                .map(change -> new SchemaChangeVO(
                        change.kind().name(), change.column(), change.fromType(), change.toType(), change.detail()))
                .toList();
    }

    private static String changesJson(List<SchemaDiff> changes) {
        if (changes.isEmpty()) return "[]";
        try {
            return CHANGES.writeValueAsString(changesToVo(changes));
        } catch (Exception serialization) {
            // A record without its diff snapshot is better than no record at all; the DDL and the
            // execution log still describe what ran.
            return null;
        }
    }

    private static List<SchemaChangeVO> changesFromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return List.of(CHANGES.readValue(json, SchemaChangeVO[].class));
        } catch (Exception deserialization) {
            // History stays readable even if a hand-written row carries a malformed snapshot.
            return List.of();
        }
    }

    private static String changeCounts(List<SchemaDiff> changes) {
        if (changes.isEmpty()) return "无差异";
        var add = changes.stream()
                .filter(change -> change.kind() == SchemaDiff.Kind.ADD)
                .count();
        var change = changes.stream()
                .filter(item -> item.kind() == SchemaDiff.Kind.CHANGE)
                .count();
        var drop = changes.stream()
                .filter(item -> item.kind() == SchemaDiff.Kind.DROP)
                .count();
        return "新增 " + add + " 列，类型变更 " + change + " 列，删除 " + drop + " 列";
    }

    private static String singleLine(String sql) {
        return sql.replace("\n", " ");
    }

    private static String stackTraceOf(Throwable failure) {
        var buffer = new StringWriter();
        failure.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    private static PublicationVO project(ModelPublicationEntity record) {
        return new PublicationVO(
                record.getId(),
                record.getModelId(),
                record.getDatasourceId(),
                record.getDatasourceRevision(),
                PublicationEnvironment.from(record.getEnvironment()),
                record.getPhysicalDatabase(),
                record.getPhysicalSchema(),
                record.getPhysicalTable(),
                record.getOperation(),
                PublicationStatus.from(record.getStatus()),
                changesFromJson(record.getChangesJson()),
                record.getDdl(),
                record.getDurationMs(),
                record.getExecutionLog(),
                record.getError(),
                record.getCreatedTime(),
                record.getFinishedTime(),
                record.getCreateBy());
    }

    /**
     * Everything one publication needs, validated before anything is written or executed. The
     * operation decides whether a script exists at all: {@code NONE} has none.
     */
    private record Plan(
            ModelVO model,
            LayerEntity layer,
            PublicationEnvironment environment,
            String datasourceId,
            long datasourceRevision,
            PhysicalDialect dialect,
            PhysicalAddress address,
            PhysicalTableProperties properties,
            List<PhysicalColumnSpec> columns,
            List<SchemaDiff> changes,
            Operation operation,
            PhysicalScript script) {

        boolean dropsData() {
            return SchemaDiff.dropsData(changes);
        }
    }

    /** The three outcomes a plan can have; NONE is a valid answer, not an error. */
    private enum Operation {
        CREATE,
        ALTER,
        NONE
    }
}
