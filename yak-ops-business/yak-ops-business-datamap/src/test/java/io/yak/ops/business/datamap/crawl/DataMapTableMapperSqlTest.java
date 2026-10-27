package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

/**
 * Keeps the observed/curated column split structural.
 *
 * <p>This is the test that stays true after a later field is added: the crawler statement may
 * name only observed columns and the audit update time, while the curation statement may name
 * nothing but the three curated columns.
 */
class DataMapTableMapperSqlTest {

    private static final Set<String> OBSERVED = Set.of("table_type", "remarks", "status", "last_seen_time");

    private static final Set<String> CURATED = Set.of("description", "owner_id", "domain_id");

    @Test
    void crawlerUpdateNamesObservedColumnsAndTheAuditTimeOnly() throws NoSuchMethodException {
        String sql = sqlOf("updateObserved", String.class, String.class, String.class, String.class,
                String.class, LocalDateTime.class, LocalDateTime.class);
        assertEquals(OBSERVED, assignedColumns(sql).stream()
                .filter(column -> !column.equals("update_time"))
                .collect(Collectors.toSet()));
        assertTrue(assignedColumns(sql).contains("update_time"));
        CURATED.forEach(column -> assertFalse(sql.contains(column), column));
        assertFalse(sql.contains("first_seen_time"));
        assertFalse(sql.contains("update_by"));
    }

    @Test
    void curationUpdateNamesTheThreeCuratedColumnsOnly() throws NoSuchMethodException {
        String sql = sqlOf("updateCurated", String.class, String.class, String.class, String.class, String.class);
        assertEquals(CURATED, assignedColumns(sql));
        assertFalse(sql.contains("table_type"));
        assertFalse(sql.contains("remarks"));
        assertFalse(sql.contains("status"));
        assertFalse(sql.contains("last_seen_time"));
        assertFalse(sql.contains("first_seen_time"));
        assertFalse(sql.contains("update_time"));
        assertFalse(sql.contains("update_by"));
    }

    /** Reads the left-hand sides of {@code SET} so a column mention elsewhere cannot mask a write. */
    private static Set<String> assignedColumns(String sql) {
        String assignment = sql.substring(sql.indexOf(" SET ") + 5, sql.indexOf(" WHERE "));
        return Arrays.stream(assignment.split(","))
                .map(pair -> pair.split("=")[0].trim())
                .collect(Collectors.toSet());
    }

    private static String sqlOf(String method, Class<?>... parameters) throws NoSuchMethodException {
        Update update = DataMapTableMapper.class.getMethod(method, parameters).getAnnotation(Update.class);
        return String.join(" ", update.value());
    }
}
