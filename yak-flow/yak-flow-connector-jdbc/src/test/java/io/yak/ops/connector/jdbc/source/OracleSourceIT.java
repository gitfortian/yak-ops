package io.yak.ops.connector.jdbc.source;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.OracleDatabase;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("oracle")
class OracleSourceIT extends JdbcSourceTestBase {
    @RegisterExtension
    static final OracleDatabase DATABASE = new OracleDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected Map<String, String> sqlTypes() {
        return Map.of(
                "integer",
                "NUMBER(19)",
                "string",
                "VARCHAR2(48)",
                "decimal",
                "NUMBER(18,2)",
                "binary",
                "BLOB",
                "timestamp",
                "TIMESTAMP(6)");
    }

    @Override
    protected String widenColumn(String table, String column) {
        return "ALTER TABLE " + table + " MODIFY (" + column + " VARCHAR2(72))";
    }
}
