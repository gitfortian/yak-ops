package io.yak.ops.connector.cdc.mysql.source.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MySqlSourceOptionsTest {
    @Test
    void retainsFlinkNamesTypesAndDefaults() {
        assertEquals("scan.incremental.snapshot.chunk.size",
                MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE.key());
        assertEquals(8096, MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_SIZE.defaultValue());
        assertEquals("scan.startup.mode", MySqlSourceOptions.SCAN_STARTUP_MODE.key());
        assertEquals("initial", MySqlSourceOptions.SCAN_STARTUP_MODE.defaultValue());
        assertEquals("server-id", MySqlSourceOptions.SERVER_ID.key());
        assertNull(MySqlSourceOptions.SERVER_ID.defaultValue());
    }
}
