package io.yak.ops.connector.cdc.base.source.meta.split;

import io.yak.ops.core.api.connector.source.SourceSplit;

/**
 * Unbounded CDC stream work.
 *
 * <p>Each connector retains its own offset type and schema history format.
 */
public interface StreamSplit extends SourceSplit {}
