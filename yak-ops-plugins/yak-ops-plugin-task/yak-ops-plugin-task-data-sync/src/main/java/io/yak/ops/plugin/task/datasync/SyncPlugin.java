package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.TaskValidationContext;

/** DATA_SYNC owns only definition parameters and physical metadata validation. */
public final class SyncPlugin implements TaskPlugin {
    public static final String TYPE = "DATA_SYNC";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public TaskParameters parseParameters(String json) {
        try {
            SyncParameters parameters = JSONUtils.parseObject(json, SyncParameters.class);
            if (parameters == null) throw new TaskPluginException("DATA_SYNC parameters must be an object");
            return parameters;
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC parameters");
        }
    }

    @Override
    public TaskParameters normalize(TaskParameters parameters, TaskValidationContext context) {
        return SyncValidator.validate((SyncParameters) parameters, context);
    }
}
