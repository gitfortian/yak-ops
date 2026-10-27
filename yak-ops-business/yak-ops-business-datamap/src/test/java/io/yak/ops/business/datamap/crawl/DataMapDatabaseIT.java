package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.testutils.MySqlDatabase;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Proves the scope lock on real MySQL, because it is index behaviour rather than Java behaviour.
 *
 * <p>A second insert for a datasource that already holds an active run must fail on
 * {@code uk_data_map_crawl_active} with no pre-check to lean on, and every terminal path — success,
 * run failure, queue rejection and the restart sweep — must leave {@code active_scope} NULL in the
 * same statement that writes the terminal status. A leaked lock would make the datasource
 * un-crawlable with no UI remedy, so each path is asserted by submitting again afterwards.
 */
@Tag("datamap")
@Timeout(120)
class DataMapDatabaseIT {

    private static final String WORKSPACE = "ws";
    private static final String DATASOURCE = "ds-a";
    private static final String OTHER_DATASOURCE = "ds-b";

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static DataMapCrawlMapper crawlMapper;

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(metadata.url(), metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration/yak-ops")
                .load()
                .migrate();
        context = new AnnotationConfigApplicationContext(Beans.class);
        crawlMapper = context.getBean(DataMapCrawlMapper.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        jdbc.update("DELETE FROM yak_ops_data_map_crawl");
        jdbc.update("DELETE FROM yak_ops_data_map_table");
        jdbc.update("DELETE FROM yak_ops_data_source");
        jdbc.update("INSERT IGNORE INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) "
                + "VALUES('" + WORKSPACE + "','ws',NOW(3),NOW(3),'it','it')");
        datasource(DATASOURCE);
        datasource(OTHER_DATASOURCE);
    }

    @Test
    void aSecondActiveRunIsRejectedByTheUniqueIndex() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));

        assertThrows(
                DuplicateKeyException.class, () -> crawlMapper.insert(run("run-2", DATASOURCE, "RUNNING", 1)));
        assertEquals(1, activeRuns(DATASOURCE));
    }

    @Test
    void finishedRunsCoexistBecauseNullsAreDistinct() {
        crawlMapper.insert(run("run-1", DATASOURCE, "SUCCEEDED", null));
        crawlMapper.insert(run("run-2", DATASOURCE, "FAILED", null));

        assertEquals(0, activeRuns(DATASOURCE));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_crawl WHERE datasource_id=?", Integer.class, DATASOURCE));
    }

    @Test
    void oneDatasourceDoesNotBlockAnother() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));
        crawlMapper.insert(run("run-2", OTHER_DATASOURCE, "PENDING", 1));

        assertEquals(1, activeRuns(DATASOURCE));
        assertEquals(1, activeRuns(OTHER_DATASOURCE));
    }

    @Test
    void concurrentInsertsHaveExactlyOneWinner() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> attempts = List.of(
                    () -> attempt("race-1", barrier), () -> attempt("race-2", barrier));
            List<Future<String>> futures = attempts.stream().map(pool::submit).toList();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(30, TimeUnit.SECONDS));

            assertEquals(1, Collections.frequency(outcomes, "ACCEPTED"), outcomes.toString());
            assertEquals(1, Collections.frequency(outcomes, "DUPLICATE"), outcomes.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, activeRuns(DATASOURCE));
    }

    @Test
    void successReleasesTheScopeLock() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));
        crawlMapper.claim("run-1", LocalDateTime.now());
        crawlMapper.finishSucceeded("run-1", 10, 20, 3, 7, 0, 0, 0, LocalDateTime.now());

        assertNull(activeScope("run-1"));
        crawlMapper.insert(run("run-2", DATASOURCE, "PENDING", 1));
    }

    @Test
    void runFailureReleasesTheScopeLock() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));
        crawlMapper.claim("run-1", LocalDateTime.now());
        crawlMapper.finishFailed("run-1", "enumeration failed", 0, 0, 0, 0, 0, LocalDateTime.now());

        assertNull(activeScope("run-1"));
        crawlMapper.insert(run("run-2", DATASOURCE, "PENDING", 1));
    }

    @Test
    void queueRejectionReleasesTheScopeLock() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));
        crawlMapper.reject("run-1", "queue full", LocalDateTime.now());

        assertNull(activeScope("run-1"));
        crawlMapper.insert(run("run-2", DATASOURCE, "PENDING", 1));
    }

    @Test
    void restartSweepFailsInterruptedRunsAndReleasesEveryLock() {
        crawlMapper.insert(run("run-1", DATASOURCE, "PENDING", 1));
        crawlMapper.insert(run("run-2", OTHER_DATASOURCE, "RUNNING", 1));
        assertThrows(
                DuplicateKeyException.class, () -> crawlMapper.insert(run("run-3", DATASOURCE, "PENDING", 1)));

        assertEquals(2, crawlMapper.sweepInterrupted("interrupted by application restart", LocalDateTime.now()));

        assertEquals("FAILED", status("run-1"));
        assertEquals("FAILED", status("run-2"));
        assertNull(activeScope("run-1"));
        assertNull(activeScope("run-2"));
        crawlMapper.insert(run("run-4", DATASOURCE, "PENDING", 1));
        crawlMapper.insert(run("run-5", OTHER_DATASOURCE, "PENDING", 1));
    }

    private static String attempt(String id, CyclicBarrier barrier) throws Exception {
        barrier.await(30, TimeUnit.SECONDS);
        try {
            crawlMapper.insert(run(id, DATASOURCE, "PENDING", 1));
            return "ACCEPTED";
        } catch (DuplicateKeyException duplicate) {
            return "DUPLICATE";
        }
    }

    private static void datasource(String id) {
        jdbc.update(
                "INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,conn_status,"
                        + "remark,connection_params,original_json,create_time,update_time,create_by,update_by,revision) "
                        + "VALUES(?,?,?,'MYSQL','jdbc:mysql://localhost/test',1,0,NULL,'{}','{}',"
                        + "NOW(3),NOW(3),'it','it',1)",
                id,
                WORKSPACE,
                "name-" + id);
    }

    private static DataMapCrawlEntity run(String id, String datasourceId, String status, Integer activeScope) {
        DataMapCrawlEntity row = new DataMapCrawlEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setDatasourceId(datasourceId);
        row.setDatasourceRevision(1L);
        row.setDatabaseName("");
        row.setSchemaName("");
        row.setStatus(status);
        row.setActiveScope(activeScope);
        row.setTableCount(0);
        row.setColumnCount(0);
        row.setAddedCount(0);
        row.setUpdatedCount(0);
        row.setRemovedCount(0);
        row.setFailedCount(0);
        row.setFilteredCount(0);
        row.setTriggerType("MANUAL");
        row.setCreatedTime(LocalDateTime.now());
        row.setCreateBy("it");
        return row;
    }

    private static int activeRuns(String datasourceId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_crawl WHERE datasource_id=? AND active_scope IS NOT NULL",
                Integer.class,
                datasourceId);
    }

    private static Integer activeScope(String id) {
        return jdbc.queryForObject(
                "SELECT active_scope FROM yak_ops_data_map_crawl WHERE id=?", Integer.class, id);
    }

    private static String status(String id) {
        return jdbc.queryForObject("SELECT status FROM yak_ops_data_map_crawl WHERE id=?", String.class, id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan("io.yak.ops.dao.mapper.datamap")
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
        SqlSessionFactory sqlSessionFactory() throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
    }
}
