package io.yak.ops.flow.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceColumn;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcSchemaMapperTest {

    @Test
    void shouldMapCatalogColumnsInOrdinalOrder() {
        YakTableSchema schema = JdbcSchemaMapper.fromColumns(List.of(
                new DataSourceColumn("name", "VARCHAR", Types.VARCHAR, 100, null, true, 2, false, null),
                new DataSourceColumn("id", "BIGINT", Types.BIGINT, 19, 0, false, 1, true, null),
                new DataSourceColumn("amount", "DECIMAL", Types.DECIMAL, 10, 2, true, 3, false, null)));

        assertEquals(List.of("id", "name", "amount"), schema.columns().stream().map(column -> column.name()).toList());
        assertEquals(List.of(YakDataType.BIGINT, YakDataType.STRING, YakDataType.DECIMAL), schema.columns().stream()
                .map(column -> column.dataType())
                .toList());
        assertEquals(List.of("id"), schema.primaryKeys());
        assertEquals(100, schema.column(1).length());
        assertEquals(10, schema.column(2).precision());
        assertEquals(2, schema.column(2).scale());
    }
}
