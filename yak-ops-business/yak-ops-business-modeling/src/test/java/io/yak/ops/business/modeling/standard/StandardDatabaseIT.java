package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.business.modeling.standard.impl.StandardServiceImpl;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.SecurityLevel;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.security.user.UserStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.modeling.standard.StandardRepository;
import io.yak.ops.dao.repository.modeling.standard.impl.StandardRepositoryImpl;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL standard initialization, global code uniqueness, isolation and catalog locking. */
@Tag("modeling")
@Timeout(180)
class StandardDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static StandardService service;
    static final WorkspaceService WORKSPACES = mock(WorkspaceService.class);
    static final UserRepository USERS = mock(UserRepository.class);

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(
                metadata.url() + "&useAffectedRows=true", metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class, StandardRepositoryImpl.class, StandardServiceImpl.class);
        service = context.getBean(StandardService.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        reset(WORKSPACES, USERS);
        when(WORKSPACES.isActiveMember(anyString(), anyString())).thenReturn(true);
        var member = new WorkspaceMemberVO();
        member.setUserId("owner");
        when(WORKSPACES.queryWorkspaceMembers(anyString(), anyString())).thenReturn(List.of(member));
        when(USERS.queryByIds(anyList())).thenReturn(List.of(user("owner")));
        for (String table : List.of("field", "category", "catalog"))
            jdbc.update("DELETE FROM yak_ops_standard_" + table);
        jdbc.update("DELETE FROM yak_ops_workspace WHERE id IN ('ws','other','fresh')");
        for (String workspace : List.of("ws", "other", "fresh"))
            jdbc.update(
                    "INSERT INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) VALUES(?,?,NOW(3),NOW(3),'it','it')",
                    workspace,
                    workspace);
    }

    @Test
    void initializesOneSetOfDefaultRootsConcurrently() throws Exception {
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> seed = () -> {
                start.await();
                return service.initialize("ws", "actor").size();
            };
            var first = workers.submit(seed);
            var second = workers.submit(seed);
            start.countDown();
            assertEquals(2, first.get(30, TimeUnit.SECONDS));
            assertEquals(2, second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(2, count("category"));
        assertEquals(1, count("catalog"));
    }

    @Test
    void keepsDeletedDefaultRootsDeleted() {
        assertEquals(2, service.initialize("ws", "actor").size());
        var root = jdbc.queryForObject(
                "SELECT id FROM yak_ops_standard_category WHERE workspace_id='ws' AND name='公共标准'", String.class);
        service.deleteCategory("ws", "actor", root, 1);
        assertEquals(1, service.initialize("ws", "actor").size());
        assertEquals(1, count("category"));
    }

    @Test
    void globalCodeRaceHasOneWinnerAcrossWorkspaces() throws Exception {
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> createCodeAfter(start, "ws"));
            var second = workers.submit(() -> createCodeAfter(start, "other"));
            start.countDown();
            assertEquals(1, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, count("field"));
    }

    @Test
    void deletingACategoryAndCreatingItsFieldCannotLeaveAnOrphan() throws Exception {
        service.initialize("ws", "actor");
        var category = service.createCategory("ws", "actor", category("临时目录"));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var deletion = workers.submit(() -> {
                start.await();
                try {
                    service.deleteCategory("ws", "actor", category.id(), 1);
                    return true;
                } catch (BusinessException dependency) {
                    assertEquals(StandardErrorCode.HAS_DEPENDENCIES, dependency.getErrorCode());
                    return false;
                }
            });
            var creation = workers.submit(() -> {
                start.await();
                try {
                    service.createField("ws", "actor", category.id(), input("orphan_check"));
                    return true;
                } catch (BusinessException missing) {
                    assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());
                    return false;
                }
            });
            start.countDown();
            assertNotEquals(deletion.get(30, TimeUnit.SECONDS), creation.get(30, TimeUnit.SECONDS));
        }
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM yak_ops_standard_field f LEFT JOIN yak_ops_standard_category c ON f.workspace_id=c.workspace_id AND f.category_id=c.id WHERE c.id IS NULL",
                        Integer.class));
    }

    @Test
    void hidesOtherWorkspaceIdentitiesAndRejectsNonMembers() {
        service.initialize("ws", "actor");
        var category = service.createCategory("ws", "actor", category("本地目录"));
        var missing = assertThrows(
                BusinessException.class, () -> service.page("other", "actor", category.id(), null, 1, 20));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, missing.getErrorCode());
        var deletion = assertThrows(
                BusinessException.class, () -> service.deleteCategory("other", "actor", category.id(), 1));
        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, deletion.getErrorCode());
        when(WORKSPACES.isActiveMember("other", "actor")).thenReturn(false);
        var denied = assertThrows(BusinessException.class, () -> service.tree("other", "actor"));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, denied.getErrorCode());
    }

    @Test
    void escapesSearchWildcardsAndKeepsPagingSnapshots() {
        service.initialize("ws", "actor");
        var category = service.createCategory("ws", "actor", category("检索目录"));
        service.createField("ws", "actor", category.id(), input("order_underscore", "订单_明细"));
        service.createField("ws", "actor", category.id(), input("order_letter", "订单X明细"));
        service.createField("ws", "actor", category.id(), input("order_percent", "订单%合计"));
        service.createField("ws", "actor", category.id(), input("order_bang", "折扣!金额"));
        assertEquals(1, service.page("ws", "actor", null, "订单_", 1, 20).total());
        assertEquals(1, service.page("ws", "actor", null, "订单%", 1, 20).total());
        assertEquals(1, service.page("ws", "actor", null, "折扣!", 1, 20).total());
        assertEquals(2, service.page("ws", "actor", null, "订单", 1, 2).records().size());
        assertEquals(3, service.page("ws", "actor", null, "订单", 1, 20).total());
        assertEquals(0, service.page("ws", "actor", null, "订单", 9, 20).records().size());
    }

    @Test
    void advancesNoOpRevisionsAndRollsBackDuplicateCodes() {
        service.initialize("ws", "actor");
        var category = service.createCategory("ws", "actor", category("编号目录"));
        var saved = service.createField("ws", "actor", category.id(), input("same_code"));
        assertEquals(2, service.updateField("ws", "actor", saved.id(), update("same_code", 1)).revision());
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT revision FROM yak_ops_standard_field WHERE id=?", Long.class, saved.id()));

        var other = service.createField("ws", "actor", category.id(), input("taken_code"));
        var duplicate = assertThrows(
                BusinessException.class,
                () -> service.updateField("ws", "actor", other.id(), update("same_code", 1)));
        assertEquals(StandardErrorCode.DUPLICATE_CODE, duplicate.getErrorCode());
        assertEquals(
                "taken_code",
                jdbc.queryForObject(
                        "SELECT en_code FROM yak_ops_standard_field WHERE id=?", String.class, other.id()));
    }

    @Test
    void rollsBackTheCatalogReservationWhenTheFirstWriteFails() {
        var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var node = new StandardFieldEntity();
        node.setWorkspaceId("fresh");
        node.setCategoryId("missing-category");
        node.setCnName("非法字段");
        node.setEnCode("rollback_probe");
        node.setDataType(DataType.STRING);
        node.setIsNotNull(false);
        node.setSecurityLevel(SecurityLevel.INTERNAL);
        node.setOwnerId("owner");
        node.setRevision(1);
        node.initCreate("actor");
        assertThrows(RuntimeException.class, () -> transactions.executeWithoutResult(ignored -> {
            var repository = context.getBean(StandardRepository.class);
            repository.lockCatalog("fresh", "actor");
            repository.insertField(node);
        }));
        assertEquals(0, count("catalog"));
        assertEquals(0, count("field"));
    }

    private static int createCodeAfter(CountDownLatch start, String workspace) throws InterruptedException {
        start.await();
        try {
            service.initialize(workspace, "actor");
            var category = service.createCategory(workspace, "actor", category("共享目录"));
            service.createField(workspace, "actor", category.id(), input("shared_code"));
            return 1;
        } catch (BusinessException conflict) {
            assertEquals(StandardErrorCode.DUPLICATE_CODE, conflict.getErrorCode());
            return 0;
        }
    }

    private static CategoryInputDTO category(String name) {
        var input = new CategoryInputDTO();
        input.setName(name);
        return input;
    }

    static StandardInputDTO input(String code) {
        return input(code, "订单金额");
    }

    static StandardInputDTO input(String code, String name) {
        var input = new StandardInputDTO();
        input.setCnName(name);
        input.setEnCode(code);
        input.setDataType(DataType.STRING);
        input.setIsNotNull(false);
        input.setSecurityLevel(SecurityLevel.INTERNAL);
        input.setOwnerId("owner");
        return input;
    }

    static StandardUpdateDTO update(String code, long revision) {
        var input = new StandardUpdateDTO();
        input.setCnName("订单金额");
        input.setEnCode(code);
        input.setDataType(DataType.STRING);
        input.setIsNotNull(false);
        input.setSecurityLevel(SecurityLevel.INTERNAL);
        input.setOwnerId("owner");
        input.setRevision(revision);
        return input;
    }

    private static UserEntity user(String id) {
        var user = new UserEntity();
        user.setId(id);
        user.setUserName(id + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private static int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_standard_" + table, Integer.class);
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
        UserRepository userRepository() {
            return USERS;
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
