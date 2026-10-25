package io.yak.ops.business.modeling.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.metric.impl.MetricAtomicServiceImpl;
import io.yak.ops.business.modeling.metric.impl.MetricCompositeServiceImpl;
import io.yak.ops.business.modeling.metric.impl.MetricDerivedServiceImpl;
import io.yak.ops.business.modeling.metric.impl.MetricModifierServiceImpl;
import io.yak.ops.business.modeling.metric.impl.MetricTimePeriodServiceImpl;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodInputDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.enums.modeling.metric.MetricReferenceKind;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.ErrorCode;
import io.yak.ops.dao.repository.modeling.impl.PlanningRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricAtomicRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricCompositeRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricDerivedRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricModifierRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricReferenceRepositoryImpl;
import io.yak.ops.dao.repository.modeling.metric.impl.MetricTimePeriodRepositoryImpl;
import io.yak.ops.dao.repository.modeling.standard.impl.StandardMeasureUnitRepositoryImpl;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Real MySQL metric constraints: Workspace-scoped codes, composed-code collisions, the restrict
 * graph between the five kinds, the model-field link and the reference panel.
 */
@Tag("modeling")
@Timeout(180)
class MetricDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static MetricTimePeriodService periodsService;
    static MetricModifierService modifiersService;
    static MetricAtomicService atomicsService;
    static MetricDerivedService derivedService;
    static MetricCompositeService compositesService;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(
                metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class,
                MetricCatalog.class,
                MetricTimePeriodRepositoryImpl.class,
                MetricModifierRepositoryImpl.class,
                MetricAtomicRepositoryImpl.class,
                MetricDerivedRepositoryImpl.class,
                MetricCompositeRepositoryImpl.class,
                MetricReferenceRepositoryImpl.class,
                MetricTimePeriodServiceImpl.class,
                MetricModifierServiceImpl.class,
                MetricAtomicServiceImpl.class,
                MetricDerivedServiceImpl.class,
                MetricCompositeServiceImpl.class,
                PlanningRepositoryImpl.class,
                StandardMeasureUnitRepositoryImpl.class);
        periodsService = context.getBean(MetricTimePeriodService.class);
        modifiersService = context.getBean(MetricModifierService.class);
        atomicsService = context.getBean(MetricAtomicService.class);
        derivedService = context.getBean(MetricDerivedService.class);
        compositesService = context.getBean(MetricCompositeService.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        reset(WORKSPACES);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        // Model fields cite derived metrics with RESTRICT, so they go before the metric tables.
        jdbc.update("DELETE FROM yak_ops_modeling_model_field");
        // Rows left by other ITs RESTRICT the planning tables below: models cite every planning
        // parent, subjects cite datamarts, dimensions cite domain and datamart, and publications
        // trail models.
        jdbc.update("DELETE FROM yak_ops_modeling_publication");
        jdbc.update("DELETE FROM yak_ops_modeling_model");
        jdbc.update("DELETE FROM yak_ops_modeling_subject");
        jdbc.update("DELETE FROM yak_ops_modeling_dimension");
        for (String table : List.of(
                "metric_composite_ref",
                "metric_composite",
                "metric_derived_modifier",
                "metric_derived",
                "metric_atomic",
                "metric_modifier",
                "metric_time_period"))
            jdbc.update("DELETE FROM yak_ops_modeling_" + table);
        // Planning rows keep metric rows alive through RESTRICT foreign keys, so they go after.
        for (String table : List.of("process", "domain", "category", "layer", "datamart"))
            jdbc.update("DELETE FROM yak_ops_modeling_" + table);
        jdbc.update("DELETE FROM yak_ops_modeling_code");
        jdbc.update("DELETE FROM yak_ops_data_source");
        jdbc.update("DELETE FROM yak_ops_workspace WHERE id IN ('ws','other')");
        seedWorkspace("ws");
        seedWorkspace("other");
        seedPlanningGraph();
    }

    @Test
    void keepsPeriodCodesWorkspaceScopedAndRejectsAnIllegalCodeAtTheDatabase() {
        periodsService.create("ws", "actor", period("d7", "最近7天"));
        periodsService.create("other", "actor", period("d7", "最近7天"));
        assertEquals(1, periodsService.page("ws", "actor", null, 1, 20).total());
        assertEquals(1, periodsService.page("other", "actor", null, 1, 20).total());

        // The V6 CHECK enforces the code grammar below the application, like every other kind.
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_metric_time_period(id,workspace_id,en_code,cn_name,revision,"
                        + "create_time,update_time,create_by,update_by) "
                        + "VALUES('bad','ws','D7','大写',1,NOW(3),NOW(3),'it','it')"));
    }

    @Test
    void composesTheStoredCodeAndCollidesOnIdenticalParts() {
        seedMetricParts("ws");
        var first = derivedService.create("ws", "actor", derived("atomic", "period", List.of("muying")));
        assertEquals("d7_muying_pay_amt", derivedService.detail("ws", "actor", first.id()).enCode());
        assertFalse(derivedService.available("ws", "actor", "atomic", "period", List.of("muying"), null));

        var duplicate = assertThrows(
                BusinessException.class,
                () -> derivedService.create("ws", "actor", derived("atomic", "period", List.of("muying"))));
        assertEquals(MetricErrorCode.DUPLICATE_CODE, duplicate.getErrorCode());

        modifiersService.create("ws", "actor", modifier("vip"));
        var update = new MetricDerivedUpdateDTO();
        update.setAtomicMetricId("atomic");
        update.setPeriodId("period");
        update.setModifierIds(List.of("vip"));
        update.setRevision(first.revision());
        var edited = derivedService.update("ws", "actor", first.id(), update);
        assertEquals(2, edited.revision());
        var stored = derivedService.detail("ws", "actor", first.id());
        assertEquals("d7_vip_pay_amt", stored.enCode());
        assertEquals(1, stored.modifiers().size());
        assertEquals("vip", stored.modifiers().get(0).modifierEnCode());

        var stale = assertThrows(BusinessException.class, () -> derivedService.update("ws", "actor", first.id(), update));
        assertEquals(MetricErrorCode.REVISION_CONFLICT, stale.getErrorCode());
    }

    @Test
    void protectsThePartsAndCascadesTheLinksWithTheDerivedMetric() {
        seedMetricParts("ws");
        var derived = derivedService.create("ws", "actor", derived("atomic", "period", List.of("muying")));

        assertEquals(MetricErrorCode.REFERENCED, referenced(() -> modifiersService.delete("ws", "actor", "muying", 1)));
        assertEquals(MetricErrorCode.REFERENCED, referenced(() -> atomicsService.delete("ws", "actor", "atomic", 1)));
        assertEquals(MetricErrorCode.REFERENCED, referenced(() -> periodsService.delete("ws", "actor", "period", 1)));

        derivedService.delete("ws", "actor", derived.id(), derived.revision());
        assertEquals(0, count("metric_derived_modifier"));
        assertEquals(0, count("metric_derived"));
        modifiersService.delete("ws", "actor", "muying", 1);
        atomicsService.delete("ws", "actor", "atomic", 1);
        periodsService.delete("ws", "actor", "period", 1);
        assertEquals(0, count("metric_modifier"));
    }

    @Test
    void answersTheReferencePanelAndRestrictsTheDerivedMetricWhileReferenced() {
        seedMetricParts("ws");
        var derived = derivedService.create("ws", "actor", derived("atomic", "period", List.of()));
        var composite = compositesService.create("ws", "actor", composite("growth", "pay_amt * 100"));

        var panel = derivedService.references("ws", "actor", derived.id());
        assertEquals(1L, panel.count());
        assertEquals(MetricReferenceKind.COMPOSITE_METRIC, panel.items().get(0).kind());
        assertEquals("growth", panel.items().get(0).path());

        assertEquals(
                MetricErrorCode.REFERENCED,
                referenced(() -> derivedService.delete("ws", "actor", derived.id(), derived.revision())));

        compositesService.delete("ws", "actor", composite.id(), composite.revision());
        assertEquals(0, count("metric_composite_ref"));
        derivedService.delete("ws", "actor", derived.id(), derived.revision());
        assertEquals(0, count("metric_derived"));
    }

    @Test
    void citesTheDerivedMetricFromAModelFieldAndProtectsItThere() {
        seedMetricParts("ws");
        var derived = derivedService.create("ws", "actor", derived("atomic", "period", List.of()));
        jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('model','ws','DWD','layer','cat',"
                        + "'process','domain',NULL,NULL,'dwd_trade_order','交易明细','DAILY_FULL',30,'it',NULL,1,NOW(3),NOW(3),'it','it')");
        jdbc.update(
                "INSERT INTO yak_ops_modeling_model_field(id,workspace_id,model_id,ordinal,en_code,cn_name,data_type,"
                        + "length_precision,is_primary_key,is_not_null,is_partition,standard_id,description,metric_id,"
                        + "create_time,update_time,create_by,update_by) VALUES('field','ws','model',1,'pay_amt','支付金额',"
                        + "'DECIMAL','18,2',0,0,0,NULL,NULL,?,NOW(3),NOW(3),'it','it')",
                derived.id());

        var panel = derivedService.references("ws", "actor", derived.id());
        assertEquals(1L, panel.count());
        assertEquals(MetricReferenceKind.MODEL_FIELD, panel.items().get(0).kind());
        assertEquals("pay_amt", panel.items().get(0).name());
        assertEquals("dwd_trade_order.pay_amt", panel.items().get(0).path());

        assertEquals(
                MetricErrorCode.REFERENCED,
                referenced(() -> derivedService.delete("ws", "actor", derived.id(), derived.revision())));
        jdbc.update("DELETE FROM yak_ops_modeling_model_field WHERE id='field'");
        derivedService.delete("ws", "actor", derived.id(), derived.revision());
    }

    @Test
    void hidesOtherWorkspaceMetricsAndEnforcesMembership() {
        seedMetricParts("ws");
        var derived = derivedService.create("ws", "actor", derived("atomic", "period", List.of()));

        var missing = assertThrows(BusinessException.class, () -> derivedService.detail("other", "actor", derived.id()));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());

        when(WORKSPACES.isActiveMember("other", "actor")).thenReturn(false);
        var denied = assertThrows(BusinessException.class, () -> derivedService.page("other", "actor", null, null, 1, 20));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, denied.getErrorCode());
    }

    private static ErrorCode referenced(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected the deletion to be refused");
        } catch (BusinessException failure) {
            return failure.getErrorCode();
        }
    }

    private static int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_" + table, Integer.class);
    }

    private static MetricTimePeriodInputDTO period(String enCode, String cnName) {
        var input = new MetricTimePeriodInputDTO();
        input.setEnCode(enCode);
        input.setCnName(cnName);
        return input;
    }

    private static MetricModifierInputDTO modifier(String enCode) {
        var input = new MetricModifierInputDTO();
        input.setDomainId("domain");
        input.setEnCode(enCode);
        input.setCnName("muying".equals(enCode) ? "母婴" : "会员");
        return input;
    }

    private static MetricAtomicInputDTO atomic() {
        var input = new MetricAtomicInputDTO();
        input.setProcessId("process");
        input.setEnCode("pay_amt");
        input.setCnName("支付金额");
        input.setDataType(DataType.DECIMAL);
        input.setAggregateType(AggregateType.SUM);
        return input;
    }

    private static MetricDerivedInputDTO derived(String atomicId, String periodId, List<String> modifierIds) {
        var input = new MetricDerivedInputDTO();
        input.setAtomicMetricId(atomicId);
        input.setPeriodId(periodId);
        input.setModifierIds(modifierIds);
        return input;
    }

    private static MetricCompositeInputDTO composite(String enCode, String expression) {
        var input = new MetricCompositeInputDTO();
        input.setEnCode(enCode);
        input.setCnName("增长率");
        input.setExpression(expression);
        return input;
    }

    private void seedMetricParts(String workspace) {
        periodsService.create(workspace, "actor", period("d7", "最近7天"));
        modifiersService.create(workspace, "actor", modifier("muying"));
        atomicsService.create(workspace, "actor", atomic());
    }

    private static void seedWorkspace(String id) {
        jdbc.update("INSERT INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) "
                + "VALUES(?,?,NOW(3),NOW(3),'it','it')", id, id);
    }

    private void seedPlanningGraph() {
        jdbc.update("INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,conn_status,"
                + "remark,connection_params,original_json,create_time,update_time,create_by,update_by) "
                + "VALUES('ds','ws','主仓','MYSQL','jdbc:mysql://127.0.0.1:3306/demo',2,1,NULL,'{}','{}',"
                + "NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,description,"
                + "enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by) "
                + "VALUES('layer','ws','DWD','明细层','ds',NULL,1,0,1000,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_category(id,workspace_id,name,code,owner_id,description,revision,"
                + "create_time,update_time,create_by,update_by) VALUES('cat','ws','电商业务','commerce','it',NULL,1,"
                + "NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_domain(id,workspace_id,category_id,name,code,owner_id,description,"
                + "revision,create_time,update_time,create_by,update_by) "
                + "VALUES('domain','ws','cat','交易域','trade','it',NULL,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_process(id,workspace_id,domain_id,name,code,owner_id,description,"
                + "revision,create_time,update_time,create_by,update_by) "
                + "VALUES('process','ws','domain','下单','order','it',NULL,1,NOW(3),NOW(3),'it','it')");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("io.yak.ops.dao.mapper.modeling")
    static class Beans {
        @Bean
        DataSource dataSource() {
            return source;
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new JdbcTransactionManager(source);
        }

        @Bean
        WorkspaceService workspaceService() {
            return WORKSPACES;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }
    }
}
