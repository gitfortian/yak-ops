package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Data;

/**
 * 数据源结构化连接参数。
 *
 * <p>HTTP 层只传递结构化连接信息；JDBC URL 生成、Provider 参数解释与规范化由数据源插件负责。</p>
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Data
public class DataSourceConnectionDTO {

    /** Provider 原生 JDBC URL；Oracle 直接使用该字段，结构化数据源可为空。 */
    private String jdbcUrl;

    /** 数据库主机名或 IP；结构化连接模式使用。 */
    private String host;

    /** 数据库端口；结构化连接模式使用。 */
    private Integer port;

    /** 默认数据库或服务名；结构化连接模式使用。 */
    private String database;

    /** 数据库登录用户名。 */
    @NotBlank(message = "数据库用户名不能为空")
    private String username;

    /** 数据库登录密码；编辑时允许提交遮罩值，由后端保留已保存密钥。 */
    private String password;

    /** Provider 自己解释的 JDBC Driver 选择标识；当前仅 MySQL 使用。 */
    private String driverId;

    /** 透传给对应 Provider 的高级连接属性。 */
    private Map<String, String> properties;
}
