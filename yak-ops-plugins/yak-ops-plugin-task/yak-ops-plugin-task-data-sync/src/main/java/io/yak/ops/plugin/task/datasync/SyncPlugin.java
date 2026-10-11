package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.hocon.SyncHoconFormatter;
import io.yak.ops.plugin.task.datasync.hocon.SyncHoconParser;

/** DATA_SYNC Task plugin: pipeline parameters, parsing and physical metadata checks only. */
public final class SyncPlugin implements TaskPlugin {
    public static final String TYPE = "DATA_SYNC";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public int parameterSchemaVersion() {
        return 2;
    }

    @Override
    public TaskParameters parseParameters(String json) {
        try {
            SyncParameters parsed = JSONUtils.parseObject(json, SyncParameters.class);
            if (parsed == null) throw new TaskPluginException("DATA_SYNC requires a pipeline JSON object");
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON");
        }
    }

    @Override
    public TaskParameters parseParameters(String parameters, String format) {
        if ("HOCON".equalsIgnoreCase(format)) {
            return parseParameters(SyncHoconParser.parse(parameters));
        }
        return TaskPlugin.super.parseParameters(parameters, format);
    }

    @Override
    public String formatParameters(TaskParameters parameters, String format) {
        if ("HOCON".equalsIgnoreCase(format)) {
            if (!(parameters instanceof SyncParameters sync)) {
                throw new TaskPluginException("Invalid DATA_SYNC pipeline parameters");
            }
            return SyncHoconFormatter.format(sync);
        }
        return TaskPlugin.super.formatParameters(parameters, format);
    }

    @Override
    public TaskParameters normalize(TaskParameters parameters, TaskValidationContext context) {
        return SyncValidator.validate((SyncParameters) parameters, context);
    }
}
