package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.impl.LayerServiceImpl;
import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.impl.LayerRepositoryImpl;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/** Real MySQL preset seeding, Workspace-unique abbreviations, binding rules and revision contracts. */
@Tag("modeling")
@Timeout(180)
class LayerDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static LayerService service;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(
                metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class, LayerRepositoryImpl.class, LayerServiceImpl.class);
        service = context.getBean(LayerService.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        reset(WORKSPACES);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        jdbc.update("DELETE FROM yak_ops_modeling_layer");
        jdbc.update("DELETE FROM yak_ops_data_source");
        jdbc.update("DELETE FROM yak_ops_workspace WHERE id IN ('ws','other')");
        for (String workspace : List.of("ws", "other")) {
            jdbc.update(
                    "INSERT INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) VALUES(?,?,NOW(3),NOW(3),'it','it')",
                    workspace,
                    workspace);
        }
    }

    @Test
    void seedsOneSetOfPresetsUnderConcurrentFirstReads() throws Exception {
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> read = () -> {
                start.await();
                return service.list("ws", "actor").size();
            };
            var first = workers.submit(read);
            var second = workers.submit(read);
            start.countDown();
            assertEquals(5, first.get(30, TimeUnit.SECONDS));
            assertEquals(5, second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(5, count("ws"));
        assertEquals(5, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_modeling_layer WHERE builtin=1 AND data_source_id IS NULL", Integer.class));
    }

    @Test
    void returnsPresetsInWarehouseOrderEveryRead() {
        assertEquals(
                List.of("ODS", "DWD", "DWS", "ADS", "DIM"),
                service.list("ws", "actor").stream().map(row -> row.enCode()).toList());
        assertEquals(5, service.list("ws", "actor").size());
        assertTrue(service.list("ws", "actor").stream().allMatch(row -> row.builtin() && !row.enabled()));
        assertTrue(service.options("ws", "actor").isEmpty());
    }

    @Test
    void keepsPresetIdentityImmutableButAllowsBinding() {
        var ods = preset("ODS");
        var rename = assertThrows(
                BusinessException.class, () -> service.update("ws", "actor", ods, update("贴源改", "ODS", null, false, 1)));
        assertEquals(LayerErrorCode.BUILTIN_IMMUTABLE, rename.getErrorCode());

        var codes = assertThrows(
                BusinessException.class, () -> service.update("ws", "actor", ods, update("贴源层", "ODS_ALT", null, false, 1)));
        assertEquals(LayerErrorCode.BUILTIN_IMMUTABLE, codes.getErrorCode());

        String datasource = datasource("ws", "ds-1");
        var bound = service.update("ws", "actor", ods, update("贴源层", "ODS", datasource, true, 1));
        assertEquals(2, bound.revision());
        assertEquals(datasource, bound.dataSourceId());
        assertEquals("主仓", bound.dataSourceName());
        assertTrue(bound.enabled());
        assertEquals(1, service.options("ws", "actor").size());

        var deletion = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", ods, 2));
        assertEquals(LayerErrorCode.BUILTIN_UNDELETABLE, deletion.getErrorCode());
        assertEquals(5, count("ws"));
    }

    @Test
    void enforcesUniqueAbbreviationInsideTheWorkspace() {
        assertEquals(5, service.list("ws", "actor").size());
        var created = service.create("ws", "actor", input("宽表层", "dwd_wide", null, false));
        assertEquals("DWD_WIDE", created.enCode());
        assertFalse(created.builtin());
        assertEquals(1000, created.sortNo());

        var duplicate = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("重复层", "DWD_WIDE", null, false)));
        assertEquals(LayerErrorCode.DUPLICATE_CODE, duplicate.getErrorCode());
        assertEquals(6, count("ws"));

        assertFalse(service.available("ws", "actor", "DWD_WIDE", null));
        assertTrue(service.available("ws", "actor", "DWD_WIDE", created.id()));
        assertTrue(service.available("ws", "actor", "ODS_FREE", null));
        assertFalse(service.available("ws", "actor", "ods", null));
        assertEquals(6, service.list("ws", "actor").size());
    }

    @Test
    void requiresABoundDatasourceBeforeEnabling() {
        var unbound = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("无源层", "NO_SRC", null, true)));
        assertEquals(LayerErrorCode.DATASOURCE_REQUIRED, unbound.getErrorCode());

        var missing = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("越权层", "CROSS", "ds-other", false)));
        assertEquals(LayerErrorCode.INVALID_DATASOURCE, missing.getErrorCode());

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "INSERT INTO yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,description,enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','RAW','裸层',NULL,NULL,1,0,1000,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, count("ws"));
    }

    @Test
    void rejectsAbbreviationsThatBreakTheDatabaseContract() {
        var invalid = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("非法层", "2_LAYER", null, false)));
        assertEquals(LayerErrorCode.INVALID_CODE, invalid.getErrorCode());

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update(
                        "INSERT INTO yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,description,enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by) VALUES('raw','ws','lower','小写层',NULL,NULL,0,0,1000,1,NOW(3),NOW(3),'it','it')"));
        assertEquals(0, count("ws"));
    }

    @Test
    void advancesRevisionAndRejectsStaleWrites() {
        assertEquals(5, service.list("ws", "actor").size());
        var created = service.create("ws", "actor", input("临时层", "TMP", null, false));
        assertEquals(2, service.update("ws", "actor", created.id(), update("临时层改", "TMP", null, false, 1))
                .revision());
        assertEquals(3, service.update("ws", "actor", created.id(), update("临时层再改", "TMP", null, false, 2))
                .revision());
        assertEquals(
                3L,
                jdbc.queryForObject("SELECT revision FROM yak_ops_modeling_layer WHERE id=?", Long.class, created.id()));

        var stale = assertThrows(
                BusinessException.class,
                () -> service.update("ws", "actor", created.id(), update("过期层", "TMP", null, false, 1)));
        assertEquals(LayerErrorCode.REVISION_CONFLICT, stale.getErrorCode());
        var staleDelete = assertThrows(
                BusinessException.class, () -> service.delete("ws", "actor", created.id(), 1));
        assertEquals(LayerErrorCode.REVISION_CONFLICT, staleDelete.getErrorCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_modeling_layer WHERE id=? AND revision=3", Integer.class, created.id()));

        service.delete("ws", "actor", created.id(), 3);
        assertEquals(5, count("ws"));
        var missing = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", created.id(), 3));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());
    }

    @Test
    void blocksDeletingADatasourceBoundToALayer() {
        String datasource = datasource("ws", "ds-bound");
        var layer = service.create("ws", "actor", input("绑定层", "BOUND", datasource, true));
        assertEquals("主仓", layer.dataSourceName());

        assertThrows(
                DataAccessException.class,
                () -> jdbc.update("DELETE FROM yak_ops_data_source WHERE id=?", datasource));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_data_source WHERE id=?", Integer.class, datasource));

        service.delete("ws", "actor", layer.id(), 1);
        jdbc.update("DELETE FROM yak_ops_data_source WHERE id=?", datasource);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_data_source", Integer.class));
    }

    @Test
    void hidesOtherWorkspaceLayersAndRejectsNonMembers() {
        var custom = service.create("ws", "actor", input("本地层", "LOCAL", null, false));
        assertTrue(service.list("other", "actor").stream().noneMatch(row -> row.id().equals(custom.id())));
        assertTrue(service.options("other", "actor").isEmpty());
        var missing = assertThrows(
                BusinessException.class, () -> service.update("other", "actor", custom.id(), update("跨空间", "LOCAL", null, false, 1)));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());
        var duplicateAcrossWorkspaces = service.create("other", "actor", input("对方层", "LOCAL", null, false));
        assertEquals("LOCAL", duplicateAcrossWorkspaces.enCode());

        when(WORKSPACES.isActiveMember("other", "actor")).thenReturn(false);
        var denied = assertThrows(BusinessException.class, () -> service.list("other", "actor"));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, denied.getErrorCode());
        assertEquals(6, count("other"));
    }

    private static String preset(String enCode) {
        service.list("ws", "actor");
        return jdbc.queryForObject(
                "SELECT id FROM yak_ops_modeling_layer WHERE workspace_id='ws' AND en_code=?", String.class, enCode);
    }

    private static int count(String workspace) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_modeling_layer WHERE workspace_id=?", Integer.class, workspace);
    }

    private String datasource(String workspace, String id) {
        jdbc.update(
                "INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,conn_status,remark,connection_params,original_json,create_time,update_time,create_by,update_by) VALUES(?,?,?,'MYSQL','jdbc:mysql://127.0.0.1:3306/demo',2,1,NULL,'{}','{}',NOW(3),NOW(3),'it','it')",
                id,
                workspace,
                "主仓");
        return id;
    }

    private static LayerInputDTO input(String cnName, String enCode, String dataSourceId, boolean enabled) {
        var input = new LayerInputDTO();
        input.setCnName(cnName);
        input.setEnCode(enCode);
        input.setDataSourceId(dataSourceId);
        input.setEnabled(enabled);
        return input;
    }

    private static UpdateLayerDTO update(
            String cnName, String enCode, String dataSourceId, boolean enabled, long revision) {
        var input = new UpdateLayerDTO();
        input.setCnName(cnName);
        input.setEnCode(enCode);
        input.setDataSourceId(dataSourceId);
        input.setEnabled(enabled);
        input.setRevision(revision);
        return input;
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
