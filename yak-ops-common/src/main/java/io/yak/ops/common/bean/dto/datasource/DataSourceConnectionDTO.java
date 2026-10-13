package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

/**
 * Carries structured connection parameters from Datasource HTTP requests.
 *
 * <p>Plugins own JDBC URL generation, vendor-specific parsing and normalization; the HTTP layer only passes the submitted structure.
 */
@Data
public class DataSourceConnectionDTO {

    /** Provider-native JDBC URL; required for Oracle raw URL mode and optional for structured connections. */
    private String jdbcUrl;

    /** Database host or IP address used by structured connection mode. */
    private String host;

    /** Database port used by structured connection mode. */
    private Integer port;

    /** Default database or service name used by structured connection mode. */
    private String database;

    /** Database authentication username. */
    @NotBlank(message = "数据库用户名不能为空")
    private String username;

    /** Database password; a masked value on edit means the saved secret should be retained. */
    private String password;

    /** Provider-specific JDBC driver identifier, currently used only for MySQL. */
    private String driverId;

    /** Additional connection properties passed through to the selected provider. */
    private Map<String, String> properties;
}
