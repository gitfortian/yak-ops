package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.LayerService;
import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.model.impl.ModelServiceImpl;
import io.yak.ops.common.bean.dto.modeling.ModelFieldInputDTO;
import io.yak.ops.common.bean.dto.modeling.ModelInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelDTO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.StoragePolicy;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.repository.modeling.impl.DimensionRepositoryImpl;
import io.yak.ops.dao.repository.modeling.impl.ModelRepositoryImpl;
import io.yak.ops.dao.repository.modeling.impl.PlanningRepositoryImpl;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

/** Real MySQL model constraints, cascades, reference protection and service transactions. */
@Tag("modeling")
@Timeout(180)
class ModelDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static ModelService service;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);
    static final LayerService LAYERS = mock(LayerService.class);

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(
                metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class,
                ModelRepositoryImpl.class,
                ModelServiceImpl.class,
                PlanningRepositoryImpl.class,
                DimensionRepositoryImpl.class);
        service = context.getBean(ModelService.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        reset(WORKSPACES, LAYERS);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        when(LAYERS.options(anyString(), anyString()))
                .thenReturn(List.of(new LayerOptionVO("layer", "DWD", "明细层", "ds", "主库")));
        for (String table :
                List.of("model_field", "model", "dimension", "datamart", "subject", "process", "domain", "category", "layer"))
            jdbc.update("DELETE FROM yak_ops_modeling_" + table);
        jdbc.update("DELETE FROM yak_ops_standard_field");
        jdbc.update("DELETE FROM yak_ops_standard_category");
        jdbc.update("DELETE FROM yak_ops_standard_catalog");
        jdbc.update("DELETE FROM yak_ops_data_source");
        jdbc.update("DELETE FROM yak_ops_workspace");
        jdbc.update("DELETE FROM yak_ops_modeling_code");
        seedWorkspace("ws");
        seedWorkspace("other");
        seedPlanningGraph();
        seedStandard();
    }

    @AfterEach
    void cleanup() {
        // Later ITs clear layers and standards, which these rows hold alive through RESTRICT foreign keys.
        jdbc.update("DELETE FROM yak_ops_modeling_model_field");
        jdbc.update("DELETE FROM yak_ops_modeling_model");
    }

    @Test
    void databaseRejectsADetailTableWithoutAProcess() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','DWD','layer','cat',"
                        + "NULL,'domain',NULL,NULL,'dwd_raw','裸表','DAILY_FULL',30,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, models());
    }

    @Test
    void databaseRejectsAModelWithTwoOwners() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','DIM','layer','cat',"
                        + "NULL,'domain','mart',NULL,'dwd_raw','裸表','DAILY_FULL',30,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, models());
    }

    @Test
    void databaseRejectsALifecycleOutsideTheRange() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','DWD','layer','cat',"
                        + "'process','domain',NULL,NULL,'dwd_raw','裸表','DAILY_FULL',0,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw2','ws','DWD','layer','cat',"
                        + "'process','domain',NULL,NULL,'dwd_raw2','裸表','DAILY_FULL',36001,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, models());
    }

    @Test
    void databaseRejectsAnUnknownStoragePolicy() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','DWD','layer','cat',"
                        + "'process','domain',NULL,NULL,'dwd_raw','裸表','WEEKLY',30,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, models());
    }

    @Test
    void modelTypeColumnCarriesNoCheckConstraint() {
        // DWS/ADS are scheduled extensions; a CHECK would force a DDL change on every deployment.
        Integer matching = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.check_constraints cc "
                        + "JOIN information_schema.table_constraints tc "
                        + "ON tc.constraint_schema=cc.constraint_schema "
                        + "AND tc.constraint_name=cc.constraint_name "
                        + "WHERE cc.constraint_schema=DATABASE() AND tc.table_name='yak_ops_modeling_model' "
                        + "AND REPLACE(cc.check_clause,' ','') LIKE '%typein(%'",
                Integer.class);
        assertEquals(0, matching);
        Integer constraints = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.check_constraints cc "
                        + "JOIN information_schema.table_constraints tc "
                        + "ON tc.constraint_schema=cc.constraint_schema "
                        + "AND tc.constraint_name=cc.constraint_name "
                        + "WHERE cc.constraint_schema=DATABASE() AND tc.table_name='yak_ops_modeling_model'",
                Integer.class);
        assertTrue(constraints != null && constraints >= 5);
    }

    @Test
    void databaseRejectsAnApplicationTableWithABusinessProcess() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,domain_id,"
                        + "datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','ADS','layer','cat',"
                        + "'process',NULL,'mart',NULL,'ads_raw','应用表','DAILY_FULL',30,'it',NULL,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, models());
    }

    @Test
    void databaseKeepsTheMeasureFunctionInsideTheAllowedSet() {
        String modelId = createDetailModel("dwd_order_detail", 30, List.of());
        String insert = "INSERT INTO yak_ops_modeling_model_field(id,workspace_id,model_id,ordinal,en_code,cn_name,"
                + "data_type,length_precision,is_primary_key,is_not_null,is_partition,aggregate_type,standard_id,"
                + "description,create_time,update_time,create_by,update_by) VALUES(?, 'ws', ?, ?, ?, '金额',"
                + " 'DECIMAL', NULL, 0, 0, 0, ?, NULL, NULL, NOW(3), NOW(3), 'it', 'it')";

        jdbc.update(insert, "m1", modelId, 1, "amount", "SUM");
        assertEquals("SUM", jdbc.queryForObject(
                "SELECT aggregate_type FROM yak_ops_modeling_model_field WHERE id='m1'", String.class));

        // The value check keeps a hand-written row from carrying a function the application cannot read.
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, "m2", modelId, 2, "median", "MEDIAN"));
    }

    @Test
    void keepsFieldOrdinalsAndCodesUniquePerModel() {
        String modelId = createDetailModel("dwd_order_detail", 30, List.of());
        insertField("f1", modelId, 1, "order_id");
        assertThrows(DataAccessException.class, () -> insertField("f2", modelId, 1, "buyer_id"));
        assertThrows(DataAccessException.class, () -> insertField("f3", modelId, 2, "order_id"));
        assertEquals(1, fieldCount(modelId));
    }

    @Test
    void deletingAModelTakesItsFields() {
        String modelId = createDetailModel("dwd_order_detail", 30, detailFields());

        assertEquals(2, fieldCount(modelId));

        service.delete("ws", "actor", modelId, 1);

        assertEquals(0, models());
        assertEquals(0, fieldCount(modelId));
    }

    @Test
    void blocksDeletingADomainReferencedByAModel() {
        createDetailModel("dwd_order_detail", 30, List.of());

        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM yak_ops_modeling_domain WHERE workspace_id='ws' AND id='domain'"));
    }

    @Test
    void blocksDeletingAStandardReferencedByAField() {
        String modelId = createDetailModel("dwd_order_detail", 30, List.of());
        var field = new ModelFieldInputDTO();
        field.setCnName("订单号");
        field.setStandardId("std");
        service.update("ws", "actor", modelId, updateDetailModel("order_detail", 1, List.of(field)));

        assertEquals("order_id", jdbc.queryForObject(
                "SELECT en_code FROM yak_ops_modeling_model_field WHERE model_id=?", String.class, modelId));
        assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM yak_ops_standard_field WHERE workspace_id='ws' AND id='std'"));
    }

    @Test
    void keepsTheTableNameUniquePerWorkspace() {
        createDetailModel("order_detail", 30, List.of());

        var duplicate = assertThrows(BusinessException.class,
                () -> createDetailModel("order_detail", 30, List.of()));

        assertEquals(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME, duplicate.getErrorCode());
        assertEquals(1, models());
    }

    @Test
    void replacesFieldsAndAdvancesTheRevision() {
        String modelId = createDetailModel("dwd_order_detail", 30, detailFields());

        var saved = service.update("ws", "actor", modelId, updateDetailModel("order_detail", 1, List.of(
                field("order_id", DataType.BIGINT))));

        assertEquals(2, saved.revision());
        assertEquals(1, fieldCount(modelId));
        assertEquals(1, saved.fields().size());
        assertEquals("order_id", saved.fields().getFirst().enCode());
        var stale = assertThrows(BusinessException.class,
                () -> service.update("ws", "actor", modelId, updateDetailModel("order_detail", 1, List.of())));
        assertEquals(DimensionModelingErrorCode.REVISION_CONFLICT, stale.getErrorCode());
    }

    @Test
    void hidesModelsFromAnotherWorkspace() {
        String modelId = createDetailModel("dwd_order_detail", 30, List.of());

        var missing = assertThrows(BusinessException.class,
                () -> service.detail("other", "actor", modelId));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());
    }

    private String createDetailModel(String suffix, int lifecycleDays, List<ModelFieldInputDTO> fields) {
        var input = new ModelInputDTO();
        input.setType(ModelType.DWD);
        input.setLayerId("layer");
        input.setCategoryId("cat");
        input.setProcessId("process");
        input.setDomainId("domain");
        input.setNameSuffix(suffix);
        input.setCnName("订单明细");
        input.setStoragePolicy(StoragePolicy.DAILY_INCREMENTAL);
        input.setLifecycleDays(lifecycleDays);
        input.setFields(fields);
        return service.create("ws", "actor", input).id();
    }

    private static UpdateModelDTO updateDetailModel(
            String suffix, long revision, List<ModelFieldInputDTO> fields) {
        var input = new UpdateModelDTO();
        input.setType(ModelType.DWD);
        input.setLayerId("layer");
        input.setCategoryId("cat");
        input.setProcessId("process");
        input.setDomainId("domain");
        input.setNameSuffix(suffix);
        input.setCnName("订单明细");
        input.setStoragePolicy(StoragePolicy.DAILY_INCREMENTAL);
        input.setLifecycleDays(30);
        input.setRevision(revision);
        input.setFields(fields);
        return input;
    }

    private static List<ModelFieldInputDTO> detailFields() {
        return List.of(field("order_id", DataType.BIGINT), field("ds", DataType.STRING));
    }

    private static ModelFieldInputDTO field(String enCode, DataType dataType) {
        var field = new ModelFieldInputDTO();
        field.setEnCode(enCode);
        field.setCnName("字段");
        field.setDataType(dataType);
        return field;
    }

    private void insertField(String id, String modelId, int ordinal, String enCode) {
        jdbc.update("INSERT INTO yak_ops_modeling_model_field(id,workspace_id,model_id,ordinal,en_code,cn_name,"
                        + "data_type,length_precision,is_primary_key,is_not_null,is_partition,standard_id,description,"
                        + "create_time,update_time,create_by,update_by) VALUES(?,?,?,?,?,'字段','BIGINT',NULL,0,0,0,NULL,"
                        + "NULL,NOW(3),NOW(3),'it','it')",
                id, "ws", modelId, ordinal, enCode);
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
        jdbc.update("INSERT INTO yak_ops_modeling_datamart(id,workspace_id,name,en_name,code,owner_id,description,"
                + "revision,create_time,update_time,create_by,update_by) "
                + "VALUES('mart','ws','运营集市','Operating Mart','operating','it',NULL,1,NOW(3),NOW(3),'it','it')");
    }

    private void seedStandard() {
        jdbc.update("INSERT INTO yak_ops_standard_catalog(workspace_id,initialized_at,create_time,create_by) "
                + "VALUES('ws',NULL,NOW(3),'it')");
        jdbc.update("INSERT INTO yak_ops_standard_category(id,workspace_id,parent_id,name,revision,create_time,"
                + "update_time,create_by,update_by) VALUES('std-cat','ws',NULL,'交易',1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_standard_field(id,workspace_id,category_id,cn_name,en_code,data_type,"
                + "length_precision,is_not_null,security_level,owner_id,business_caliber,revision,create_time,"
                + "update_time,create_by,update_by) VALUES('std','ws','std-cat','订单号','order_id','BIGINT','20',1,"
                + "'INTERNAL','it',NULL,1,NOW(3),NOW(3),'it','it')");
    }

    private static int models() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_model", Integer.class);
    }

    private static int fieldCount(String modelId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_modeling_model_field WHERE model_id=?", Integer.class, modelId);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("io.yak.ops.dao.mapper.modeling")
    static class Beans {
        @Bean DataSource dataSource() {
            return source;
        }

        @Bean PlatformTransactionManager transactionManager() {
            return new JdbcTransactionManager(source);
        }

        @Bean WorkspaceService workspaceService() {
            return WORKSPACES;
        }

        @Bean LayerService layerService() {
            return LAYERS;
        }

        @Bean SqlSessionFactory sqlSessionFactory() throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }
    }
}
