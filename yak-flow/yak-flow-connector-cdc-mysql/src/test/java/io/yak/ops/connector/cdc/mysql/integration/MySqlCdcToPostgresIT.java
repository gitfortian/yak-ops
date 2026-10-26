package io.yak.ops.connector.cdc.mysql.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.PostgresDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("cdc-pipeline-postgres")
class MySqlCdcToPostgresIT extends MySqlCdcPipelineTestBase {
    @RegisterExtension
    static final PostgresDatabase TARGET = new PostgresDatabase();

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
