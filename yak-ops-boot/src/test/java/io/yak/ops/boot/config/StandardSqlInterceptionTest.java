package io.yak.ops.boot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import io.yak.ops.dao.mapper.modeling.standard.StandardMapper;
import io.yak.ops.security.config.YakSecurityProperties;
import java.util.Map;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

/** Standard catalog locking reads retain valid MySQL clause order through the production plugins. */
class StandardSqlInterceptionTest {
    @Test
    void catalogLockKeepsForUpdateLastWithSecurityEnabled() throws Exception {
        var properties = new YakSecurityProperties();
        properties.setApplicationName("standards-test");
        var plugins = new MybatisPlusConfiguration().mybatisPlusInterceptor(properties);
        var configuration = new MybatisConfiguration();
        configuration.addMapper(StandardMapper.class);
        var statement = configuration.getMappedStatement(StandardMapper.class.getName() + ".lockCatalog");
        var parameters = Map.<String, Object>of("workspaceId", "ws");
        var sql = statement.getBoundSql(parameters);

        assertTrue(sql.getSql().contains("yak_ops_standard_catalog"), sql::getSql);
        assertTrue(sql.getSql().trim().endsWith("FOR UPDATE"), sql::getSql);
        for (var plugin : plugins.getInterceptors()) {
            plugin.beforeQuery(null, statement, parameters, RowBounds.DEFAULT, null, sql);
            assertTrue(
                    sql.getSql().contains("yak_ops_standard_catalog"),
                    () -> plugin.getClass().getSimpleName() + ": " + sql.getSql());
            assertTrue(
                    sql.getSql().trim().endsWith("FOR UPDATE"),
                    () -> plugin.getClass().getSimpleName() + ": " + sql.getSql());
            assertFalse(
                    sql.getSql().contains("app_name"),
                    () -> plugin.getClass().getSimpleName() + ": " + sql.getSql());
        }
        assertFalse(sql.getSql().contains("app_name"), sql::getSql);
    }
}
