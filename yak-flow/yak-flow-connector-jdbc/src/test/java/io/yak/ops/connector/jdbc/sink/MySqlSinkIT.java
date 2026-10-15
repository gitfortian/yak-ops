package io.yak.ops.connector.jdbc.sink;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql")
class MySqlSinkIT extends JdbcSinkTestBase {
    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

    @Override
    protected DatabaseMetadata database() {
        return DATABASE.metadata();
    }

    @Override
    protected String fields() {
        return "`ID` DECIMAL(18,0) PRIMARY KEY, `LABEL` VARCHAR(48), `AMOUNT` DECIMAL(18,2), `PAYLOAD` BLOB";
    }
}
