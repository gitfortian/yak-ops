package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.OracleDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("oracle")
class OracleSinkIT extends JdbcSinkTestBase {
    @RegisterExtension
    static final OracleDatabase DATABASE = new OracleDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected String fields() {
        return "\"ID\" NUMBER(18,0) PRIMARY KEY, \"LABEL\" VARCHAR2(48), \"AMOUNT\" NUMBER(18,2), \"PAYLOAD\" BLOB";
    }
}
