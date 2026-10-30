package io.yak.ops.business.datamap.tagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.entity.tag.TagKeyEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.entity.tag.TableTagValueEntity;
import io.yak.ops.dao.mapper.datamap.DataMapSearchCriteria;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.tag.TagMapper;
import io.yak.ops.testutils.MySqlDatabase;
import java.time.LocalDateTime;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Proves the tag dictionary constraints on real MySQL, because they are index and foreign-key
 * behaviour rather than Java behaviour.
 *
 * <p>The delete protection is the point: a bound value rejects its own delete, a key delete cascades
 * into that same rejection, and an asset delete cascades its bindings while the dictionary rows
 * survive. The composite foreign keys must also reject a cross-Workspace binding, and the catalog
 * filter must OR the values of one key and AND different keys.
 */
@Tag("datamap")
@Timeout(180)
class TagDatabaseIT {

    private static final String WORKSPACE = "ws-a";
    private static final String OTHER_WORKSPACE = "ws-b";

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase();

    static DataSource source;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static TagMapper tagMapper;
    static DataMapTableMapper tableMapper;

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
        tagMapper = context.getBean(TagMapper.class);
        tableMapper = context.getBean(DataMapTableMapper.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void isolate() {
        jdbc.update("DELETE FROM yak_ops_data_map_table_tag_value");
        jdbc.update("DELETE FROM yak_ops_tag_value");
        jdbc.update("DELETE FROM yak_ops_tag_key");
        jdbc.update("DELETE FROM yak_ops_data_map_table");
        jdbc.update("DELETE FROM yak_ops_data_source");
        workspace(WORKSPACE);
        workspace(OTHER_WORKSPACE);
        datasource("ds-a", WORKSPACE);
        datasource("ds-b", OTHER_WORKSPACE);
    }

    @Test
    void aBoundValueRejectsItsOwnDelete() {
        keyValueBinding("key-1", "value-1", "table-1", WORKSPACE, "ds-a");

        assertThrows(DataIntegrityViolationException.class, () -> tagMapper.deleteValue(WORKSPACE, "value-1"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_value WHERE id='value-1'", Integer.class));
    }

    @Test
    void aKeyWithOneBoundValueRejectsItsDeleteThroughTheCascade() {
        keyValueBinding("key-1", "value-1", "table-1", WORKSPACE, "ds-a");

        assertThrows(DataIntegrityViolationException.class, () -> tagMapper.deleteKey(WORKSPACE, "key-1"));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_key WHERE id='key-1'", Integer.class));
    }

    @Test
    void unbindingTheValueLetsBothDeletesSucceed() {
        keyValueBinding("key-1", "value-1", "table-1", WORKSPACE, "ds-a");
        assertEquals(1, tagMapper.deleteBindingsByTable(WORKSPACE, "table-1"));

        assertEquals(1, tagMapper.deleteValue(WORKSPACE, "value-1"));
        assertEquals(1, tagMapper.deleteKey(WORKSPACE, "key-1"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_value WHERE workspace_id=?", Integer.class, WORKSPACE));
    }

    @Test
    void anUnboundKeyDeleteCascadesItsValues() {
        insertKey("key-1", WORKSPACE, "dept");
        insertValue("value-1", "key-1", WORKSPACE, "电商");
        insertValue("value-2", "key-1", WORKSPACE, "零售");

        assertEquals(1, tagMapper.deleteKey(WORKSPACE, "key-1"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_value WHERE key_id='key-1'", Integer.class));
    }

    @Test
    void deletingAnAssetCascadesItsBindingsAndKeepsTheDictionary() {
        keyValueBinding("key-1", "value-1", "table-1", WORKSPACE, "ds-a");

        assertEquals(1, jdbc.update("DELETE FROM yak_ops_data_map_table WHERE id='table-1'"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value WHERE table_id='table-1'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_key WHERE id='key-1'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_value WHERE id='value-1'", Integer.class));
    }

    @Test
    void aCompositeForeignKeyRejectsACrossWorkspaceBinding() {
        keyValueBinding("key-1", "value-1", "table-1", WORKSPACE, "ds-a");

        // The row claims ws-b while both the asset and the value belong to ws-a: both composite
        // foreign keys reject it, so no service mistake can bind across a Workspace boundary.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertBindingRaw(OTHER_WORKSPACE, "table-1", "value-1"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value WHERE workspace_id=?",
                Integer.class,
                OTHER_WORKSPACE));
    }

    @Test
    void aCompositeForeignKeyRejectsATableOfAnotherWorkspace() {
        keyValueBinding("key-1", "value-1", "table-1", OTHER_WORKSPACE, "ds-b");

        // table-1 exists, but as an asset of ws-b; claiming it for ws-a fails the composite key.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertBindingRaw(WORKSPACE, "table-1", "value-1"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value WHERE workspace_id=?",
                Integer.class,
                WORKSPACE));
    }

    @Test
    void theCollationMakesCaseVariantsDuplicates() {
        insertKey("key-1", WORKSPACE, "dept");
        insertKey("key-2", WORKSPACE, "subject");
        insertValue("value-1", "key-1", WORKSPACE, "电商");
        insertValue("value-2", "key-2", WORKSPACE, "电商");
        insertValue("value-4", "key-1", WORKSPACE, "Retail");

        assertThrows(DuplicateKeyException.class, () -> insertKey("key-3", WORKSPACE, "Dept"));
        assertThrows(DuplicateKeyException.class, () -> insertValue("value-3", "key-1", WORKSPACE, "RETAIL"));

        // The same value name under a different key stays legal: uniqueness is per key.
        assertNotNull(tagMapper.findValueByName("key-2", "电商"));
    }

    @Test
    void theUsageCountQueryCountsBindingsPerValue() {
        insertKey("key-1", WORKSPACE, "dept");
        insertValue("value-1", "key-1", WORKSPACE, "电商");
        insertValue("value-2", "key-1", WORKSPACE, "零售");
        table("table-1", WORKSPACE, "ds-a");
        table("table-2", WORKSPACE, "ds-a");
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-1", "value-1")));
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-2", "value-1")));
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-1", "value-2")));

        var usage = tagMapper.listUsageCounts(WORKSPACE);

        assertEquals(2L, usage.stream()
                .filter(row -> "value-1".equals(row.getValueId()))
                .mapToLong(row -> row.getUsageCount())
                .findFirst()
                .orElse(0L));
        assertEquals(1L, usage.stream()
                .filter(row -> "value-2".equals(row.getValueId()))
                .mapToLong(row -> row.getUsageCount())
                .findFirst()
                .orElse(0L));
    }

    @Test
    void theCatalogFilterUnionsTheValuesOfOneKey() {
        seedFilteredCatalog();
        // dept IN (电商, 零售): table-1 and table-2 match, table-3 carries no dept value at all.
        assertEquals(List.of("table-1", "table-2"), matched(List.of(List.of("value-1", "value-2"))));
        assertEquals(2, counted(List.of(List.of("value-1", "value-2"))));
    }

    @Test
    void theCatalogFilterIntersectsDifferentKeys() {
        seedFilteredCatalog();
        // dept = 电商 AND sensitivity = PII: only table-2 carries both.
        assertEquals(List.of("table-2"), matched(List.of(List.of("value-1"), List.of("value-3"))));
        assertEquals(1, counted(List.of(List.of("value-1"), List.of("value-3"))));
    }

    @Test
    void theCatalogFilterReturnsNothingWhenOneKeyClauseFails() {
        seedFilteredCatalog();
        // dept = 零售 AND sensitivity = PII: no asset carries both, so the intersection is empty.
        assertEquals(List.of(), matched(List.of(List.of("value-2"), List.of("value-3"))));
        assertEquals(0, counted(List.of(List.of("value-2"), List.of("value-3"))));
    }

    private void seedFilteredCatalog() {
        insertKey("key-dept", WORKSPACE, "dept");
        insertValue("value-1", "key-dept", WORKSPACE, "电商");
        insertValue("value-2", "key-dept", WORKSPACE, "零售");
        insertKey("key-sensitivity", WORKSPACE, "sensitivity");
        insertValue("value-3", "key-sensitivity", WORKSPACE, "PII");
        table("table-1", WORKSPACE, "ds-a");
        table("table-2", WORKSPACE, "ds-a");
        table("table-3", WORKSPACE, "ds-a");
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-1", "value-1")));
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-2", "value-1")));
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-2", "value-3")));
        tagMapper.insertBindings(List.of(binding(WORKSPACE, "table-3", "value-3")));
    }

    private List<String> matched(List<List<String>> groups) {
        return tableMapper.selectPage(criteria(groups)).stream()
                .map(DataMapTableEntity::getId)
                .toList();
    }

    private long counted(List<List<String>> groups) {
        return tableMapper.count(criteria(groups));
    }

    private static DataMapSearchCriteria criteria(List<List<String>> groups) {
        DataMapSearchCriteria criteria = new DataMapSearchCriteria();
        criteria.setWorkspaceId(WORKSPACE);
        criteria.setTagValueGroups(groups);
        criteria.setIncludeMissing(false);
        criteria.setIds(List.of());
        criteria.setLimit(50);
        criteria.setOffset(0);
        return criteria;
    }

    private void keyValueBinding(String keyId, String valueId, String tableId, String workspaceId, String ds) {
        insertKey(keyId, workspaceId, "dept");
        insertValue(valueId, keyId, workspaceId, "电商");
        table(tableId, workspaceId, ds);
        tagMapper.insertBindings(List.of(binding(workspaceId, tableId, valueId)));
    }

    private void insertKey(String id, String workspaceId, String name) {
        TagKeyEntity row = new TagKeyEntity();
        row.setId(id);
        row.setWorkspaceId(workspaceId);
        row.setName(name);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(row.getCreateTime());
        row.setCreateBy("it");
        row.setUpdateBy("it");
        tagMapper.insertKey(row);
    }

    private void insertValue(String id, String keyId, String workspaceId, String name) {
        TagValueEntity row = new TagValueEntity();
        row.setId(id);
        row.setWorkspaceId(workspaceId);
        row.setKeyId(keyId);
        row.setName(name);
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(row.getCreateTime());
        row.setCreateBy("it");
        row.setUpdateBy("it");
        tagMapper.insertValue(row);
    }

    private static TableTagValueEntity binding(String workspaceId, String tableId, String valueId) {
        TableTagValueEntity row = new TableTagValueEntity();
        row.setWorkspaceId(workspaceId);
        row.setTableId(tableId);
        row.setTagValueId(valueId);
        row.setCreateTime(LocalDateTime.now());
        row.setCreateBy("it");
        return row;
    }

    /**
     * Binds through a plain INSERT because {@code insertBindings} uses {@code INSERT IGNORE}, which
     * drops a foreign-key row as a warning. The constraint is what this proves, so the statement
     * must not carry the clause that suppresses it.
     */
    private void insertBindingRaw(String workspaceId, String tableId, String valueId) {
        jdbc.update(
                "INSERT INTO yak_ops_data_map_table_tag_value"
                        + "(workspace_id,table_id,tag_value_id,create_time,create_by) VALUES(?,?,?,NOW(3),'it')",
                workspaceId,
                tableId,
                valueId);
    }

    private void table(String id, String workspaceId, String datasourceId) {
        jdbc.update(
                "INSERT INTO yak_ops_data_map_table(id,workspace_id,datasource_id,database_name,schema_name,name,"
                        + "table_type,remarks,status,first_seen_time,last_seen_time,description,owner_id,domain_id,"
                        + "create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,?,'db','',?,'TABLE',NULL,'ACTIVE',NOW(3),NOW(3),NULL,NULL,NULL,"
                        + "NOW(3),NOW(3),'it','it')",
                id,
                workspaceId,
                datasourceId,
                id);
    }

    private static void workspace(String id) {
        jdbc.update(
                "INSERT IGNORE INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) "
                        + "VALUES(?,'name',NOW(3),NOW(3),'it','it')",
                id);
    }

    private void datasource(String id, String workspaceId) {
        jdbc.update(
                "INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,conn_status,"
                        + "remark,connection_params,original_json,create_time,update_time,create_by,update_by,revision) "
                        + "VALUES(?,?,?,'MYSQL','jdbc:mysql://localhost/test',1,0,NULL,'{}','{}',"
                        + "NOW(3),NOW(3),'it','it',1)",
                id,
                workspaceId,
                "name-" + id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan({"io.yak.ops.dao.mapper.tag", "io.yak.ops.dao.mapper.datamap"})
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
            var factory = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
    }
}
