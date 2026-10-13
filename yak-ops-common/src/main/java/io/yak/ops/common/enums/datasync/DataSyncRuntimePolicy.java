package io.yak.ops.common.enums.datasync;

/**
 * Selects how effective runtime parameters are planned for synchronization.
 */
public enum DataSyncRuntimePolicy {

    /** Computes effective runtime settings from the current source and schema at execution creation. */
    AUTO,

    /** Uses the concrete runtime settings saved in the task definition. */
    FIXED
}
