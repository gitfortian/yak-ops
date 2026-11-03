package io.yak.ops.business.modeling.semantic;

import io.yak.ops.common.bean.vo.modeling.SemanticExportCheckVO;
import io.yak.ops.common.bean.vo.modeling.SemanticExportVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingRelationRepository;
import io.yak.ops.dao.repository.modeling.ModelingSynonymRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricCompositeRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders the one-model Ossie projection: the model, one hop of relations, and the metrics that
 * survive the portability whitelist. The exporter writes nothing back; Ossie is an output format
 * pinned to a draft specification, never a storage format.
 */
@Component
public class OssieSemanticExporter {
    public static final String SPEC_VERSION = "0.2.0.dev0";
    private static final Set<String> PORTABLE_AGGREGATES = Set.of(
            AggregateType.SUM.name(),
            AggregateType.COUNT.name(),
            AggregateType.AVG.name(),
            AggregateType.MAX.name(),
            AggregateType.MIN.name());
    private static final String VENDOR = "yak-ops";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String LEVEL_WARN = "WARN";
    private static final String LEVEL_OK = "OK";
    private static final String MODEL_FIELD_SYNONYM = "MODEL_FIELD";

    @Resource
    private ModelRepository models;

    @Resource
    private ModelingRelationRepository relations;

    @Resource
    private ModelingSynonymRepository synonyms;

    @Resource
    private PublicationRepository publications;

    @Resource
    private MetricAtomicRepository atomicMetrics;

    @Resource
    private MetricDerivedRepository derivedMetrics;

    @Resource
    private MetricCompositeRepository compositeMetrics;

    @Resource
    private LayerRepository layers;

    @Resource
    private WorkspaceService workspaces;

