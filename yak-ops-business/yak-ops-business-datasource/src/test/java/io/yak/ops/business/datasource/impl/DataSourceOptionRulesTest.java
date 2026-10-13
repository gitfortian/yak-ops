package io.yak.ops.business.datasource.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.vo.datasource.DataSourceOptionRulesVO;
import org.junit.jupiter.api.Test;

/** Verifies datasource REST metadata is derived exclusively from provider OptionRules. */
class DataSourceOptionRulesTest {

    private final DataSourceServiceImpl service = new DataSourceServiceImpl();

    @Test
    void readsPortDefaultsAndDriverChoicesFromRegisteredPlugins() {
        DataSourceOptionRulesVO mysql = service.queryOptionRules("MYSQL");
        DataSourceOptionRulesVO postgres = service.queryOptionRules("POSTGRES");
        DataSourceOptionRulesVO oracle = service.queryOptionRules("ORACLE");

        assertEquals(3306, value(mysql, "port").defaultValue());
        assertEquals(5432, value(postgres, "port").defaultValue());
        assertEquals(1521, value(oracle, "port").defaultValue());
        assertEquals("POSTGRE_SQL", postgres.dbType());
        assertEquals(java.util.List.of("AUTO", "MYSQL_8", "MYSQL_5"), value(mysql, "driverId").allowedValues());
    }

    @Test
    void doesNotPublishSavedPasswordsOrAnyConnectionValues() {
        DataSourceOptionRulesVO mysql = service.queryOptionRules("MYSQL");

        assertNull(value(mysql, "password").defaultValue());
        assertNull(value(mysql, "sshTunnel.password").defaultValue());
        assertNull(value(mysql, "sshTunnel.privateKey").defaultValue());
        assertTrue(mysql.options().stream().noneMatch(option -> option.key().equals("private-secret")));
    }

    private static DataSourceOptionRulesVO.OptionDefinitionVO value(
            DataSourceOptionRulesVO rules, String key) {
        return rules.options().stream().filter(option -> option.key().equals(key)).findFirst().orElseThrow();
    }
}
