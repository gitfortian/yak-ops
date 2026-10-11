package io.yak.ops.plugin.task.api.validation;

/**
 * Workspace-authorized Datasource identity and selected default namespace without credentials.
 *
 * @param type canonical Datasource provider type
 * @param database database bound to the resource, when defined
 * @param schema schema bound to the resource, when defined
 */
public record DataSourceInfo(String type, String database, String schema) {}
