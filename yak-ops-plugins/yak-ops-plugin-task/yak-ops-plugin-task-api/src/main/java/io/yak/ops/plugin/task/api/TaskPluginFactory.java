package io.yak.ops.plugin.task.api;

/**
 * Provides ServiceLoader discovery of stable Task plugin types and their implementations.
 */
public interface TaskPluginFactory {

    /** Returns a stable, extensible Task type such as DATA_SYNC. */
    String type();

    /** Creates a parameter-handling plugin without starting business Task execution. */
    TaskPlugin create();
}
