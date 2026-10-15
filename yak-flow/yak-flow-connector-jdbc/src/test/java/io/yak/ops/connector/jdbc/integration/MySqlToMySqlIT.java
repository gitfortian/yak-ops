package io.yak.ops.connector.jdbc.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql-mysql")
class MySqlToMySqlIT extends JdbcPipelineTestBase {
    @RegisterExtension
    static final MySqlDatabase SOURCE = new MySqlDatabase();

    @RegisterExtension
    static final MySqlDatabase TARGET = new MySqlDatabase("mysql-target", false, true);

    @Override
    protected DatabaseMetadata sourceDatabase() {
        return SOURCE.metadata();
    }

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
