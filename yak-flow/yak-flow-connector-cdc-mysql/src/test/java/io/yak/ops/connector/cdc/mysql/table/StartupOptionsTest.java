package io.yak.ops.connector.cdc.mysql.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Six Flink CDC startup modes are modeled while only the existing two can execute. */
class StartupOptionsTest {
    @Test
    void parsesEveryFlinkStartupModeWithoutChangingProductValues() {
        Map<String, StartupMode> expected = Map.of(
                "initial", StartupMode.INITIAL,
                "earliest-offset", StartupMode.EARLIEST_OFFSET,
                "latest-offset", StartupMode.LATEST_OFFSET,
                "specific-offset", StartupMode.SPECIFIC_OFFSETS,
                "timestamp", StartupMode.TIMESTAMP,
                "snapshot", StartupMode.SNAPSHOT);
        assertEquals(6, StartupMode.values().length);
        expected.forEach((name, mode) -> {
            var options = switch (mode) {
                case SPECIFIC_OFFSETS -> StartupOptions.specificOffset("mysql-bin.000001", 4L);
                case TIMESTAMP -> StartupOptions.timestamp(1700000000000L);
                default -> StartupOptions.from(name);
            };
            assertEquals(mode, options.startupMode());
            assertEquals(name, options.asModeString());
            if (mode == StartupMode.SPECIFIC_OFFSETS || mode == StartupMode.TIMESTAMP) {
                assertThrows(UnsupportedOperationException.class, () -> StartupOptions.of(mode));
            } else {
                assertEquals(options, StartupOptions.of(mode));
                assertEquals(options.hashCode(), StartupOptions.of(mode).hashCode());
            }
        });
        assertThrows(IllegalArgumentException.class, () -> StartupOptions.from("other"));
        assertThrows(IllegalArgumentException.class, () -> StartupOptions.from(null));
        assertThrows(UnsupportedOperationException.class, () -> StartupOptions.from("specific-offset"));
        assertThrows(UnsupportedOperationException.class, () -> StartupOptions.from("timestamp"));
    }

    @Test
    void distinguishesStreamOnlyFromSnapshotOnlyAndHybrid() {
        assertFalse(StartupOptions.initial().isStreamOnly());
        assertFalse(StartupOptions.initial().isSnapshotOnly());
        assertTrue(StartupOptions.latest().isStreamOnly());
        assertTrue(StartupOptions.of(StartupMode.SNAPSHOT).isSnapshotOnly());
        assertFalse(StartupOptions.of(StartupMode.SNAPSHOT).isStreamOnly());
        for (StartupMode mode : new StartupMode[] {
                StartupMode.EARLIEST_OFFSET, StartupMode.SPECIFIC_OFFSETS, StartupMode.TIMESTAMP
        }) {
            assertTrue(StartupOptions.of(mode).isStreamOnly());
            assertFalse(StartupOptions.of(mode).isSnapshotOnly());
        }
        assertNotEquals(StartupOptions.initial(), StartupOptions.latest());
    }

    @Test
    void preventsUnsupportedModesFromBecomingExecutableSources() {
        assertEquals(null, StartupOptions.initial().binlogOffset);
        assertEquals(io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffsetKind.LATEST,
                StartupOptions.latest().binlogOffset.getOffsetKind());
        assertEquals(100L, StartupOptions.specificOffset("mysql-bin.000002", 100L).binlogOffset.getPosition());
        assertEquals(1700000000L, StartupOptions.timestamp(1700000000000L).binlogOffset.getTimestampSec());
        StartupOptions.initial().requireSupported();
        StartupOptions.latest().requireSupported();
        for (StartupMode mode : new StartupMode[] {
                StartupMode.EARLIEST_OFFSET, StartupMode.SPECIFIC_OFFSETS,
                StartupMode.TIMESTAMP, StartupMode.SNAPSHOT
        }) {
            var unavailable = StartupOptions.of(mode);
            assertThrows(UnsupportedOperationException.class, unavailable::requireSupported);
        }
    }
}
