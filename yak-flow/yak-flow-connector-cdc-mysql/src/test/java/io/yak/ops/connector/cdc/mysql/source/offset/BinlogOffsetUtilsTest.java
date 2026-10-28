package io.yak.ops.connector.cdc.mysql.source.offset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flink CDC effective-offset initialization keeps already resolved positions unchanged
 * and does not open a JDBC connection for a kind that requires no lookup.
 */
class BinlogOffsetUtilsTest {

    @Test
    void preservesSpecificOffsetsWithoutOpeningMySql() {
        BinlogOffset specific = BinlogOffset.ofBinlogFilePosition("mysql-bin.000011", 123L);
        assertThat(BinlogOffsetUtils.initializeEffectiveOffset(specific, null)).isSameAs(specific);
    }

    @Test
    void earliestOffsetDoesNotRequireMySqlConnection() {
        BinlogOffset result = BinlogOffsetUtils.initializeEffectiveOffset(BinlogOffset.ofEarliest(), null);
        assertThat(result.getFilename()).isEmpty();
        assertThat(result.getPosition()).isZero();
    }

    @Test
    void nonStoppingOffsetRemainsUnchanged() {
        BinlogOffset source = BinlogOffset.ofNonStopping();
        assertThat(BinlogOffsetUtils.initializeEffectiveOffset(source, null)).isSameAs(source);
        assertThat(BinlogOffsetUtils.isNonStoppingOffset(source)).isTrue();
    }
}
