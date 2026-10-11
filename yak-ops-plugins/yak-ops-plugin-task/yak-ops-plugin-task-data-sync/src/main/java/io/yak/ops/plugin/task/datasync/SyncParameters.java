package io.yak.ops.plugin.task.datasync;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPluginException;

/**
 * Defines single-table parameters for the DATA_SYNC plugin; generic Task Definitions own name, versions and publication.
 *
 * <p>Runtime and retry payloads must be JSON objects without credentials. Workspace authorization and physical schema access are delegated to the trusted host.
 *
 * @param syncType OFFLINE bounded sync or REALTIME continuous sync
 * @param writeMode target mode; REALTIME currently allows APPEND only
 * @param sourceDataSourceId stable source identifier without credentials
 * @param sourceDatabase source database, absent when unsupported
 * @param sourceSchema source schema, absent when unsupported
 * @param sourceTable physical source table name
 * @param targetDataSourceId stable target identifier without credentials
 * @param targetDatabase target database, absent when unsupported
 * @param targetSchema target schema, absent when unsupported
 * @param targetTable physical target table name
 * @param runtimeConfig optional offline execution settings JSON object
 * @param realtimeConfig optional realtime CDC settings JSON object
 * @param retryPolicy optional business retry settings JSON object
 */
public record SyncParameters(
        DataSyncType syncType,
        DataSyncWriteMode writeMode,
        String sourceDataSourceId,
        String sourceDatabase,
        String sourceSchema,
        String sourceTable,
        String targetDataSourceId,
        String targetDatabase,
        String targetSchema,
        String targetTable,
        JsonNode runtimeConfig,
        JsonNode realtimeConfig,
        JsonNode retryPolicy)
        implements TaskParameters {

    @Override
    public void validate() {
        if (syncType == null || writeMode == null) {
            throw new TaskPluginException("DATA_SYNC requires syncType and writeMode");
        }
        if (syncType == DataSyncType.REALTIME && writeMode != DataSyncWriteMode.APPEND) {
            throw new TaskPluginException("REALTIME DATA_SYNC requires APPEND write mode");
        }
        requireText(sourceDataSourceId, "sourceDataSourceId", 64);
        requireText(sourceTable, "sourceTable", 128);
        requireText(targetDataSourceId, "targetDataSourceId", 64);
        requireText(targetTable, "targetTable", 128);
        checkOptionalText(sourceDatabase, "sourceDatabase", 128);
        checkOptionalText(sourceSchema, "sourceSchema", 128);
        checkOptionalText(targetDatabase, "targetDatabase", 128);
        checkOptionalText(targetSchema, "targetSchema", 128);
        checkObject(runtimeConfig, "runtimeConfig");
        checkObject(realtimeConfig, "realtimeConfig");
        checkObject(retryPolicy, "retryPolicy");
        rejectSecrets(runtimeConfig);
        rejectSecrets(realtimeConfig);
        rejectSecrets(retryPolicy);
    }

    private static void rejectSecrets(JsonNode node) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey().toLowerCase(java.util.Locale.ROOT).replace("_", "");
                if (key.contains("password")
                        || key.contains("secret")
                        || key.contains("token")
                        || key.contains("credential")
                        || key.contains("privatekey")
                        || key.contains("connectionjson")
                        || key.contains("jdbcurl")) {
                    throw new TaskPluginException("Credentials must not be stored in task parameters");
                }
                rejectSecrets(entry.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) rejectSecrets(child);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (StringUtils.isBlank(value)) {
            throw new TaskPluginException("DATA_SYNC requires " + field);
        }
        checkOptionalText(value, field, maxLength);
    }

    private static void checkOptionalText(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new TaskPluginException("DATA_SYNC " + field + " exceeds maximum length " + maxLength);
        }
    }

    private static void checkObject(JsonNode value, String field) {
        if (value != null && !value.isNull() && !value.isObject()) {
            throw new TaskPluginException("DATA_SYNC " + field + " must be a JSON object");
        }
    }
}
