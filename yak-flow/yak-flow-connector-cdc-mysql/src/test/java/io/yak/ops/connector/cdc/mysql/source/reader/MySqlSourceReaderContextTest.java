package io.yak.ops.connector.cdc.mysql.source.reader;

import io.yak.ops.core.api.connector.source.SourceReaderContext;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the source/split reader coordination flags ported from Flink CDC. */
class MySqlSourceReaderContextTest {

    @Test
    void tracksBinlogAssignmentAndSuspendWakeup() {
        SourceReaderContext delegate = (SourceReaderContext) Proxy.newProxyInstance(
                SourceReaderContext.class.getClassLoader(),
                new Class<?>[] {SourceReaderContext.class},
                (proxy, method, args) -> null);
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
