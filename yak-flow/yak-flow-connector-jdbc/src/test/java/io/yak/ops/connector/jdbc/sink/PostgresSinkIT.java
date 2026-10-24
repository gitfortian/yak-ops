package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.PostgresDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("postgres")
class PostgresSinkIT extends JdbcSinkTestBase {
    @RegisterExtension
    static final PostgresDatabase DATABASE = new PostgresDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }


    @Test
    void tinyintSourceWritesToNativeSmallintTarget() throws Exception {
        io.yak.ops.connector.jdbc.database.dialect.JdbcDialect dialect =
                new io.yak.ops.connector.jdbc.database.internal.dialect.PostgresJdbcDialect();
        var source = new io.yak.ops.core.types.TableSchema(
                java.util.List.of(new io.yak.ops.core.types.Column("id", io.yak.ops.core.types.LogicalTypes.TINYINT)),
                java.util.List.of());
        var target = new io.yak.ops.core.types.TableSchema(
                java.util.List.of(new io.yak.ops.core.types.Column("id", io.yak.ops.core.types.LogicalTypes.SMALLINT)),
                java.util.List.of());
        JdbcSinkSchemaValidator.validate(source, target, false);
        var route = new JdbcTableWritePlan(
                new io.yak.ops.core.data.TableId(null, "public", "input"),
                new io.yak.ops.core.data.TableId(null, "public", "output"),
                target, JdbcWriteMode.APPEND, source, java.util.Map.of());
        var projected = route.project(io.yak.ops.core.data.GenericRowData.of((byte) 42),
                io.yak.ops.core.data.RowKind.INSERT);
        org.junit.jupiter.api.Assertions.assertEquals((short) 42, projected.getShort(0));
        org.junit.jupiter.api.Assertions.assertEquals("SMALLINT",
                dialect.nativeType(source.column(0)).ddl());
    }

    @Override
    protected String fields() {
        return "\"ID\" DECIMAL(18,0) PRIMARY KEY, \"LABEL\" VARCHAR(48), \"AMOUNT\" DECIMAL(18,2), \"PAYLOAD\" BYTEA";
    }
}
