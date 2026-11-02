package io.yak.ops.business.modeling.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.yak.ops.common.bean.vo.modeling.SemanticExportCheckVO;
import io.yak.ops.common.bean.vo.modeling.SemanticExportVO;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Golden checks of the Ossie projection: pinned spec version, code-based key references, the
 * aggregate whitelist and the skip-and-declare metric policy.
 */
class OssieSemanticExporterTest {
    private static final String WS = "ws-1";
    private static final String ACTOR = "u-1";
    private static final String MODEL_ID = "m-fact";

    private ModelRepository models;
    private ModelingRelationRepository relations;
    private ModelingSynonymRepository synonyms;
    private PublicationRepository publications;
    private MetricAtomicRepository atomicMetrics;
    private OssieSemanticExporter exporter;

    @BeforeEach
    void setUp() {
        models = Mockito.mock(ModelRepository.class);
        relations = Mockito.mock(ModelingRelationRepository.class);
        synonyms = Mockito.mock(ModelingSynonymRepository.class);
        publications = Mockito.mock(PublicationRepository.class);
        atomicMetrics = Mockito.mock(MetricAtomicRepository.class);
        WorkspaceService workspaces = Mockito.mock(WorkspaceService.class);
        when(workspaces.isActiveMember(anyString(), anyString())).thenReturn(true);
        exporter = new OssieSemanticExporter();
        inject("models", models);
        inject("relations", relations);
        inject("synonyms", synonyms);
        inject("publications", publications);
        inject("atomicMetrics", atomicMetrics);
        inject("workspaces", workspaces);
    }

