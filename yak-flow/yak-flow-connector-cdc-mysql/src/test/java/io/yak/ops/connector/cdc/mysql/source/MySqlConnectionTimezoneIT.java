package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.testutils.MySqlDatabase;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Session-level time zone is isolated from the database global setting. */
@Tag("cdc")
@Timeout(120)
class MySqlConnectionTimezoneIT {
    @RegisterExtension
    static final MySqlDatabase DATABASE = MySqlDatabase.cdc();

    @Test
    void snapshotSessionUsesUtcWithoutChangingGlobalTimeZone() throws Exception {
        var db = DATABASE.metadata();
        var schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        var config = new MySqlCdcSourceConfig(
                db.host(), db.port(), db.username(), db.password(), 7001, "tz-test",
                Map.of(new TableId("yak_cdc_it", null, "orders"), schema), 256);
        String globalBefore;
        try (Connection connection = db.connect();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@global.time_zone")) {
            assertTrue(result.next());
            globalBefore = result.getString(1);
        }

        try (Connection connection = config.openSnapshotConnection();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@session.time_zone")) {
            assertTrue(result.next());
            assertTrue(List.of("+00:00", "UTC").contains(result.getString(1)));
        }
        try (Connection connection = db.connect();
                Statement sql = connection.createStatement();
                var result = sql.executeQuery("SELECT @@global.time_zone")) {
            assertTrue(result.next());
            assertEquals(globalBefore, result.getString(1));
        }
    }
}
