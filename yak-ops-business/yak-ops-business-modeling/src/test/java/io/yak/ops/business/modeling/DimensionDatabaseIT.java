package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.dimension.DimensionService;
import io.yak.ops.business.modeling.dimension.impl.DimensionServiceImpl;
import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import io.yak.ops.dao.repository.modeling.impl.DimensionRepositoryImpl;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
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

/** Real MySQL per-scope uniqueness, ownership constraints, scope projection and revisions. */
@Tag("modeling")
@Timeout(180)
class DimensionDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static DimensionService service;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(
                metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class, DimensionRepositoryImpl.class, DimensionServiceImpl.class);
        service = context.getBean(DimensionService.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        reset(WORKSPACES);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        for (String table : List.of("dimension", "datamart", "domain", "category"))
            jdbc.update("DELETE FROM yak_ops_modeling_" + table);
        jdbc.update("DELETE FROM yak_ops_modeling_code");
        seedDomain("ws", "domain-a", "commerce", "交易域");
        seedDomain("ws", "domain-b", "trade", "贸易域");
        seedDatamart("ws", "mart-a", "operating", "运营集市");
    }

    @Test
    void keepsTheAbbreviationUniquePerScopeOnly() {
        var first = service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain-a", null, "product"));
        var second = service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain-b", null, "product"));
        var third = service.create("ws", "actor", input(DimensionScope.APPLICATION, null, "mart-a", "product"));

        assertNotNull(first.id());
        assertEquals(3, count());
        var duplicate = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain-a", null, "product")));
        assertEquals(DimensionModelingErrorCode.DUPLICATE_CODE, duplicate.getErrorCode());
        assertEquals(3, count());
        assertNotNull(second.id());
        assertNotNull(third.id());

        // The generated scope key backs a real unique index, not only a service check.
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_dimension(id,workspace_id,scope,domain_id,datamart_id,name,en_name,code,"
                        + "description,revision,create_time,update_time,create_by,update_by) "
                        + "VALUES('raw','ws','PUBLIC','domain-a',NULL,'商品','Product','product',NULL,1,"
                        + "NOW(3),NOW(3),'it','it')"));
        assertEquals(3, count());
    }

    @Test
    void databaseRejectsAMixedOwnershipRow() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_dimension(id,workspace_id,scope,domain_id,datamart_id,name,en_name,code,"
                        + "description,revision,create_time,update_time,create_by,update_by) "
                        + "VALUES('raw','ws','PUBLIC','domain-a','mart-a','商品','Product','raw',NULL,1,"
                        + "NOW(3),NOW(3),'it','it')"));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO yak_ops_modeling_dimension(id,workspace_id,scope,domain_id,datamart_id,name,en_name,code,"
                        + "description,revision,create_time,update_time,create_by,update_by) "
                        + "VALUES('raw2','ws','OTHER','domain-a',NULL,'商品','Product','raw2',NULL,1,"
                        + "NOW(3),NOW(3),'it','it')"));
        assertEquals(0, count());
    }

    @Test
    void rejectsAScopeOwnedByAnotherWorkspace() {
        var error = assertThrows(BusinessException.class,
                () -> service.create("other", "actor", input(DimensionScope.PUBLIC, "domain-a", null, "product")));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
        assertEquals(0, count());
    }

    @Test
    void projectsTheOwningScopeNameAndAdvancesRevisions() {
        var created = service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain-a", null, "product"));
        assertEquals("交易域", created.domainName());

        var updated = service.update(
                "ws", "actor", created.id(), update("product_sk", 1));
        assertEquals(2, updated.revision());
        assertEquals("product_sk", updated.code());
        assertEquals("交易域", updated.domainName());

        var stale = assertThrows(BusinessException.class,
                () -> service.update("ws", "actor", created.id(), update("product_sk", 1)));
        assertEquals(DimensionModelingErrorCode.REVISION_CONFLICT, stale.getErrorCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_modeling_dimension WHERE id=? AND revision=2", Integer.class, created.id()));

        service.delete("ws", "actor", created.id(), 2);
        assertEquals(0, count());
    }

    @Test
    void refusesToMoveAnExistingDimensionToAnotherScope() {
        var created = service.create("ws", "actor", input(DimensionScope.PUBLIC, "domain-a", null, "product"));

        var moved = new UpdateDimensionDTO();
        moved.setScope(DimensionScope.APPLICATION);
        moved.setDatamartId("mart-a");
        moved.setName("商品");
        moved.setEnName("Product");
        moved.setCode("product");
        moved.setRevision(1L);
        var error = assertThrows(BusinessException.class,
                () -> service.update("ws", "actor", created.id(), moved));

        assertEquals(DimensionModelingErrorCode.INVALID_SCOPE, error.getErrorCode());
        assertEquals(
                "PUBLIC",
                jdbc.queryForObject("SELECT scope FROM yak_ops_modeling_dimension WHERE id=?", String.class, created.id()));
    }

    private static DimensionInputDTO input(
            DimensionScope scope, String domainId, String datamartId, String code) {
        var input = new DimensionInputDTO();
        input.setScope(scope);
        input.setDomainId(domainId);
        input.setDatamartId(datamartId);
        input.setName("商品");
        input.setEnName("Product");
        input.setCode(code);
        return input;
    }

    private static UpdateDimensionDTO update(String code, long revision) {
        var input = new UpdateDimensionDTO();
        input.setScope(DimensionScope.PUBLIC);
        input.setDomainId("domain-a");
        input.setName("商品");
        input.setEnName("Product");
        input.setCode(code);
        input.setRevision(revision);
        return input;
    }

    private static void seedDomain(String workspace, String domainId, String code, String name) {
        String categoryId = "category-" + domainId;
        jdbc.update("INSERT INTO yak_ops_modeling_category(id,workspace_id,name,code,owner_id,description,revision,"
                        + "create_time,update_time,create_by,update_by) VALUES(?,?,?,?,'it',NULL,1,NOW(3),NOW(3),'it','it')",
                categoryId, workspace, "业务分类", categoryId);
        jdbc.update("INSERT INTO yak_ops_modeling_domain(id,workspace_id,category_id,name,code,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,?,?,?,'it',NULL,1,NOW(3),NOW(3),'it','it')",
                domainId, workspace, categoryId, name, code);
    }

    private static void seedDatamart(String workspace, String datamartId, String code, String name) {
        jdbc.update("INSERT INTO yak_ops_modeling_datamart(id,workspace_id,name,en_name,code,owner_id,description,"
                        + "revision,create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,?,'Operating Mart',?,'it',NULL,1,NOW(3),NOW(3),'it','it')",
                datamartId, workspace, name, code);
    }

    private static int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_dimension", Integer.class);
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
