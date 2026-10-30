package io.yak.ops.business.datamap.tagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.testutils.MySqlDatabase;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Proves the FYOIP-19 migration on real MySQL: every flat tag survives as one value of one legacy
 * key, every binding survives with its original author, and the derived identities are
 * deterministic so a restored backup and a fresh migration produce the same dictionary.
 *
 * <p>The database is migrated only to V7 first, so the old tag tables exist to seed; the rest of
 * the chain then runs exactly as an upgrade does. A second database repeats the whole sequence with
 * the same seed, which is what makes the identity comparison meaningful.
 */
@Tag("datamap")
@Timeout(240)
class TagLegacyMigrationIT {

    private static final String WORKSPACE = "ws-a";
    private static final String EMPTY_WORKSPACE = "ws-b";
    private static final String COPY_DATABASE = "yak_it_copy";

    @RegisterExtension
    static final MySqlDatabase MYSQL = new MySqlDatabase("mysql-tag-legacy", false, false);

    static DataSource source;
    static JdbcTemplate jdbc;
    static DataSource copySource;
    static JdbcTemplate copyJdbc;

    @BeforeAll
    static void start() {
        var metadata = MYSQL.metadata();
        source = new DriverManagerDataSource(metadata.url(), metadata.username(), metadata.password());
        jdbc = new JdbcTemplate(source);
        copySource = new DriverManagerDataSource(
                "jdbc:mysql://" + metadata.host() + ":" + metadata.port() + "/" + COPY_DATABASE
                        + "?useSSL=false&allowPublicKeyRetrieval=true",
                metadata.username(),
                metadata.password());
        copyJdbc = new JdbcTemplate(copySource);
        jdbc.execute("DROP DATABASE IF EXISTS " + COPY_DATABASE);
        jdbc.execute("CREATE DATABASE " + COPY_DATABASE);

        for (DataSource current : new DataSource[] {source, copySource}) {
            Flyway.configure()
                    .dataSource(current)
                    .locations("classpath:db/migration/yak-ops")
                    .target("7")
                    .load()
                    .migrate();
            seed(current);
            Flyway.configure()
                    .dataSource(current)
                    .locations("classpath:db/migration/yak-ops")
                    .load()
                    .migrate();
        }
    }

    @AfterAll
    static void close() {
        jdbc.execute("DROP DATABASE IF EXISTS " + COPY_DATABASE);
    }

