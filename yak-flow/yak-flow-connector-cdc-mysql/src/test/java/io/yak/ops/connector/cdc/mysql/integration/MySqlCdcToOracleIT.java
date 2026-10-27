package io.yak.ops.connector.cdc.mysql.integration;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.OracleDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("cdc-pipeline-oracle")
@Tag("cdc-acceptance-oracle")
class MySqlCdcToOracleIT extends MySqlCdcPipelineTestBase {
    @RegisterExtension
    static final OracleDatabase TARGET = new OracleDatabase();

    @Override
    protected DatabaseMetadata targetDatabase() {
        return TARGET.metadata();
    }
}
