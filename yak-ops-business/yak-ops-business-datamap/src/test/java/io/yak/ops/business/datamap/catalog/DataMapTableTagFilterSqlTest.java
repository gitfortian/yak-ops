package io.yak.ops.business.datamap.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.mapper.datamap.DataMapSearchCriteria;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.util.List;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * Verifies the catalog tag filter builds one {@code EXISTS} clause per key on real MyBatis.
 *
 * <p>The per-key grouping is a nested {@code <foreach>} inside the provider-built statement, so the
 * parsing and parameter binding are exercised here through {@code getBoundSql} rather than through
 * a database round trip: every placeholder is bound and no caller value reaches the SQL text.
 */
class DataMapTableTagFilterSqlTest {

    private static final String STATEMENT = "io.yak.ops.dao.mapper.datamap.DataMapTableMapper.selectPage";

    private static final String TAG_CLAUSE =
            "EXISTS (SELECT 1 FROM yak_ops_data_map_table_tag_value b WHERE b.workspace_id=t.workspace_id "
                    + "AND b.table_id=t.id AND b.tag_value_id IN";

    @Test
    void twoKeysProduceTwoAndedExistsClauses() {
        BoundSql bound = boundSql(List.of(List.of("v1", "v2"), List.of("v3")));

        String sql = squeeze(bound.getSql());
        assertEquals(2, countOccurrences(sql, TAG_CLAUSE), sql);
        assertTrue(sql.contains("IN ( ? , ? )"), sql);
        // The clauses are combined with AND, so values of one key are OR-ed and keys are ANDed.
        assertEquals(1, countOccurrences(sql, ") AND EXISTS ("), sql);
        assertEveryPlaceholderIsBound(sql, bound);
    }

    @Test
    void oneKeyProducesASingleInList() {
        BoundSql bound = boundSql(List.of(List.of("v1")));

        String sql = squeeze(bound.getSql());
        assertEquals(1, countOccurrences(sql, TAG_CLAUSE), sql);
        assertTrue(sql.contains("IN ( ? )"), sql);
        assertEquals(0, countOccurrences(sql, ") AND EXISTS ("), sql);
        assertEveryPlaceholderIsBound(sql, bound);
    }

    @Test
    void noTagFilterLeavesNoTagClause() {
        BoundSql bound = boundSql(List.of());

        String sql = squeeze(bound.getSql());
        assertEquals(0, countOccurrences(sql, TAG_CLAUSE), sql);
        assertEveryPlaceholderIsBound(sql, bound);
    }

    @Test
    void noCallerValueReachesTheSqlText() {
        BoundSql bound = boundSql(List.of(List.of("v1", "v2"), List.of("v3")));

        String sql = bound.getSql();
        assertFalse(sql.contains("v1"), sql);
        assertFalse(sql.contains("v2"), sql);
        assertFalse(sql.contains("v3"), sql);
    }

    private static void assertEveryPlaceholderIsBound(String sql, BoundSql bound) {
        assertEquals(countOccurrences(sql, "?"), bound.getParameterMappings().size(), sql);
    }

    private static BoundSql boundSql(List<List<String>> groups) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(DataMapTableMapper.class);
        MappedStatement statement = configuration.getMappedStatement(STATEMENT);
        DataMapSearchCriteria criteria = new DataMapSearchCriteria();
        criteria.setWorkspaceId("ws");
        criteria.setTagValueGroups(groups);
        criteria.setIncludeMissing(false);
        criteria.setIds(List.of());
        criteria.setLimit(20);
        criteria.setOffset(0);
        return statement.getBoundSql(criteria);
    }

    /** Collapses the whitespace MyBatis leaves around foreach separators so clauses can be matched. */
    private static String squeeze(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
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
