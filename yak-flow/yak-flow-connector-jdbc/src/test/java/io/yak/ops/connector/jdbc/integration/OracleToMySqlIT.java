package io.yak.ops.connector.jdbc.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.OracleDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("oracle-mysql")
class OracleToMySqlIT extends JdbcPipelineTestBase {
    @RegisterExtension
    static final OracleDatabase SOURCE = new OracleDatabase();

    @RegisterExtension
    static final MySqlDatabase TARGET = new MySqlDatabase();

    @Override
    protected DatabaseMetadata sourceDatabase() {
        return SOURCE.metadata();
    }

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
