package io.yak.ops.connector.jdbc.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.OracleDatabase;
import io.yak.ops.testutils.PostgresDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("postgres-oracle")
class PostgresToOracleIT extends JdbcPipelineTestBase {
    @RegisterExtension
    static final PostgresDatabase SOURCE = new PostgresDatabase();

    @RegisterExtension
    static final OracleDatabase TARGET = new OracleDatabase();

    @Override
    protected DatabaseMetadata sourceDatabase() {
        return SOURCE.metadata();
    }

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
