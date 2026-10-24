package io.yak.ops.connector.cdc.base.source.meta.split;

import io.yak.ops.core.data.TableId;

/**
 * A completed snapshot chunk and its stream filtering watermark.
 *
 * <p>Offset and chunk boundary types remain owned by the database connector.
 *
 * @param <OffsetT> the database-specific source offset
 */
public interface FinishedSnapshotSplit<OffsetT> {

    TableId tableId();

    String splitId();

    OffsetT highWatermark();
}
