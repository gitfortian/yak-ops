package io.yak.ops.business.modeling.semantic;

import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.common.bean.vo.modeling.SemanticExportCheckVO;
import io.yak.ops.common.bean.vo.modeling.SemanticExportVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationColumnEntity;
import io.yak.ops.dao.entity.modeling.ModelingRelationEntity;
import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.ModelingRelationRepository;
import io.yak.ops.dao.repository.modeling.ModelingSynonymRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.dao.repository.modeling.metric.MetricAtomicRepository;
import io.yak.ops.workspace.WorkspaceService;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
            AggregateType.SUM.name(), AggregateType.COUNT.name(), AggregateType.AVG.name(),
            AggregateType.MAX.name(), AggregateType.MIN.name());
    private static final String VENDOR = "yak-ops";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String LEVEL_WARN = "WARN";
    private static final String LEVEL_OK = "OK";

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
        ModelEntity model = models
                .find(workspaceId, modelId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        List<ModelFieldEntity> fields = models.fields(modelId);
        List<ModelingRelationEntity> relationRows = this.relations.listByModel(workspaceId, modelId);

        Map<String, List<ModelFieldEntity>> datasetFields = new LinkedHashMap<>();
        datasetFields.put(model.getId(), fields);
        for (ModelingRelationEntity relation : relationRows) {
            String otherId = model.getId().equals(relation.getFromModelId())
                    ? relation.getToModelId()
                    : relation.getFromModelId();
            datasetFields.computeIfAbsent(otherId, key -> models.fields(key));
        }

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
                            LEVEL_ERROR,
                            "FIELD_REMOVED",
                            "关系 " + relation.getName() + " 的关联键字段已移除，无法完成导出"));
                } else if (own.getStandardId() == null || other.getStandardId() == null) {
                    checks.add(new SemanticExportCheckVO(
                            LEVEL_WARN,
                            "RELATION_KEY_UNSTANDARDIZED",
                            "关系 " + relation.getName() + " 存在未绑定标准的关联键"));
                } else {
                    readyKeys++;
                }
            }
        }
        checks.add(new SemanticExportCheckVO(LEVEL_OK, "RELATIONS_READY", readyKeys + " 个关联键已绑标准，就绪"));

        List<SemanticMetricProjection> metrics = resolveMetrics(workspaceId, model.getId(), fields, checks);

        String yaml = render(workspaceId, model, relationRows, datasetFields, metrics);
        return new SemanticExportVO(checks, yaml);
    }

    private List<SemanticMetricProjection> resolveMetrics(
            String workspaceId,
            String modelId,
            List<ModelFieldEntity> fields,
            List<SemanticExportCheckVO> checks) {
        Map<String, ModelFieldEntity> binding = new HashMap<>();
        Set<String> metricIds = new LinkedHashSet<>();
        for (ModelFieldEntity field : fields) {
            if (field.getMetricId() != null) {
                binding.put(field.getMetricId(), field);
                metricIds.add(field.getMetricId());
            }
        }
        List<SemanticMetricProjection> projections = new ArrayList<>();
        for (String metricId : metricIds) {
            long bindings = models.countFieldsByMetric(workspaceId, metricId);
            if (bindings != 1) {
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN,
                        "METRIC_SKIPPED",
                        "指标 " + metricId + " 关联了 " + bindings + " 个模型字段，无法唯一确定数据来源，已跳过"));
                continue;
            }
            var atomic = atomicMetrics.find(workspaceId, metricId);
            if (atomic.isEmpty()) {
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN,
                        "METRIC_SKIPPED",
                        "指标 " + metricId + " 为派生或复合指标，暂不支持 Ossie 表达式，已跳过"));
                continue;
            }
            if (!PORTABLE_AGGREGATES.contains(atomic.get().getAggregateType().name())) {
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN,
                        "METRIC_SKIPPED",
                        "指标 " + metricId + " 的聚合不在可移植白名单内，已跳过"));
                continue;
            }
            ModelFieldEntity field = binding.get(metricId);
            if (field == null) {
                checks.add(new SemanticExportCheckVO(
                        LEVEL_WARN, "METRIC_SKIPPED", "指标 " + metricId + " 的绑定字段不在导出范围内，已跳过"));
                continue;
            }
            projections.add(new SemanticMetricProjection(
                    atomic.get().getEnCode(),
                    atomic.get().getAggregateType().name() + "(" + field.getEnCode() + ")",
                    atomic.get().getDataType()));
        }
        return projections;
    }

    private String render(
            String workspaceId,
            ModelEntity model,
            List<ModelingRelationEntity> relationRows,
            Map<String, List<ModelFieldEntity>> datasetFields,
            List<SemanticMetricProjection> metrics) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# 由 Yak Ops 1.16.0 生成，规范版本固定 ").append(SPEC_VERSION).append('\n');
        yaml.append("# 指标导出策略：仅导出有唯一字段绑定的基础聚合，其余计入 custom_extensions\n");
        yaml.append("version: \"").append(SPEC_VERSION).append("\"\n");
        yaml.append("name: ").append(quote(model.getName())).append('\n');
        if (model.getDescription() != null)
            yaml.append("description: ").append(quote(model.getDescription())).append('\n');
        if (model.getGrainNote() != null) {
            yaml.append("ai_context:\n");
            yaml.append("  instructions: ")
                    .append(quote("粒度：" + model.getGrainNote()))
                    .append('\n');
        }
        yaml.append("datasets:\n");
        List<String> datasetNames = new ArrayList<>(datasetFields.keySet());
        for (int index = 0; index < datasetNames.size(); index++) {
            String datasetId = datasetNames.get(index);
            List<ModelFieldEntity> fields = datasetFields.get(datasetId);
            yaml.append(index == 0 ? "  " : "  - ").append("name: ").append(quote(datasetId)).append('\n');
            if (index == 0) {
                yaml.append("    source: ").append(quote(source(workspaceId, model))).append('\n');
            } else {
                yaml.append("    source: ").append(quote(datasetId)).append('\n');
            }
            List<String> primaryKey = new ArrayList<>();
            for (ModelFieldEntity field : fields) {
                if (Boolean.TRUE.equals(field.getIsPrimaryKey())) primaryKey.add(field.getEnCode());
            }
            if (!primaryKey.isEmpty()) {
                yaml.append("    primary_key: ").append(bracket(primaryKey)).append('\n');
            }
            yaml.append("    fields:\n");
            for (ModelFieldEntity field : fields) {
                renderField(yaml, field);
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
                yaml.append("    from: ").append(quote(relation.getFromModelId())).append('\n');
                yaml.append("    to: ").append(quote(relation.getToModelId())).append('\n');
                yaml.append("    from_columns: ").append(bracket(from)).append('\n');
                yaml.append("    to_columns: ").append(bracket(to)).append('\n');
            }
        }
        if (!metrics.isEmpty()) {
            yaml.append("metrics:\n");
            for (SemanticMetricProjection metric : metrics) {
                yaml.append("  - name: ").append(quote(metric.name())).append('\n');
                yaml.append("    expression:\n");
                yaml.append("      dialects:\n");
                yaml.append("        - dialect: ANSI_SQL\n");
                yaml.append("          expression: ").append(quote(metric.expression())).append('\n');
                yaml.append("    datatype: ").append(datatype(metric.dataType())).append('\n');
            }
        }
        yaml.append("custom_extensions:\n");
        yaml.append("  - vendor_name: ").append(quote(VENDOR)).append('\n');
        yaml.append("    data: ").append(quote(extensions(model))).append('\n');
        return yaml.toString();
    }

    private void renderField(StringBuilder yaml, ModelFieldEntity field) {
        yaml.append("      - name: ").append(quote(field.getEnCode())).append('\n');
        yaml.append("        expression:\n");
        yaml.append("          dialects:\n");
        yaml.append("            - dialect: ANSI_SQL\n");
        yaml.append("              expression: ").append(quote(field.getEnCode())).append('\n');
        yaml.append("        datatype: ").append(datatype(field.getDataType())).append('\n');
        yaml.append("        dimension:\n");
        yaml.append("          is_time: ")
                .append(Boolean.TRUE.equals(field.getIsTime()) ? "true" : "false")
                .append('\n');
        List<ModelingSynonymEntity> synonymRows = synonyms.listByTarget(
                field.getWorkspaceId(),
                "MODEL_FIELD",
                field.getModelId() + ":" + field.getEnCode());
        if (!synonymRows.isEmpty()) {
            List<String> terms = new ArrayList<>();
            for (ModelingSynonymEntity row : synonymRows) {
                terms.add(row.getTerm());
            }
            yaml.append("        ai_context:\n");
            yaml.append("          synonyms: ").append(bracket(terms)).append('\n');
        }
    }

    private String source(String workspaceId, ModelEntity model) {
        List<ModelPublicationEntity> rows = publications.listByModel(workspaceId, model.getId(), 1);
        if (rows.isEmpty()) return model.getName();
        ModelPublicationEntity publication = rows.get(0);
        if (publication.getPhysicalTable() == null) return model.getName();
        return publication.getPhysicalDatabase() + "." + publication.getPhysicalSchema() + "."
                + publication.getPhysicalTable();
    }

    private String extensions(ModelEntity model) {
        StringBuilder json = new StringBuilder();
        json.append("{\"release\":\"1.16.0\"");
        json.append(",\"exportedAt\":\"").append(LocalDate.now()).append('"');
        json.append(",\"modelId\":\"").append(escape(model.getId())).append('"');
        json.append('}');
        return json.toString();
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
}
