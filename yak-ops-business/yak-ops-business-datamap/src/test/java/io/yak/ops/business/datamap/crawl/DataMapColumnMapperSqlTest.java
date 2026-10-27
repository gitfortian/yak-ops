package io.yak.ops.business.datamap.crawl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * Keeps the field list of one asset complete and ordered.
 *
 * <p>The detail page renders whatever this statement returns, without paging of its own, so a
 * LIMIT here would silently drop columns and an unordered result would scramble the 序号 column.
 * A table with seventeen columns must come back with seventeen rows in declaration order.
 */
class DataMapColumnMapperSqlTest {

    @Test
    void listsEveryColumnOfOneAssetInOrdinalOrder() throws NoSuchMethodException {
        Select select = DataMapColumnMapper.class
                .getMethod("listByTable", String.class, String.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("ORDER BY ordinal"), sql);
        assertFalse(sql.toUpperCase().contains("LIMIT"), sql);
        assertTrue(sql.contains("table_id=#{tableId}"), sql);
    }
}
