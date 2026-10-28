package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Tests the source/split reader coordination flags ported from Flink CDC. */
class MySqlSourceReaderContextTest {

    @Test
    void tracksBinlogAssignmentAndSuspendWakeup() {
        SourceReaderContext delegate = mock(SourceReaderContext.class);
        MySqlSourceReaderContext context = new MySqlSourceReaderContext(delegate);
        assertThat(context.getSourceReaderContext()).isSameAs(delegate);
        assertThat(context.isHasAssignedBinlogSplit()).isFalse();
        assertThat(context.isBinlogSplitReaderSuspended()).isFalse();

        context.setHasAssignedBinlogSplit(true);
        context.suspendBinlogSplitReader();
        assertThat(context.isHasAssignedBinlogSplit()).isTrue();
        assertThat(context.isBinlogSplitReaderSuspended()).isTrue();

        context.wakeupSuspendedBinlogSplitReader();
        assertThat(context.isBinlogSplitReaderSuspended()).isFalse();
        assertThat(context.isHasAssignedBinlogSplit()).isTrue();
    }
}
