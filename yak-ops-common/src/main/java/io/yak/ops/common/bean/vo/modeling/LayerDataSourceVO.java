package io.yak.ops.common.bean.vo.modeling;

/**
 * Bindable physical datasource without connection parameters or credentials.
 *
 * @param id datasource identity
 * @param name Workspace-unique datasource name
 * @param dbType registered datasource plugin type
 * @param environment operating environment name
 * @param connStatus last verified connection status name
 */
public record LayerDataSourceVO(String id, String name, String dbType, String environment, String connStatus) {}