    @Test
    void onlyAWorkspaceWithTagsGetsALegacyKey() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_key WHERE workspace_id=?", Integer.class, WORKSPACE));
        assertEquals("legacy", jdbc.queryForObject(
                "SELECT name FROM yak_ops_tag_key WHERE workspace_id=?", String.class, WORKSPACE));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_tag_key WHERE workspace_id=?", Integer.class, EMPTY_WORKSPACE));
    }

    @Test
    void theLegacyKeyIsAMigrationArtifact() {
        assertEquals("system", jdbc.queryForObject(
                "SELECT create_by FROM yak_ops_tag_key WHERE workspace_id=?", String.class, WORKSPACE));
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0, 0), jdbc.queryForObject(
                "SELECT create_time FROM yak_ops_tag_key WHERE workspace_id=?", LocalDateTime.class, WORKSPACE));
        assertEquals(LocalDateTime.of(2026, 10, 2, 11, 0, 0), jdbc.queryForObject(
                "SELECT update_time FROM yak_ops_tag_key WHERE workspace_id=?", LocalDateTime.class, WORKSPACE));
    }

    @Test
    void everyOldTagBecomesOneValueUnderTheLegacyKey() {
        List<String> names = jdbc.query(
                "SELECT name FROM yak_ops_tag_value WHERE workspace_id=?", (rs, row) -> rs.getString("name"), WORKSPACE);
        assertEquals(3, names.size());
        for (String name : names) {
            assertEquals("tag-creator", jdbc.queryForObject(
                    "SELECT create_by FROM yak_ops_tag_value WHERE workspace_id=? AND name=?", String.class,
                    WORKSPACE, name));
        }
        List<String> keyIds = jdbc.query(
                "SELECT key_id FROM yak_ops_tag_value WHERE workspace_id=?", (rs, row) -> rs.getString("key_id"),
                WORKSPACE);
        assertEquals(1, keyIds.stream().distinct().count());
        assertTrue(keyIds.contains(legacyKeyId(WORKSPACE)));
    }

    @Test
    void everyOldBindingSurvivesWithItsAuthor() {
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value WHERE workspace_id=?", Integer.class,
                WORKSPACE));
        // table-1 carries the two bindings of its author, so one distinct author proves both survived.
        assertEquals("member-1", jdbc.queryForObject(
                "SELECT DISTINCT create_by FROM yak_ops_data_map_table_tag_value WHERE workspace_id=? AND table_id=?",
                String.class, WORKSPACE, "table-1"));
        assertEquals("member-2", jdbc.queryForObject(
                "SELECT create_by FROM yak_ops_data_map_table_tag_value WHERE workspace_id=? AND table_id=?",
                String.class, WORKSPACE, "table-2"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM yak_ops_data_map_table_tag_value b JOIN yak_ops_tag_value v "
                        + "ON v.id=b.tag_value_id AND v.workspace_id=b.workspace_id "
                        + "WHERE b.workspace_id=? AND b.table_id=? AND v.name=?",
                Integer.class, WORKSPACE, "table-2", "E-commerce"));
    }

    @Test
    void theOldTablesAreGone() {
        assertFalse(tableExists(jdbc, "yak_ops_data_map_tag"));
        assertFalse(tableExists(jdbc, "yak_ops_data_map_table_tag"));
        assertFalse(tableExists(copyJdbc, "yak_ops_data_map_tag"));
    }

    @Test
    void theDerivedIdentitiesMatchTheDocumentedFormula() {
        assertEquals(
                jdbc.queryForObject("SELECT CONCAT('tklg', LEFT(SHA2('" + WORKSPACE + "', 256), 32))", String.class),
                legacyKeyId(WORKSPACE));
        assertEquals(
                jdbc.queryForObject(
                        "SELECT CONCAT('tvlgb', LEFT(SHA2(CONCAT('" + WORKSPACE + "', ':', 'tag-2'), 256), 32))",
                        String.class),
                valueId(WORKSPACE, "零售"));
    }

    @Test
    void theSameSeedProducesTheSameIdentitiesInBothDatabases() {
        assertEquals(legacyKeyId(WORKSPACE), legacyKeyIdIn(copyJdbc, WORKSPACE));
        Map<String, String> primary = valueIdsByName(jdbc);
        Map<String, String> copy = valueIdsByName(copyJdbc);
        assertEquals(primary, copy);
        assertEquals(3, primary.size());
    }

    private static String legacyKeyId(String workspaceId) {
        return jdbc.queryForObject(
                "SELECT id FROM yak_ops_tag_key WHERE workspace_id=?", String.class, workspaceId);
    }

    private static String legacyKeyIdIn(JdbcTemplate template, String workspaceId) {
        return template.queryForObject(
                "SELECT id FROM yak_ops_tag_key WHERE workspace_id=?", String.class, workspaceId);
    }

    private static String valueId(String workspaceId, String name) {
        return jdbc.queryForObject(
                "SELECT id FROM yak_ops_tag_value WHERE workspace_id=? AND name=?", String.class, workspaceId, name);
    }

    private static Map<String, String> valueIdsByName(JdbcTemplate template) {
        List<Map<String, Object>> rows =
                template.queryForList("SELECT name,id FROM yak_ops_tag_value WHERE workspace_id=? ORDER BY name", WORKSPACE);
        Map<String, String> ids = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            ids.put(String.valueOf(row.get("name")), String.valueOf(row.get("id")));
        }
        return ids;
    }

    private static boolean tableExists(JdbcTemplate template, String table) {
        Integer count = template.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class,
                table);
        return count != null && count > 0;
    }

    /** Seeds the pre-FYOIP-19 state: two Workspaces, three flat tags and three bindings. */
    private static void seed(DataSource current) {
        JdbcTemplate template = new JdbcTemplate(current);
        workspace(template, WORKSPACE);
        workspace(template, EMPTY_WORKSPACE);
        datasource(template, "ds-a", WORKSPACE);
        datasource(template, "ds-b", EMPTY_WORKSPACE);
        table(template, "table-1", WORKSPACE);
        table(template, "table-2", WORKSPACE);
        tag(template, "tag-1", WORKSPACE, "电商");
        tag(template, "tag-2", WORKSPACE, "零售");
        tag(template, "tag-3", WORKSPACE, "E-commerce");
        binding(template, WORKSPACE, "table-1", "tag-1", "member-1");
        binding(template, WORKSPACE, "table-1", "tag-2", "member-1");
        binding(template, WORKSPACE, "table-2", "tag-3", "member-2");
    }

    private static void workspace(JdbcTemplate template, String id) {
        template.update(
                "INSERT IGNORE INTO yak_ops_workspace(id,name,create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,NOW(3),NOW(3),'it','it')",
                id,
                "name-" + id);
    }

    private static void datasource(JdbcTemplate template, String id, String workspaceId) {
        template.update(
                "INSERT INTO yak_ops_data_source(id,workspace_id,name,db_type,jdbc_url,environment,conn_status,"
                        + "remark,connection_params,original_json,create_time,update_time,create_by,update_by,revision) "
                        + "VALUES(?,?,?,'MYSQL','jdbc:mysql://localhost/test',1,0,NULL,'{}','{}',"
                        + "NOW(3),NOW(3),'it','it',1)",
                id,
                workspaceId,
                "name-" + id);
    }

    private static void table(JdbcTemplate template, String id, String workspaceId) {
        template.update(
                "INSERT INTO yak_ops_data_map_table(id,workspace_id,datasource_id,database_name,schema_name,name,"
                        + "table_type,remarks,status,first_seen_time,last_seen_time,description,owner_id,domain_id,"
                        + "create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,?,?,'',?,'TABLE',NULL,'ACTIVE',NOW(3),NOW(3),NULL,NULL,NULL,"
                        + "NOW(3),NOW(3),'it','it')",
                id,
                workspaceId,
                workspaceId.equals(WORKSPACE) ? "ds-a" : "ds-b",
                "db",
                id);
    }

    private static void tag(JdbcTemplate template, String id, String workspaceId, String name) {
        template.update(
                "INSERT INTO yak_ops_data_map_tag(id,workspace_id,name,create_time,update_time,create_by,update_by) "
                        + "VALUES(?,?,?,'2026-10-01 10:00:00.000','2026-10-02 11:00:00.000','tag-creator','tag-updater')",
                id,
                workspaceId,
                name);
    }

    private static void binding(
            JdbcTemplate template, String workspaceId, String tableId, String tagId, String actor) {
        template.update(
                "INSERT INTO yak_ops_data_map_table_tag(workspace_id,table_id,tag_id,create_time,create_by) "
                        + "VALUES(?,?,?,NOW(3),?)",
                workspaceId,
                tableId,
                tagId,
                actor);
    }
}
