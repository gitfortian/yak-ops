package io.yak.ops.connector.jdbc.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.PostgresDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql-postgres")
class MySqlToPostgresIT extends JdbcPipelineTestBase {
    @RegisterExtension
    static final MySqlDatabase SOURCE = new MySqlDatabase();

    @RegisterExtension
    static final PostgresDatabase TARGET = new PostgresDatabase();

    @Override
    protected DatabaseMetadata sourceDatabase() {
        return SOURCE.metadata();
    }

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
