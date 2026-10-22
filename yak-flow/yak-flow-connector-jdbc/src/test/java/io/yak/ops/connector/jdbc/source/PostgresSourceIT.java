package io.yak.ops.connector.jdbc.source;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.PostgresDatabase;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("postgres")
class PostgresSourceIT extends JdbcSourceTestBase {
    @RegisterExtension
    static final PostgresDatabase DATABASE = new PostgresDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected Map<String, String> sqlTypes() {
        return Map.of(
                "integer",
                "BIGINT",
                "string",
                "VARCHAR(48)",
                "decimal",
                "DECIMAL(18,2)",
                "binary",
                "BYTEA",
                "timestamp",
                "TIMESTAMP(6)");
    }

    @Override
    protected String widenColumn(String table, String column) {
        return "ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE VARCHAR(72)";
    }
}
