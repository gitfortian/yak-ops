import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

const model = readFileSync(
  new URL("../apps/web/app/datasource/form/model.ts", import.meta.url),
  "utf8",
);
const compiled = ts.transpileModule(model, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
const exports = {};
vm.runInNewContext(compiled.outputText, {
  exports,
  require(specifier) {
    assert.equal(specifier, "../constants");
    return {
      COMMON_DB_OPTIONS: [
        { label: "MySQL", value: "MYSQL", category: "RELATIONAL" },
        { label: "Oracle", value: "ORACLE", category: "RELATIONAL" },
        { label: "PostgreSQL", value: "POSTGRE_SQL", category: "RELATIONAL" },
      ],
      JDBC_DEFAULT_PORTS: { MYSQL: 3306, ORACLE: 1521, POSTGRE_SQL: 5432 },
      normalizeDataSourceType: (value) => {
        const normalized = String(value || "")
          .trim()
          .toUpperCase();
        return normalized === "POSTGRESQL" || normalized === "POSTGRES"
          ? "POSTGRE_SQL"
          : normalized;
      },
    };
  },
});

const { parseOriginalJson, buildJdbcPreview, frequentDataSourceOptions } = exports;

const oracle = parseOriginalJson({
  dbType: "ORACLE",
  originalJson: JSON.stringify({
    host: "oracle.local",
    port: 1521,
    database: "ORCL",
    username: "user",
  }),
});
assert.equal(oracle.jdbcUrl, "jdbc:oracle:thin:@//oracle.local:1521/ORCL");
assert.equal(oracle.username, "user");

const mysql = parseOriginalJson({
  dbType: "MYSQL",
  originalJson: JSON.stringify({
    host: "mysql.local",
    port: 3307,
    database: "warehouse",
    driverId: "MYSQL_5",
    properties: { connectionTimeZone: "SERVER" },
  }),
});
assert.equal(mysql.port, "3307");
assert.equal(mysql.driverId, "MYSQL_5");
assert.equal(mysql.properties[0].value, "SERVER");

const fallback = parseOriginalJson({
  dbType: "POSTGRE_SQL",
  jdbcUrl: "jdbc:postgresql://pg.local:5434/app",
  originalJson: "{invalid",
});
assert.equal(fallback.host, "pg.local");
assert.equal(fallback.port, "5434");
assert.equal(fallback.database, "app");

assert.equal(
  buildJdbcPreview({
    dbType: "MYSQL",
    host: "mysql.local",
    port: "3306",
    database: "warehouse",
    properties: [{ key: "connectionTimeZone", value: "Asia/Shanghai" }],
  }),
  "jdbc:mysql://mysql.local:3306/warehouse?connectionTimeZone=Asia%2FShanghai",
);

const popular = frequentDataSourceOptions([
  { itemKey: "POSTGRES", useCount: 3, lastUsedTime: "2026-10-20T00:00:00Z" },
  { itemKey: "MYSQL", useCount: 8, lastUsedTime: "2026-10-19T00:00:00Z" },
]);
assert.equal(popular[0].value, "MYSQL");
assert.equal(popular[1].value, "POSTGRE_SQL");

console.log("Data source model tests passed");
