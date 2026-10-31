package io.yak.ops.business.datamap.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.dao.mapper.datamap.DataMapSearchCriteria;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.util.List;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * Verifies the catalog owner filter binds the owner as a parameter on real MyBatis.
 *
 * <p>The condition is provider-built, so the parsed statement is exercised here rather than through
 * a database round trip: the owner clause appears exactly once, every placeholder is bound and the
 * owner value never reaches the SQL text.
 */
class DataMapTableOwnerFilterSqlTest {

    private static final String STATEMENT = "io.yak.ops.dao.mapper.datamap.DataMapTableMapper.selectPage";

    private static final String OWNER_CLAUSE = "t.owner_id=?";

    @Test
    void ownerFilterAddsOneBoundOwnerClause() {
        BoundSql bound = boundSql("user-1");

        String sql = squeeze(bound.getSql());
        assertEquals(1, countOccurrences(sql, OWNER_CLAUSE), sql);
        assertEquals(countOccurrences(sql, "?"), bound.getParameterMappings().size(), sql);
        assertFalse(sql.contains("user-1"), sql);
    }

    @Test
    void noOwnerFilterLeavesNoOwnerClause() {
        BoundSql bound = boundSql(null);

        String sql = squeeze(bound.getSql());
        assertEquals(0, countOccurrences(sql, OWNER_CLAUSE), sql);
        assertEquals(countOccurrences(sql, "?"), bound.getParameterMappings().size(), sql);
    }

    private static BoundSql boundSql(String ownerId) {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(DataMapTableMapper.class);
        MappedStatement statement = configuration.getMappedStatement(STATEMENT);
        DataMapSearchCriteria criteria = new DataMapSearchCriteria();
        criteria.setWorkspaceId("ws");
        criteria.setOwnerId(ownerId);
        criteria.setIncludeMissing(false);
        criteria.setIds(List.of());
        criteria.setLimit(20);
        criteria.setOffset(0);
        return statement.getBoundSql(criteria);
    }

    /** Collapses the whitespace MyBatis leaves around separators so clauses can be matched. */
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
