package io.yak.ops.connector.jdbc.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceSplitReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.core.data.TableId;
import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.MySqlDatabase;
import io.yak.ops.testutils.TestNames;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Tag("mysql")
class MySqlSourceIT extends JdbcSourceTestBase {
    @RegisterExtension
    static final MySqlDatabase DATABASE = new MySqlDatabase();

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
                "BLOB",
                "timestamp",
                "DATETIME(6)");
    }

    @Override
    protected String widenColumn(String table, String column) {
        return "ALTER TABLE " + table + " MODIFY COLUMN " + column + " VARCHAR(72)";
    }

    @Test
    void mysqlCursorFetchReadsLargeTableInSmallBatches() throws Exception {
        String url = database().url();
        String user = database().username();
        String password = database().password();
        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions(url, user, password);
        JdbcConnectionOptions cursorOptions =
                new JdbcConnectionOptions(url, user, password, null, Map.of("useCursorFetch", "true"));
        JdbcDialect dialect = JdbcDialects.forUrl(url);
        String name = TestNames.unique("cursor").toUpperCase(java.util.Locale.ROOT);
        String tableSql = dialect.quoteIdentifier(name);
        TableId table;
        try (Connection setup = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                                connectionOptions)
                        .getConnection();
                Statement ddl = setup.createStatement()) {
            ddl.execute("DROP TABLE IF EXISTS " + tableSql);
            ddl.execute("CREATE TABLE " + tableSql + " (ID BIGINT PRIMARY KEY, LABEL VARCHAR(40))");
            table = new TableId(setup.getCatalog(), null, name);
            try (PreparedStatement insert = setup.prepareStatement("INSERT INTO " + tableSql + " VALUES (?, ?)")) {
                for (int index = 1; index <= 8192; index++) {
                    insert.setLong(1, index);
                    insert.setString(2, "row" + index);
                    insert.addBatch();
                    if (index % 256 == 0) {
                        insert.executeBatch();
                    }
                }
            }
        }
        try {
            Configuration config = new Configuration();
            config.set(JdbcSourceOptions.MAX_SPLITS_PER_TABLE, 1);
            config.set(JdbcSourceOptions.READER_FETCH_BATCH_SIZE, 64);
            config.set(JdbcSourceOptions.RESULT_SET_FETCH_SIZE, 32);
            JdbcSourceSplit split = new JdbcSplitPlanner(cursorOptions, dialect, config)
                    .plan(table, 0)
                    .getFirst();
            int count = 0;
            boolean finished = false;
            try (JdbcSourceSplitReader reader = new JdbcSourceSplitReader(cursorOptions, dialect, config)) {
                reader.addSplits(List.of(split));
                for (int attempt = 0; attempt < 200 && !finished; attempt++) {
                    var batch = reader.fetch();
                    int size = 0;
                    if (split.splitId().equals(batch.nextSplit())) {
                        while (batch.nextRecordFromSplit() != null) {
                            size++;
                        }
                    }
                    assertTrue(size <= 64);
                    count += size;
                    finished = batch.finishedSplits().contains(split.splitId());
                }
            }
            assertTrue(finished);
            assertEquals(8192, count);
        } finally {
            try (Connection cleanup = new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(
                                    connectionOptions)
                            .getConnection();
                    Statement ddl = cleanup.createStatement()) {
                ddl.execute("DROP TABLE IF EXISTS " + tableSql);
            }
        }
    }
}
