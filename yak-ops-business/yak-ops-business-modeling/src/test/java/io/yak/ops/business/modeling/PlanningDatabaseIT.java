package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.impl.PlanningServiceImpl;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.impl.PlanningRepositoryImpl;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL global uniqueness, rollback, composite foreign keys and parent/child concurrency. */
@Tag("modeling")
@Timeout(120)
class PlanningDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();
    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static PlanningService service;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);

    @BeforeAll static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(Beans.class, PlanningRepositoryImpl.class, PlanningServiceImpl.class);
        service = context.getBean(PlanningService.class);
    }
    @AfterAll static void close() { if (context != null) context.close(); }
    @BeforeEach void isolate() {
        reset(WORKSPACES);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        for (String table : List.of("process", "domain", "category", "code")) jdbc.update("DELETE FROM yak_ops_modeling_" + table);
    }

    @Test void globalCodeRaceHasOneWinnerAcrossWorkspaces() throws Exception {
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> createAfter(start, "one"));
            var b = workers.submit(() -> createAfter(start, "two"));
            start.countDown();
            assertEquals(1, a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, count("category"));
        assertEquals(1, count("code"));
    }

    @Test void codeUniquenessSpansLevelsAndFailedUpdatesRollBack() {
        var category = service.create("ws", "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("commerce"));
        var domain = service.create("ws", "actor", NodeKind.DOMAIN, category.id(), PlanningServiceTest.input("trade"));
        var duplicate = assertThrows(BusinessException.class, () ->
                service.create("other", "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("trade")));
        assertEquals(ModelingErrorCode.DUPLICATE_CODE, duplicate.getErrorCode());
        assertThrows(BusinessException.class, () ->
                service.update("ws", "actor", NodeKind.CATEGORY, category.id(), PlanningServiceTest.update("trade", 1)));
        assertEquals("commerce", jdbc.queryForObject("SELECT code FROM yak_ops_modeling_category WHERE id=?", String.class, category.id()));
        assertEquals(1L, jdbc.queryForObject("SELECT revision FROM yak_ops_modeling_category WHERE id=?", Long.class, category.id()));
        assertEquals(2, count("code"));
        assertNotNull(domain.id());
    }

    @Test void failedForeignKeyInsertDoesNotLeaveAReservation() {
        var node = PlanningServiceTest.node(NodeKind.DOMAIN, "bad-parent", "missing");
        node.setName("invalid");
        node.setCode("rollback");
        node.setOwnerId("owner");
        node.initCreate("actor");
        var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(ignored ->
                context.getBean(PlanningRepository.class).add(node)));
        assertEquals(0, count("domain"));
        assertEquals(0, count("code"));
    }

    @Test void updatesFieldsWithAnUnchangedCodeInAffectedRowsMode() {
        var category = service.create("ws", "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("same_code"));
        var result = service.update("ws", "actor", NodeKind.CATEGORY, category.id(), PlanningServiceTest.update("same_code", 1));
        assertEquals(2, result.revision());
        assertEquals(1, count("code"));
    }

    @Test void rejectsCrossWorkspaceParentAndProtectsHierarchyDeletion() {
        var category = service.create("ws", "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("commerce"));
        assertThrows(BusinessException.class, () -> service.create("other", "actor", NodeKind.DOMAIN, category.id(), PlanningServiceTest.input("bad")));
        var domain = service.create("ws", "actor", NodeKind.DOMAIN, category.id(), PlanningServiceTest.input("trade"));
        var node = PlanningServiceTest.node(NodeKind.DOMAIN, "cross-sql", category.id());
        node.setWorkspaceId("other");
        node.setName("invalid");
        node.setCode("cross_sql");
        node.setOwnerId("owner");
        node.initCreate("actor");
        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(ignored -> context.getBean(PlanningRepository.class).add(node)));
        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", NodeKind.CATEGORY, category.id(), 1));
        assertEquals(ModelingErrorCode.HAS_DEPENDENCIES, error.getErrorCode());
        service.delete("ws", "actor", NodeKind.DOMAIN, domain.id(), 1);
        service.delete("ws", "actor", NodeKind.CATEGORY, category.id(), 1);
        assertEquals(0, count("code"));
    }

    @Test void deletingParentAndCreatingChildCannotLeaveAnOrphan() throws Exception {
        var category = service.create("ws", "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("commerce"));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var deletion = workers.submit(() -> {
                start.await();
                try { service.delete("ws", "actor", NodeKind.CATEGORY, category.id(), 1); return true; }
                catch (BusinessException conflict) { assertEquals(ModelingErrorCode.HAS_DEPENDENCIES, conflict.getErrorCode()); return false; }
            });
            var creation = workers.submit(() -> {
                start.await();
                try { service.create("ws", "actor", NodeKind.DOMAIN, category.id(), PlanningServiceTest.input("trade")); return true; }
                catch (BusinessException missing) { assertEquals(10010, missing.getErrorCode().getCode()); return false; }
            });
            start.countDown();
            assertNotEquals(deletion.get(20, TimeUnit.SECONDS), creation.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_domain d LEFT JOIN yak_ops_modeling_category c ON d.workspace_id=c.workspace_id AND d.category_id=c.id WHERE c.id IS NULL", Integer.class));
    }

    private static int createAfter(CountDownLatch start, String workspace) throws InterruptedException {
        start.await();
        try { service.create(workspace, "actor", NodeKind.CATEGORY, null, PlanningServiceTest.input("same_code")); return 1; }
        catch (BusinessException conflict) { assertEquals(ModelingErrorCode.DUPLICATE_CODE, conflict.getErrorCode()); return 0; }
    }
    private static int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_" + table, Integer.class); }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("io.yak.ops.dao.mapper.modeling")
    static class Beans {
        @Bean DataSource dataSource() { return source; }
        @Bean PlatformTransactionManager transactionManager() { return new JdbcTransactionManager(source); }
        @Bean WorkspaceService workspaceService() { return WORKSPACES; }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
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