    /**
     * Renders the semantic model of one logical model.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId model identity
     * @return pre-flight checks plus the Ossie YAML document
     */
    @Transactional(readOnly = true)
    public SemanticExportVO export(String workspaceId, String actor, String modelId) {
        if (actor == null || actor.isBlank() || !workspaces.isActiveMember(workspaceId, actor))
            throw new BusinessException(io.yak.ops.workspace.enums.WorkspaceErrorCode.ACCESS_DENIED);
        ModelEntity model = models.find(workspaceId, modelId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        List<ModelFieldEntity> fields = models.fields(modelId);
        List<ModelingRelationEntity> relationRows = this.relations.listByModel(workspaceId, modelId);

        Map<String, ModelEntity> datasetModels = new LinkedHashMap<>();
        Map<String, List<ModelFieldEntity>> datasetFields = new LinkedHashMap<>();
        datasetModels.put(model.getId(), model);
        datasetFields.put(model.getId(), fields);
        for (ModelingRelationEntity relation : relationRows) {
            String otherId = model.getId().equals(relation.getFromModelId())
                    ? relation.getToModelId()
                    : relation.getFromModelId();
            if (datasetModels.containsKey(otherId)) continue;
            ModelEntity other = models.find(workspaceId, otherId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
            datasetModels.put(otherId, other);
            datasetFields.put(otherId, models.fields(otherId));
        }
        Map<String, String> datasetNames = datasetNames(datasetModels);

        List<SemanticExportCheckVO> checks = new ArrayList<>();
        int readyKeys = 0;
        for (ModelingRelationEntity relation : relationRows) {
            boolean fromSide = relation.getFromModelId().equals(model.getId());
            String ownModelId = fromSide ? relation.getFromModelId() : relation.getToModelId();
            String otherModelId = fromSide ? relation.getToModelId() : relation.getFromModelId();
            List<ModelFieldEntity> ownFields = datasetFields.get(ownModelId);
            List<ModelFieldEntity> otherFields = datasetFields.get(otherModelId);
            for (ModelingRelationColumnEntity pair : relations.columns(relation.getId())) {
                ModelFieldEntity own = byCode(ownFields, fromSide ? pair.getFromFieldCode() : pair.getToFieldCode());
                ModelFieldEntity other =
                        byCode(otherFields, fromSide ? pair.getToFieldCode() : pair.getFromFieldCode());
                if (own == null || other == null) {
                    checks.add(new SemanticExportCheckVO(
                            LEVEL_ERROR, "FIELD_REMOVED", "关系 " + relation.getName() + " 的关联键字段已移除，无法完成导出"));
                } else if (own.getStandardId() == null || other.getStandardId() == null) {
                    checks.add(new SemanticExportCheckVO(
                            LEVEL_WARN, "RELATION_KEY_UNSTANDARDIZED", "关系 " + relation.getName() + " 存在未绑定标准的关联键"));
                } else {
                    readyKeys++;
                }
            }
        }
        if (relationRows.isEmpty()) {
            checks.add(new SemanticExportCheckVO(LEVEL_OK, "NO_RELATIONS", "该模型尚未声明关系"));
        } else if (readyKeys > 0) {
            checks.add(new SemanticExportCheckVO(LEVEL_OK, "RELATIONS_READY", readyKeys + " 个关联键已绑标准，就绪"));
        }

        MetricResolution metricResolution = resolveMetrics(workspaceId, fields, checks);

        String yaml = render(workspaceId, datasetModels, datasetFields, datasetNames, relationRows, metricResolution);
        return new SemanticExportVO(checks, yaml);
    }

    private MetricResolution resolveMetrics(
            String workspaceId, List<ModelFieldEntity> fields, List<SemanticExportCheckVO> checks) {
        Map<String, ModelFieldEntity> binding = new HashMap<>();
        Set<String> metricIds = new LinkedHashSet<>();
        for (ModelFieldEntity field : fields) {
            if (field.getMetricId() != null) {
                binding.put(field.getMetricId(), field);
                metricIds.add(field.getMetricId());
            }
        }
        List<SemanticMetricProjection> projections = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String metricId : metricIds) {
            String metricCode = metricCode(workspaceId, metricId);
            long bindings = models.countFieldsByMetric(workspaceId, metricId);
            if (bindings != 1) {
                skipped.add(metricCode);
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN,
                        "METRIC_SKIPPED",
                        "指标 " + metricCode + " 关联了 " + bindings + " 个模型字段，无法唯一确定数据来源，已跳过"));
                continue;
            }
            var atomic = atomicMetrics.find(workspaceId, metricId);
            if (atomic.isEmpty()) {
                skipped.add(metricCode);
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN, "METRIC_SKIPPED", "指标 " + metricCode + " 为派生或复合指标，暂不支持 Ossie 表达式，已跳过"));
                continue;
            }
            if (!PORTABLE_AGGREGATES.contains(atomic.get().getAggregateType().name())) {
                skipped.add(metricCode);
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN, "METRIC_SKIPPED", "指标 " + metricCode + " 的聚合不在可移植白名单内，已跳过"));
                continue;
            }
            ModelFieldEntity field = binding.get(metricId);
            if (field == null) {
                skipped.add(metricCode);
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN, "METRIC_SKIPPED", "指标 " + metricCode + " 的绑定字段不在导出范围内，已跳过"));
                continue;
            }
            projections.add(new SemanticMetricProjection(
                    atomic.get().getEnCode(),
                    atomic.get().getAggregateType().name() + "(" + field.getEnCode() + ")",
                    atomic.get().getDataType()));
        }
        return new MetricResolution(projections, skipped);
    }

    private String metricCode(String workspaceId, String metricId) {
        var atomic = atomicMetrics.find(workspaceId, metricId);
        if (atomic.isPresent()) return atomic.get().getEnCode();
        var derived = derivedMetrics.find(workspaceId, metricId);
        if (derived.isPresent()) return derived.get().getEnCode();
        return compositeMetrics
                .find(workspaceId, metricId)
                .map(metric -> metric.getEnCode())
                .orElse(metricId);
    }

    private String render(
            String workspaceId,
            Map<String, ModelEntity> datasetModels,
            Map<String, List<ModelFieldEntity>> datasetFields,
            Map<String, String> datasetNames,
            List<ModelingRelationEntity> relationRows,
            MetricResolution metricResolution) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# 由 Yak Ops 1.16.0 生成，规范版本固定 ").append(SPEC_VERSION).append('\n');
        yaml.append("# 指标导出策略：仅导出有唯一字段绑定的基础聚合，其余计入 custom_extensions\n");
        yaml.append("version: \"").append(SPEC_VERSION).append("\"\n");
        ModelEntity primary = datasetModels.values().iterator().next();
        yaml.append("name: ").append(quote(primary.getName())).append('\n');
        if (primary.getDescription() != null)
            yaml.append("description: ").append(quote(primary.getDescription())).append('\n');

        Map<String, List<String>> synonymsByField = synonymsByField(workspaceId, datasetFields);
        yaml.append("datasets:\n");
        for (Map.Entry<String, List<ModelFieldEntity>> entry : datasetFields.entrySet()) {
            String datasetId = entry.getKey();
            ModelEntity datasetModel = datasetModels.get(datasetId);
            yaml.append("  - name: ").append(quote(datasetNames.get(datasetId))).append('\n');
            yaml.append("    source: ")
                    .append(quote(source(workspaceId, datasetModel)))
                    .append('\n');
            List<ModelFieldEntity> fields = entry.getValue();
            List<String> primaryKey = new ArrayList<>();
            for (ModelFieldEntity field : fields) {
                if (Boolean.TRUE.equals(field.getIsPrimaryKey())) primaryKey.add(field.getEnCode());
            }
            if (!primaryKey.isEmpty()) {
                yaml.append("    primary_key: ").append(bracket(primaryKey)).append('\n');
            }
            if (datasetModel.getDescription() != null) {
                yaml.append("    description: ")
                        .append(quote(datasetModel.getDescription()))
                        .append('\n');
            }
            if (datasetModel.getGrainNote() != null) {
                yaml.append("    ai_context:\n");
                yaml.append("      instructions: ")
                        .append(quote("粒度：" + datasetModel.getGrainNote()))
                        .append('\n');
            }
            yaml.append("    fields:\n");
            for (ModelFieldEntity field : fields) {
                renderField(yaml, field, synonymsByField.get(fieldKey(field)));
            }
        }
        if (!relationRows.isEmpty()) {
            yaml.append("relationships:\n");
            for (ModelingRelationEntity relation : relationRows) {
                List<ModelingRelationColumnEntity> pairs = this.relations.columns(relation.getId());
                List<String> from = new ArrayList<>();
                List<String> to = new ArrayList<>();
                for (ModelingRelationColumnEntity pair : pairs) {
                    from.add(pair.getFromFieldCode());
                    to.add(pair.getToFieldCode());
                }
                yaml.append("  - name: ").append(quote(relation.getName())).append('\n');
                yaml.append("    from: ")
                        .append(quote(datasetNames.getOrDefault(relation.getFromModelId(), relation.getFromModelId())))
                        .append('\n');
                yaml.append("    to: ")
                        .append(quote(datasetNames.getOrDefault(relation.getToModelId(), relation.getToModelId())))
                        .append('\n');
                yaml.append("    from_columns: ").append(bracket(from)).append('\n');
                yaml.append("    to_columns: ").append(bracket(to)).append('\n');
            }
        }
        if (!metricResolution.metrics().isEmpty()) {
            yaml.append("metrics:\n");
            for (SemanticMetricProjection metric : metricResolution.metrics()) {
                yaml.append("  - name: ").append(quote(metric.name())).append('\n');
                yaml.append("    expression:\n");
                yaml.append("      dialects:\n");
                yaml.append("        - dialect: ANSI_SQL\n");
                yaml.append("          expression: ")
                        .append(quote(metric.expression()))
                        .append('\n');
                yaml.append("    datatype: ")
                        .append(datatype(metric.dataType()))
                        .append('\n');
            }
        }
        yaml.append("custom_extensions:\n");
        yaml.append("  - vendor_name: ").append(quote(VENDOR)).append('\n');
        yaml.append("    data: ")
                .append(quote(extensions(primary, metricResolution.skippedMetrics())))
                .append('\n');
        return yaml.toString();
    }

    private Map<String, List<String>> synonymsByField(
            String workspaceId, Map<String, List<ModelFieldEntity>> datasetFields) {
        List<String> keys = new ArrayList<>();
        for (List<ModelFieldEntity> fields : datasetFields.values()) {
            for (ModelFieldEntity field : fields) {
                keys.add(fieldKey(field));
            }
        }
        Map<String, List<String>> terms = new HashMap<>();
        for (ModelingSynonymEntity row : synonyms.listByTargets(workspaceId, MODEL_FIELD_SYNONYM, keys)) {
            terms.computeIfAbsent(row.getTargetId(), key -> new ArrayList<>()).add(row.getTerm());
        }
        return terms;
    }

    private void renderField(StringBuilder yaml, ModelFieldEntity field, List<String> synonymTerms) {
        yaml.append("      - name: ").append(quote(field.getEnCode())).append('\n');
        yaml.append("        expression:\n");
        yaml.append("          dialects:\n");
        yaml.append("            - dialect: ANSI_SQL\n");
        yaml.append("              expression: ")
                .append(quote(field.getEnCode()))
                .append('\n');
        yaml.append("        datatype: ").append(datatype(field.getDataType())).append('\n');
        yaml.append("        dimension:\n");
        yaml.append("          is_time: ")
                .append(Boolean.TRUE.equals(field.getIsTime()) ? "true" : "false")
                .append('\n');
        if (synonymTerms != null && !synonymTerms.isEmpty()) {
            yaml.append("        ai_context:\n");
            yaml.append("          synonyms: ").append(bracket(synonymTerms)).append('\n');
        }
    }

    private String source(String workspaceId, ModelEntity model) {
        List<ModelPublicationEntity> rows = publications.listByModel(workspaceId, model.getId(), 1);
        if (!rows.isEmpty()) {
            ModelPublicationEntity publication = rows.get(0);
            if ("SUCCEEDED".equals(publication.getStatus()) && publication.getPhysicalTable() != null) {
                List<String> parts = new ArrayList<>();
                if (publication.getPhysicalDatabase() != null
                        && !publication.getPhysicalDatabase().isBlank()) parts.add(publication.getPhysicalDatabase());
                if (publication.getPhysicalSchema() != null
                        && !publication.getPhysicalSchema().isBlank()) parts.add(publication.getPhysicalSchema());
                if (!publication.getPhysicalTable().isBlank()) parts.add(publication.getPhysicalTable());
                if (!parts.isEmpty()) return String.join(".", parts);
            }
        }
        String layerCode = layers.layer(workspaceId, model.getLayerId())
                .map(LayerEntity::getEnCode)
                .orElse(null);
        return layerCode == null || layerCode.isBlank() ? model.getName() : layerCode + "." + model.getName();
    }

    private String extensions(ModelEntity model, List<String> skippedMetrics) {
        StringBuilder json = new StringBuilder();
        json.append("{\"release\":\"1.16.0\"");
        json.append(",\"exportedAt\":\"").append(LocalDate.now()).append('"');
        json.append(",\"modelId\":\"").append(escape(model.getId())).append('"');
        if (model.getGrainNote() != null)
            json.append(",\"grainNote\":\"")
                    .append(escape(model.getGrainNote()))
                    .append('"');
        json.append(",\"skippedMetrics\":[");
        for (int index = 0; index < skippedMetrics.size(); index++) {
            if (index > 0) json.append(',');
            json.append('"').append(escape(skippedMetrics.get(index))).append('"');
        }
        json.append("]}");
        return json.toString();
    }

    private static Map<String, String> datasetNames(Map<String, ModelEntity> datasetModels) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, ModelEntity> entry : datasetModels.entrySet()) {
            String base = entry.getValue().getName();
            String candidate = base;
            int suffix = 2;
            while (!used.add(candidate)) {
                candidate = base + "-" + suffix;
                suffix++;
            }
            names.put(entry.getKey(), candidate);
        }
        return names;
    }

    private static String fieldKey(ModelFieldEntity field) {
        return field.getModelId() + ":" + field.getEnCode();
    }

    private static String datatype(DataType dataType) {
        if (dataType == null) return "String";
        return switch (dataType) {
            case STRING -> "String";
            case BIGINT -> "Integer";
            case DECIMAL -> "Decimal";
            case DATE -> "Date";
            case TIMESTAMP -> "DateTimeTz";
        };
    }

    private static ModelFieldEntity byCode(List<ModelFieldEntity> fields, String code) {
        for (ModelFieldEntity field : fields) {
            if (field.getEnCode().equals(code)) return field;
        }
        return null;
    }

    private static String bracket(List<String> values) {
        StringBuilder yaml = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) yaml.append(", ");
            yaml.append(quote(values.get(index)));
        }
        return yaml.append(']').toString();
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return '"' + escaped + '"';
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** One exported metric synthesized from an atomic metric and its single bound field. */
    private record SemanticMetricProjection(String name, String expression, DataType dataType) {}

    /** Exported metrics plus the codes of every metric the projection had to skip. */
    private record MetricResolution(List<SemanticMetricProjection> metrics, List<String> skippedMetrics) {}
}
