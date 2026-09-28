package io.yak.ops.business.datasync.execution.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.flow.api.row.YakTableSchema;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSyncSchemaResolverTest {

    @Test
    void shouldKeepSourceValueOrderButUseTargetPhysicalColumnNames() {
        DataSourceCatalogColumnVO sourceId = column("id", Types.BIGINT, 1);
        DataSourceCatalogColumnVO sourceName = column("name", Types.VARCHAR, 2);

        DataSourceCatalogColumnVO targetName = column("NAME", Types.VARCHAR, 1);
        DataSourceCatalogColumnVO targetId = column("ID", Types.BIGINT, 2);

        YakTableSchema sourceSchema = DataSyncSchemaResolver.sourceSchema(List.of(sourceId, sourceName));
        YakTableSchema targetSchema =
                DataSyncSchemaResolver.targetWriteSchema(List.of(sourceId, sourceName), List.of(targetName, targetId));

        assertEquals(List.of("id", "name"), sourceSchema.columns().stream().map(value -> value.name()).toList());
        assertEquals(List.of("ID", "NAME"), targetSchema.columns().stream().map(value -> value.name()).toList());
    }

    private DataSourceCatalogColumnVO column(String name, int jdbcType, int ordinal) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(jdbcType == Types.BIGINT ? "BIGINT" : "VARCHAR");
        column.setJdbcType(jdbcType);
        column.setSize(jdbcType == Types.BIGINT ? 19 : 100);
        column.setScale(jdbcType == Types.BIGINT ? 0 : null);
        column.setNullable(true);
        column.setOrdinalPosition(ordinal);
        column.setPrimaryKey("id".equalsIgnoreCase(name));
        return column;
    }
}
