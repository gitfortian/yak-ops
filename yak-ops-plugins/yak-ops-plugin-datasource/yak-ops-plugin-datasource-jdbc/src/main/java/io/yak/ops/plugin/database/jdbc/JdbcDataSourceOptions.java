package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import java.util.Map;

/** Canonical JDBC datasource connection options shared by the JDBC providers. */
public final class JdbcDataSourceOptions {

    public static final Option<String> DB_TYPE = Options.key("dbType").stringType().noDefaultValue();
    public static final Option<String> JDBC_URL = Options.key("jdbcUrl").stringType().noDefaultValue();
    public static final Option<String> HOST = Options.key("host").stringType().noDefaultValue();
    public static final Option<String> DATABASE = Options.key("database").stringType().noDefaultValue();
    public static final Option<String> SCHEMA = Options.key("schema").stringType().noDefaultValue();
    public static final Option<String> USERNAME = Options.key("username").stringType().noDefaultValue();
    public static final Option<String> PASSWORD = Options.key("password").stringType().noDefaultValue();
    public static final Option<String> DRIVER_CLASS_NAME =
            Options.key("driverClassName").stringType().noDefaultValue();
    public static final Option<Map<String, String>> PROPERTIES =
            Options.key("properties").mapType().noDefaultValue();

    public static final Option<Boolean> SSH_ENABLED =
            Options.key("sshTunnel.enabled").booleanType().defaultValue(false);
    public static final Option<String> SSH_HOST =
            Options.key("sshTunnel.host").stringType().noDefaultValue();
    public static final Option<Integer> SSH_PORT =
            Options.key("sshTunnel.port").intType().defaultValue(22);
    public static final Option<String> SSH_USERNAME =
            Options.key("sshTunnel.username").stringType().noDefaultValue();
    public static final Option<String> SSH_AUTH_TYPE = Options.key("sshTunnel.authType")
            .singleChoice(String.class, java.util.List.of("PASSWORD", "PRIVATE_KEY"))
            .defaultValue("PASSWORD");
    public static final Option<String> SSH_PASSWORD =
            Options.key("sshTunnel.password").stringType().noDefaultValue();
    public static final Option<String> SSH_PRIVATE_KEY =
            Options.key("sshTunnel.privateKey").stringType().noDefaultValue();
    public static final Option<String> SSH_PASSPHRASE =
            Options.key("sshTunnel.passphrase").stringType().noDefaultValue();
    public static final Option<Boolean> SSH_STRICT_HOST_KEY_CHECKING =
            Options.key("sshTunnel.strictHostKeyChecking").booleanType().defaultValue(false);
    public static final Option<String> SSH_KNOWN_HOSTS =
            Options.key("sshTunnel.knownHosts").stringType().noDefaultValue();

    private JdbcDataSourceOptions() {}
}
