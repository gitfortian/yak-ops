package io.yak.ops.connector.cdc.mysql.source.assigners.state;

import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplit;
import java.util.Objects;

/**
 * Describes whether the single unbounded Binlog split is pending or reader-owned.
 *
 * <p>An assigned split is checkpointed by its Reader and Runtime assignment tracker,
 * not duplicated into the enumerator state.
 */
public record MySqlPendingSplitsState(boolean splitAssigned, MySqlBinlogSplit pendingSplit)
        implements PendingSplitsState {

    public MySqlPendingSplitsState {
        if (splitAssigned == (pendingSplit != null)) {
            throw new IllegalArgumentException("Invalid MySQL Binlog assignment state");
        }
    }
}
