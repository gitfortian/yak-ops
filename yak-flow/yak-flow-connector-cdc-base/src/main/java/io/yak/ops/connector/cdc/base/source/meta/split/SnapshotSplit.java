package io.yak.ops.connector.cdc.base.source.meta.split;

import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.data.TableId;

/**
 * Finite CDC snapshot work for one source table.
 *
 * <p>Chunk key types, boundaries and checkpoint cursors remain database-specific.
 */
public interface SnapshotSplit extends SourceSplit {

    TableId tableId();
}
