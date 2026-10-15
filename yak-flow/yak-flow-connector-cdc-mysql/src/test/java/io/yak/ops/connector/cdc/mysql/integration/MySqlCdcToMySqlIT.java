package io.yak.ops.connector.cdc.mysql.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("cdc-mysql")
class MySqlCdcToMySqlIT extends MySqlCdcPipelineTestBase {
    @RegisterExtension
    static final MySqlDatabase TARGET = new MySqlDatabase("mysql-target", false, true);

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