    private void inject(String field, Object value) {
        try {
            var f = OssieSemanticExporter.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(exporter, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ModelEntity model(String id, String type, String name) {
        ModelEntity row = new ModelEntity();
        row.setId(id);
        row.setWorkspaceId(WS);
        row.setType(type);
        row.setName(name);
        row.setCnName(name);
        row.setGrainNote("一行 = 一个订单明细行");
        row.setRevision(1L);
        return row;
    }

    private static ModelFieldEntity field(String modelId, String code, String standardId, boolean time) {
        ModelFieldEntity row = new ModelFieldEntity();
        row.setWorkspaceId(WS);
        row.setModelId(modelId);
        row.setEnCode(code);
        row.setCnName(code);
        row.setDataType(DataType.BIGINT);
        row.setIsPrimaryKey("order_id".equals(code));
        row.setIsTime(time);
        row.setStandardId(standardId);
        return row;
    }

    @Test
    void rendersPinnedVersionKeysTimeRoleAndSynonyms() {
        ModelEntity fact = model(MODEL_ID, "DWD", "dwd_order_detail");
        ModelEntity dim = model("m-dim", "DIM", "dim_customer");
        when(models.find(WS, MODEL_ID)).thenReturn(Optional.of(fact));
        when(models.find(WS, "m-dim")).thenReturn(Optional.of(dim));
        when(models.fields(MODEL_ID))
                .thenReturn(List.of(field(MODEL_ID, "order_id", "std-1", false), field(MODEL_ID, "biz_date_sk", "std-2", true)));
        when(models.fields("m-dim")).thenReturn(List.of(field("m-dim", "customer_id", "std-3", false)));
        ModelingRelationEntity relation = new ModelingRelationEntity();
        relation.setId("rel-1");
        relation.setName("order_to_customer");
        relation.setFromModelId(MODEL_ID);
        relation.setToModelId("m-dim");
        when(relations.listByModel(WS, MODEL_ID)).thenReturn(List.of(relation));
        ModelingRelationColumnEntity pair = new ModelingRelationColumnEntity();
        pair.setFromFieldCode("order_id");
        pair.setToFieldCode("customer_id");
        when(relations.columns("rel-1")).thenReturn(List.of(pair));
        ModelingSynonymEntity synonym = new ModelingSynonymEntity();
        synonym.setTerm("订单号");
        when(synonyms.listByTarget(WS, "MODEL_FIELD", MODEL_ID + ":order_id"))
                .thenReturn(List.of(synonym));
        when(publications.listByModel(WS, MODEL_ID, 1)).thenReturn(List.of());
        when(models.countFieldsByMetric(anyString(), anyString())).thenReturn(0L);

        SemanticExportVO vo = exporter.export(WS, ACTOR, MODEL_ID);

        String yaml = vo.yaml();
        assertTrue(yaml.startsWith("# 由 Yak Ops 1.16.0 生成"), yaml);
        assertTrue(yaml.contains("version: \"0.2.0.dev0\""));
        assertTrue(yaml.contains("source: \"dwd_order_detail\""));
        assertTrue(yaml.contains("primary_key: [\"order_id\"]"));
        assertTrue(yaml.contains("is_time: true"));
        assertTrue(yaml.contains("is_time: false"));
        assertTrue(yaml.contains("synonyms: [\"订单号\"]"));
        assertTrue(yaml.contains("from_columns: [\"order_id\"]"), yaml);
        assertTrue(yaml.contains("to_columns: [\"customer_id\"]"));
        assertTrue(yaml.contains("vendor_name: \"yak-ops\""));
        assertTrue(vo.checks().stream().anyMatch(check -> "OK".equals(check.level())));
    }

    @Test
    void synthesizesOnlyWhitelistedUniqueBoundMetrics() {
        ModelEntity fact = model(MODEL_ID, "DWD", "dwd_order_detail");
        when(models.find(WS, MODEL_ID)).thenReturn(Optional.of(fact));
        ModelFieldEntity bound = field(MODEL_ID, "paid_amount", "std-1", false);
        bound.setMetricId("metric-1");
        when(models.fields(MODEL_ID)).thenReturn(List.of(bound));
        when(relations.listByModel(WS, MODEL_ID)).thenReturn(List.of());
        when(publications.listByModel(WS, MODEL_ID, 1)).thenReturn(List.of());
        when(models.countFieldsByMetric(WS, "metric-1")).thenReturn(1L);
        io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity atomic =
                new io.yak.ops.dao.entity.modeling.metric.MetricAtomicEntity();
        atomic.setEnCode("paid_amount_sum");
        atomic.setAggregateType(AggregateType.SUM);
        atomic.setDataType(DataType.DECIMAL);
        when(atomicMetrics.find(WS, "metric-1")).thenReturn(Optional.of(atomic));

        SemanticExportVO vo = exporter.export(WS, ACTOR, MODEL_ID);

        assertTrue(vo.yaml().contains("name: \"paid_amount_sum\""));
        assertTrue(vo.yaml().contains("expression: \"SUM(paid_amount)\""));
        assertTrue(vo.yaml().contains("datatype: Decimal"));
    }

    @Test
    void skipsDerivedMetricsAndDeclaresThem() {
        ModelEntity fact = model(MODEL_ID, "DWD", "dwd_order_detail");
        when(models.find(WS, MODEL_ID)).thenReturn(Optional.of(fact));
        ModelFieldEntity bound = field(MODEL_ID, "paid_amount", "std-1", false);
        bound.setMetricId("metric-derived");
        when(models.fields(MODEL_ID)).thenReturn(List.of(bound));
        when(relations.listByModel(WS, MODEL_ID)).thenReturn(List.of());
        when(publications.listByModel(WS, MODEL_ID, 1)).thenReturn(List.of());
        when(models.countFieldsByMetric(WS, "metric-derived")).thenReturn(1L);
        when(atomicMetrics.find(WS, "metric-derived")).thenReturn(Optional.empty());

        SemanticExportVO vo = exporter.export(WS, ACTOR, MODEL_ID);

        assertEquals(true, vo.yaml().contains("METRIC_SKIPPED") || vo.yaml().contains("custom_extensions"));
        List<SemanticExportCheckVO> warns =
                vo.checks().stream().filter(check -> "METRIC_SKIPPED".equals(check.code())).toList();
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).message().contains("派生或复合指标"));
    }

    @Test
    void usesPublishedThreePartSourceWhenAvailable() {
        ModelEntity fact = model(MODEL_ID, "DWD", "dwd_order_detail");
        when(models.find(WS, MODEL_ID)).thenReturn(Optional.of(fact));
        when(models.fields(MODEL_ID)).thenReturn(List.of());
        when(relations.listByModel(WS, MODEL_ID)).thenReturn(List.of());
        ModelPublicationEntity publication = new ModelPublicationEntity();
        publication.setPhysicalDatabase("lake");
        publication.setPhysicalSchema("dws");
        publication.setPhysicalTable("dwd_order_detail");
        when(publications.listByModel(WS, MODEL_ID, 1)).thenReturn(List.of(publication));
        when(models.countFieldsByMetric(anyString(), anyString())).thenReturn(0L);

        SemanticExportVO vo = exporter.export(WS, ACTOR, MODEL_ID);

        assertTrue(vo.yaml().contains("source: \"lake.dws.dwd_order_detail\""));
    }
}
