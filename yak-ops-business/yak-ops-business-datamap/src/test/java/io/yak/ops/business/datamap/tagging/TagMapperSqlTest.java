package io.yak.ops.business.datamap.tagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.entity.tag.TagKeyEntity;
import io.yak.ops.dao.entity.tag.TableTagValueEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.mapper.tag.TagMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * Verifies every dictionary statement parses and binds on real MyBatis without a database.
 *
 * <p>The statements carry {@code <foreach>} fragments — an IN list, a multi-row insert, two lists in
 * one predicate — so binding them through {@code getBoundSql} proves the placeholders resolve before
 * the MySQL integration tests run.
 */
class TagMapperSqlTest {

    private static final String NAMESPACE = "io.yak.ops.dao.mapper.tag.TagMapper.";

    private static final List<String> TABLE_IDS = List.of("table-1");

    private static final List<String> VALUE_IDS = List.of("value-1");

    @Test
    void everyStatementBindsItsPlaceholders() {
        Configuration configuration = configuration();
        Map<String, BoundSql> bound = new HashMap<>();
        bind(configuration, bound, "insertKey", key("key-1"));
        bind(configuration, bound, "findKeyById", params("workspaceId", "ws", "id", "key-1"));
        bind(configuration, bound, "findKeyByName", params("workspaceId", "ws", "name", "dept"));
        bind(configuration, bound, "listKeys", params("workspaceId", "ws"));
        bind(configuration, bound, "renameKey", params("workspaceId", "ws", "id", "key-1", "name", "dept",
                "updateTime", LocalDateTime.now(), "updateBy", "actor"));
        bind(configuration, bound, "deleteKey", params("workspaceId", "ws", "id", "key-1"));
        bind(configuration, bound, "insertValue", value("value-1", "key-1"));
        bind(configuration, bound, "findValueById", params("workspaceId", "ws", "id", "value-1"));
        bind(configuration, bound, "findValuesByIds", params("workspaceId", "ws", "ids", VALUE_IDS));
        bind(configuration, bound, "findValueByName", params("keyId", "key-1", "name", "电商"));
        bind(configuration, bound, "listValues", params("workspaceId", "ws"));
        bind(configuration, bound, "renameValue", params("workspaceId", "ws", "id", "value-1", "name", "电商",
                "updateTime", LocalDateTime.now(), "updateBy", "actor"));
        bind(configuration, bound, "deleteValue", params("workspaceId", "ws", "id", "value-1"));
        bind(configuration, bound, "countValuesByKey", params("workspaceId", "ws", "keyId", "key-1"));
        bind(configuration, bound, "insertBindings", params("rows", List.of(binding("table-1", "value-1"))));
        bind(configuration, bound, "deleteBindingsByTable", params("workspaceId", "ws", "tableId", "table-1"));
        bind(configuration, bound, "listBindings", params("workspaceId", "ws", "tableIds", TABLE_IDS));
        bind(configuration, bound, "listBindingsByTablesAndValues",
                params("workspaceId", "ws", "tableIds", TABLE_IDS, "tagValueIds", VALUE_IDS));
        bind(configuration, bound, "deleteBindingsByTablesAndValues",
                params("workspaceId", "ws", "tableIds", TABLE_IDS, "tagValueIds", VALUE_IDS));
        bind(configuration, bound, "listUsageCounts", params("workspaceId", "ws"));
        bind(configuration, bound, "countBindingsByValue", params("workspaceId", "ws", "tagValueId", "value-1"));
        bind(configuration, bound, "listTagRefs", params("workspaceId", "ws", "tableIds", TABLE_IDS));

        bound.forEach((method, sql) -> {
            assertEquals(countOccurrences(sql.getSql(), "?"), sql.getParameterMappings().size(), method + ": " + sql.getSql());
            assertFalse(sql.getSql().contains("value-1"), method + " leaks a caller value into the SQL text");
        });
    }

    @Test
    void theBatchInsertIgnoresPairsThatAreAlreadyBound() {
        BoundSql bound = bind(configuration(), "insertBindings", params("rows", List.of(binding("table-1", "value-1"))));

        assertTrue(bound.getSql().contains("INSERT IGNORE INTO yak_ops_data_map_table_tag_value"), bound.getSql());
    }

    @Test
    void theUsageProjectionJoinsTheKeyName() {
        BoundSql bound = bind(configuration(), "listTagRefs", params("workspaceId", "ws", "tableIds", TABLE_IDS));

        String sql = bound.getSql();
        assertTrue(sql.contains("JOIN yak_ops_tag_value v ON v.id=b.tag_value_id"), sql);
        assertTrue(sql.contains("JOIN yak_ops_tag_key k ON k.id=v.key_id"), sql);
        assertTrue(sql.contains("ORDER BY k.name,v.name"), sql);
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TagMapper.class);
        return configuration;
    }

    private static void bind(
            Configuration configuration, Map<String, BoundSql> into, String method, Object parameter) {
        into.put(method, configuration.getMappedStatement(NAMESPACE + method).getBoundSql(parameter));
    }

    private static BoundSql bind(Configuration configuration, String method, Object parameter) {
        return configuration.getMappedStatement(NAMESPACE + method).getBoundSql(parameter);
    }

    /** Mirrors the MyBatis parameter map a mapper method with {@code @Param} arguments receives. */
    private static Map<String, Object> params(String key, Object value, Object... rest) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(key, value);
        for (int index = 0; index < rest.length; index += 2) {
            parameters.put(String.valueOf(rest[index]), rest[index + 1]);
        }
        return parameters;
    }

    private static TagKeyEntity key(String id) {
        TagKeyEntity row = new TagKeyEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setName("dept");
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setCreateBy("actor");
        row.setUpdateBy("actor");
        return row;
    }

    private static TagValueEntity value(String id, String keyId) {
        TagValueEntity row = new TagValueEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setKeyId(keyId);
        row.setName("电商");
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setCreateBy("actor");
        row.setUpdateBy("actor");
        return row;
    }

    private static TableTagValueEntity binding(String tableId, String valueId) {
        TableTagValueEntity row = new TableTagValueEntity();
        row.setWorkspaceId("ws");
        row.setTableId(tableId);
        row.setTagValueId(valueId);
        row.setCreateTime(LocalDateTime.now());
        row.setCreateBy("actor");
        return row;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
