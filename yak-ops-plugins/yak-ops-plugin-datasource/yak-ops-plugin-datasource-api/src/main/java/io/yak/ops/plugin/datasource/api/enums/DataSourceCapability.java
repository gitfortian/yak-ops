package io.yak.ops.plugin.datasource.api.enums;

/**
 * Declares stable capabilities supported by a datasource provider.
 */
public enum DataSourceCapability {

    /** Provider can perform connection availability tests. */
    CONNECTION_TEST,

    /** Provider can create database connections through SSH local port forwarding. */
    SSH_TUNNEL
}
