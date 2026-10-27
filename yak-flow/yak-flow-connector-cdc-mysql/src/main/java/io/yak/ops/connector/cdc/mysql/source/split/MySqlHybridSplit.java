package io.yak.ops.connector.cdc.mysql.source.split;

/**
 * Transitional grouping for initial snapshot assignment.
 *
 * <p>The source split identity is owned by {@link MySqlSplit}; the standalone
 * and initial readers share that contract.
 */
public abstract class MySqlHybridSplit extends MySqlSplit {

    protected MySqlHybridSplit(String splitId) {
        super(splitId);
    }
}
