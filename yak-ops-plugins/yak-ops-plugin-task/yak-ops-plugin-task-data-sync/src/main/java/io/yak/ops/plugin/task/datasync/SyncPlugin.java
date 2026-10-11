package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.ParameterFormat;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.datasync.hocon.SyncHoconFormatter;
import io.yak.ops.plugin.task.datasync.hocon.SyncHoconParser;

/**
 * DATA_SYNC definition plugin for bounded JDBC and continuous MySQL CDC pipelines.
 *
 * <p>Parsing rejects malformed local definitions. Resource checks use the caller's Workspace
 * metadata but never rewrite submitted parameters or create YakFlow execution objects.
 */
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
    public TaskParameters parse(String content, ParameterFormat format) {
        SyncParameters parameters;
        if (format == ParameterFormat.HOCON) {
            parameters = SyncHoconParser.parse(content);
        } else if (format == ParameterFormat.JSON) {
            try {
                parameters = JSONUtils.parseObject(content, SyncParameters.class);
            } catch (IllegalArgumentException exception) {
                throw new TaskPluginException("Invalid DATA_SYNC pipeline JSON");
            }
        } else {
            throw new TaskPluginException("Unsupported DATA_SYNC parameter format");
        }
        SyncValidator.checkParameters(parameters);
        return parameters;
    }

    @Override
    public void validate(TaskParameters parameters, TaskValidationContext context) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        SyncValidator.checkResources(pipeline, context);
    }

    @Override
    public String format(TaskParameters parameters, ParameterFormat format) {
        if (!(parameters instanceof SyncParameters pipeline)) {
            throw new TaskPluginException("Invalid DATA_SYNC parameter type");
        }
        if (format == ParameterFormat.HOCON) {
            return SyncHoconFormatter.format(pipeline);
        }
        if (format == ParameterFormat.JSON) {
            return JSONUtils.toJson(pipeline);
        }
        throw new TaskPluginException("Unsupported DATA_SYNC parameter format");
    }
}
