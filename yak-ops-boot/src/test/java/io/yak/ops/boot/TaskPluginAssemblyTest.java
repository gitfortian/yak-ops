package io.yak.ops.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.yak.ops.boot.config.TaskPluginConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.datasync.SyncPlugin;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Verifies that Boot assembles one Task registry with the installed DATA_SYNC plugin. */
class TaskPluginAssemblyTest {

    @Test
    void loadsTaskPluginThroughBootConfiguration() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(TaskPluginConfiguration.class)) {
            TaskPluginRegistry registry = context.getBean(TaskPluginRegistry.class);
            assertEquals(1, registry.types().size());
            assertInstanceOf(SyncPlugin.class, registry.get(SyncPlugin.TYPE));
        }
    }
}
