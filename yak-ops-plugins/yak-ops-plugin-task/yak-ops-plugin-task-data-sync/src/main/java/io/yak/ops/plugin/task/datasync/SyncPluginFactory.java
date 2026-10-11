package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginFactory;

/**
 * Registers the DATA_SYNC Task type through Java ServiceLoader.
 */
public final class SyncPluginFactory implements TaskPluginFactory {

    @Override
    public String type() {
        return SyncPlugin.TYPE;
    }

    @Override
    public TaskPlugin create() {
        return new SyncPlugin();
    }
}
