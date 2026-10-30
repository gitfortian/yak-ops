package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.yak.ops.dao.entity.modeling.MetadataCollectionEntity;
import io.yak.ops.dao.entity.modeling.MetadataFieldEntity;
import io.yak.ops.dao.entity.modeling.MetadataTableEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.repository.modeling.MetadataRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.dao.repository.modeling.impl.MetadataRepositoryImpl;
import io.yak.ops.dao.repository.modeling.impl.PublicationRepositoryImpl;
import io.yak.ops.testutils.MySqlDatabase;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * Real MySQL behaviour of the publication and metadata tables introduced in V4.
 *
 * <p>Only a live engine can prove what the unit tests assume: that the status and revision checks
 * reject a hand-written row, that {@code BIT(1)} round-trips through the mapper as a boolean, that a
 * deleted model keeps its publication history with a null reference, and that discarding a snapshot
 * removes its tables and fields.
 */
@Tag("modeling")
@Timeout(180)
class PublicationDatabaseIT {
    @RegisterExtension static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static PublicationRepository publications;
    static MetadataRepository metadata;

    @BeforeAll
    static void start() {
        var credentials = MYSQL.metadata();
        source = new DriverManagerDataSource(
                credentials.url() + "&useAffectedRows=true", credentials.username(), credentials.password());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/yak-ops").load().migrate();
        context = new AnnotationConfigApplicationContext(
                Beans.class, PublicationRepositoryImpl.class, MetadataRepositoryImpl.class);
        publications = context.getBean(PublicationRepository.class);
        metadata = context.getBean(MetadataRepository.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        for (String table : List.of(
                "metadata_field",
                "metadata_table",
                "metadata_collection",
                "model_publication",
                "model_field",
                "model",
                "dimension",
                "datamart",
                "subject",
                "process",
                "domain",
                "category",
                "layer")) jdbc.update("DELETE FROM yak_ops_modeling_" + table);
        jdbc.update("DELETE FROM yak_ops_standard_field");
        jdbc.update("DELETE FROM yak_ops_standard_category");
        jdbc.update("DELETE FROM yak_ops_standard_catalog");
        jdbc.update("DELETE FROM yak_ops_data_source");
        jdbc.update("DELETE FROM yak_ops_workspace");
        jdbc.update("DELETE FROM yak_ops_modeling_code");
        seed();
    }

    @Test
    void recordsAPendingPublicationAndFinishesItExactlyOnce() {
        publications.add(pending("pub1"));

        var stored = publications.find("ws", "pub1").orElseThrow();
        assertEquals("PENDING", stored.getStatus());
        assertNull(stored.getFinishedTime());
        assertEquals("dwd_order_detail", stored.getPhysicalTable());
        // MySQL has no schema separate from the database, so the record carries none.
        assertNull(stored.getPhysicalSchema());
        assertEquals(3L, stored.getDatasourceRevision());

        assertTrue(publications.finish("ws", "pub1", "SUCCEEDED", null, 1200L, "log 1", now()));
        assertFalse(publications.finish("ws", "pub1", "FAILED", "second terminal state", 1200L, "log 1", now()));

        var finished = publications.find("ws", "pub1").orElseThrow();
        assertEquals("SUCCEEDED", finished.getStatus());
        assertNull(finished.getError());
        assertNotNull(finished.getFinishedTime());
    }

    @Test
    void reconcileMovesAnUnsettledRecordAndNeverATerminalOne() {
        publications.add(pending("pub2"));

        // PENDING is the residue of a process that died before writing a terminal state, so
        // reconciliation must be able to settle it; otherwise the row stays stuck forever.
        assertTrue(publications.reconcile("ws", "pub2", "SUCCEEDED", null, now()));
        assertEquals("SUCCEEDED", publications.find("ws", "pub2").orElseThrow().getStatus());

        // Reconciliation is not repeatable: the record is terminal now.
        assertFalse(publications.reconcile("ws", "pub2", "FAILED", "late", now()));

        publications.add(pending("pub4"));
        publications.finish("ws", "pub4", "UNKNOWN", "connection reset", 1200L, "log 4", now());
        assertTrue(publications.reconcile("ws", "pub4", "FAILED", "table absent", now()));
        assertEquals("FAILED", publications.find("ws", "pub4").orElseThrow().getStatus());
    }

    @Test
    void hidesAPublicationFromAnotherWorkspace() {
        publications.add(pending("pub3"));

        assertTrue(publications.find("other", "pub3").isEmpty());
        assertFalse(publications.finish("other", "pub3", "SUCCEEDED", null, 1200L, "log 3", now()));
        assertEquals(1, publications.listByModel("ws", "model", 50).size());
        assertEquals(0, publications.listByModel("other", "model", 50).size());
    }

    @Test
    void databaseRejectsAStatusOrRevisionOutsideTheAllowedRange() {
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO "
                + "yak_ops_modeling_model_publication(id,workspace_id,model_id,datasource_id,datasource_revision,"
                + "physical_database,physical_schema,physical_table,status,ddl,error,created_time,finished_time,"
                + "create_by) VALUES('raw','ws','model','ds',3,'demo',NULL,'dwd_order_detail','DONE',"
                + "'CREATE TABLE t (id bigint)',NULL,NOW(3),NULL,'it')"));
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO "
                + "yak_ops_modeling_model_publication(id,workspace_id,model_id,datasource_id,datasource_revision,"
                + "physical_database,physical_schema,physical_table,status,ddl,error,created_time,finished_time,"
                + "create_by) VALUES('raw','ws','model','ds',0,'demo',NULL,'dwd_order_detail','PENDING',"
                + "'CREATE TABLE t (id bigint)',NULL,NOW(3),NULL,'it')"));
        assertEquals(0, rows("model_publication"));
    }

    @Test
    void deletingAModelKeepsItsPublicationHistoryWithoutTheReference() {
        publications.add(pending("pub4"));

        jdbc.update("DELETE FROM yak_ops_modeling_model_field");
        jdbc.update("DELETE FROM yak_ops_modeling_model WHERE workspace_id='ws' AND id='model'");

        var kept = publications.find("ws", "pub4").orElseThrow();
        assertNull(kept.getModelId());
        assertEquals("dwd_order_detail", kept.getPhysicalTable());
    }

    @Test
    void roundTripsASnapshotAndReplacesItAsAWhole() {
        publications.add(pending("pub5"));
        metadata.addCollection(collection("col1", "pub5"));
        metadata.addTable(table("tbl1", "col1"));
        metadata.addField(field("fld1", "tbl1", 1, "order_id", true));
        metadata.addField(field("fld2", "tbl1", 2, "amount", false));

        var latest = metadata.latestCollection("ws", "pub5").orElseThrow();
        assertEquals("SUCCEEDED", latest.getStatus());
        assertEquals(1, latest.getTableCount());
        var tables = metadata.tables("ws", "col1");
        assertEquals(1, tables.size());
        assertEquals("dwd_order_detail", tables.getFirst().getName());
        var fields = metadata.fields("ws", "tbl1");
        assertEquals(2, fields.size());
        // BIT(1) must arrive as a boolean, not as a byte or a string.
        assertEquals(Boolean.TRUE, fields.getFirst().getNullable());
        assertEquals(Boolean.TRUE, fields.getFirst().getPrimaryKey());
        assertEquals(1, fields.getFirst().getPrimaryKeyPosition());
        assertEquals(18, fields.getLast().getSize());
        assertEquals(2, fields.getLast().getScale());

        // A refresh deletes the previous run; its tables and fields go with it through the cascade.
        metadata.deleteByPublication("ws", "pub5");
        assertTrue(metadata.latestCollection("ws", "pub5").isEmpty());
        assertEquals(0, rows("metadata_table"));
        assertEquals(0, rows("metadata_field"));
    }

    @Test
    void deletingAPublicationTakesItsSnapshot() {
        publications.add(pending("pub6"));
        metadata.addCollection(collection("col2", "pub6"));
        metadata.addTable(table("tbl2", "col2"));
        metadata.addField(field("fld3", "tbl2", 1, "order_id", true));

        jdbc.update("DELETE FROM yak_ops_modeling_model_publication WHERE workspace_id='ws' AND id='pub6'");

        assertEquals(0, rows("metadata_collection"));
        assertEquals(0, rows("metadata_table"));
        assertEquals(0, rows("metadata_field"));
    }

    @Test
    void readsTheNewestStatusPerModelInOneQuery() {
        publications.add(pending("pub8"));
        publications.finish("ws", "pub8", "SUCCEEDED", null, 1200L, "log 8", now());
        var later = pending("pub9");
        later.setCreatedTime(now().plusSeconds(1));
        publications.add(later);

        assertEquals(Map.of("model", "PENDING"), publications.latestStatusByModel("ws", List.of("model")));
        assertTrue(publications.latestStatusByModel("ws", List.of("other-model")).isEmpty());
        assertTrue(publications.latestStatusByModel("ws", List.of()).isEmpty());
    }

    @Test
    void keepsSnapshotCountsAndOrdinalsPositive() {
        publications.add(pending("pub7"));

        var negativeCounts = collection("col3", "pub7");
        negativeCounts.setFieldCount(-1);
        assertThrows(DataAccessException.class, () -> metadata.addCollection(negativeCounts));

        metadata.addCollection(collection("col4", "pub7"));
        metadata.addTable(table("tbl3", "col4"));
        var zeroOrdinal = field("fld4", "tbl3", 0, "order_id", true);
        assertThrows(DataAccessException.class, () -> metadata.addField(zeroOrdinal));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static ModelPublicationEntity pending(String id) {
        var row = new ModelPublicationEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setModelId("model");
        row.setDatasourceId("ds");
        row.setDatasourceRevision(3);
        row.setPhysicalDatabase("demo");
        row.setPhysicalTable("dwd_order_detail");
        row.setStatus("PENDING");
        row.setDdl("CREATE TABLE dwd_order_detail (\n  order_id bigint NOT NULL\n)");
        row.setCreatedTime(now());
        row.setCreateBy("it");
        return row;
    }

    private static MetadataCollectionEntity collection(String id, String publicationId) {
        var row = new MetadataCollectionEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setPublicationId(publicationId);
        row.setStatus("SUCCEEDED");
        row.setTableCount(1);
        row.setFieldCount(2);
        row.setCreatedTime(now());
        row.setFinishedTime(now());
        row.setCreateBy("it");
        return row;
    }

    private static MetadataTableEntity table(String id, String collectionId) {
        var row = new MetadataTableEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setCollectionId(collectionId);
        row.setDatasourceId("ds");
        row.setDatabaseName("demo");
        row.setName("dwd_order_detail");
        row.setType("TABLE");
        row.setRemarks("订单明细");
        return row;
    }

    private static MetadataFieldEntity field(
            String id, String tableId, int ordinal, String name, boolean key) {
        var row = new MetadataFieldEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setTableId(tableId);
        row.setOrdinal(ordinal);
        row.setName(name);
        row.setTypeName(key ? "bigint" : "decimal");
        row.setJdbcType(key ? -5 : 3);
        row.setSize(key ? null : 18);
        row.setScale(key ? null : 2);
        row.setNullable(key);
        row.setPrimaryKey(key);
        row.setPrimaryKeyPosition(key ? 1 : null);
        row.setRemarks(key ? "订单号" : "金额");
        return row;
    }

    private static int rows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM yak_ops_modeling_" + table, Integer.class);
    }

    private static void seed() {
        jdbc.update("INSERT INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) "
                + "VALUES('ws','ws',NOW(3),NOW(3),'it','it'),('other','other',NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,"
                + "conn_status,remark,connection_params,original_json,create_time,update_time,create_by,update_by) "
                + "VALUES('ds','ws','主仓','MYSQL','jdbc:mysql://127.0.0.1:3306/demo',2,1,NULL,'{}','{}',"
                + "NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_layer(id,workspace_id,en_code,cn_name,data_source_id,"
                + "description,enabled,builtin,sort_no,revision,create_time,update_time,create_by,update_by) "
                + "VALUES('layer','ws','DWD','明细层','ds',NULL,1,0,1000,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_category(id,workspace_id,name,code,owner_id,description,"
                + "revision,create_time,update_time,create_by,update_by) "
                + "VALUES('cat','ws','电商业务','commerce','it',NULL,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_domain(id,workspace_id,category_id,name,code,owner_id,"
                + "description,revision,create_time,update_time,create_by,update_by) "
                + "VALUES('domain','ws','cat','交易域','trade','it',NULL,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_process(id,workspace_id,domain_id,name,code,owner_id,"
                + "description,revision,create_time,update_time,create_by,update_by) "
                + "VALUES('process','ws','domain','下单','order','it',NULL,1,NOW(3),NOW(3),'it','it')");
        jdbc.update("INSERT INTO yak_ops_modeling_model(id,workspace_id,type,layer_id,category_id,process_id,"
                + "domain_id,datamart_id,dimension_id,name,cn_name,storage_policy,lifecycle_days,owner_id,"
                + "description,revision,create_time,update_time,create_by,update_by) "
                + "VALUES('model','ws','DWD','layer','cat','process','domain',NULL,NULL,'dwd_order_detail',"
                + "'订单明细','DAILY_INCREMENTAL',30,'it',NULL,1,NOW(3),NOW(3),'it','it')");
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
