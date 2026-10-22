package io.yak.ops.boot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.dao.mapper.modeling.PlanningMapper;
import io.yak.ops.security.config.YakSecurityProperties;
import java.util.Map;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

/** Planning locking reads retain valid MySQL clause order through the production plugins. */
class PlanningSqlInterceptionTest {
    @Test
    void childLookupKeepsLimitBeforeForUpdateWithSecurityEnabled() throws Exception {
        var properties = new YakSecurityProperties();
        properties.setApplicationName("planning-test");
        var plugins = new MybatisPlusConfiguration().mybatisPlusInterceptor(properties);
        var configuration = new MybatisConfiguration();
        configuration.addMapper(PlanningMapper.class);
        var statement = configuration.getMappedStatement(PlanningMapper.class.getName() + ".child");

        for (var kind : new NodeKind[] {NodeKind.CATEGORY, NodeKind.DOMAIN}) {
            var parameters = Map.<String, Object>of("workspaceId", "ws", "kind", kind, "id", "parent");
            var sql = statement.getBoundSql(parameters);
            assertTrue(sql.getSql().contains("LIMIT 1 FOR UPDATE"));
            for (var plugin : plugins.getInterceptors()) {
                plugin.beforeQuery(null, statement, parameters, RowBounds.DEFAULT, null, sql);
                assertTrue(sql.getSql().contains("LIMIT 1 FOR UPDATE"),
                        () -> plugin.getClass().getSimpleName() + ": " + sql.getSql());
            }
            assertTrue(sql.getSql().contains("LIMIT 1 FOR UPDATE"), sql::getSql);
            assertFalse(sql.getSql().contains("FOR UPDATE LIMIT"), sql::getSql);
            assertTrue(sql.getSql().contains("workspace_id"), sql::getSql);
        }
    }

    @Test
    void securityUserReadsRetainApplicationScoping() throws Exception {
        var properties = new YakSecurityProperties();
        properties.setApplicationName("planning-test");
        var plugins = new MybatisPlusConfiguration().mybatisPlusInterceptor(properties);
        var configuration = new MybatisConfiguration();
        configuration.addMapper(PlanningMapper.class);
        var statement = new MappedStatement.Builder(configuration,
                "io.yak.ops.dao.mapper.security.UserMapper.selectById",
                new StaticSqlSource(configuration, "SELECT id FROM yak_security_user WHERE id = ?"),
                SqlCommandType.SELECT).build();
        var parameters = Map.of("id", "user");
        var sql = statement.getBoundSql(parameters);
        for (var plugin : plugins.getInterceptors()) {
            plugin.beforeQuery(null, statement, parameters, RowBounds.DEFAULT, null, sql);
        }
        assertTrue(sql.getSql().contains("app_name = 'planning-test'"), sql::getSql);
    }
}
