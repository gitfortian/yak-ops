package io.yak.ops.business.dataservice.service.support;

import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.stereotype.Component;

/** Validates read-only service SQL and compiles named parameters to JDBC placeholders. */
@Component
public class DataServiceSqlCompiler {

  public CompiledSql compile(String sql, Map<String, ?> parameters) {
    validateSelectOnly(sql);
    MapSqlParameterSource source = new MapSqlParameterSource(parameters == null ? Map.of() : parameters);
    ParsedSql parsed = NamedParameterUtils.parseSqlStatement(sql);
    for (String name : parsed.getParameterNames()) {
      if (!source.hasValue(name)) {
        throw new IllegalArgumentException("缺少请求参数：" + name);
      }
    }
    String jdbcSql = NamedParameterUtils.substituteNamedParameters(parsed, source);
    Object[] values = NamedParameterUtils.buildValueArray(parsed, source, null);
    return new CompiledSql(jdbcSql, List.of(values));
  }

  public List<String> parameterNames(String sql) {
    validateSelectOnly(sql);
    return NamedParameterUtils.parseSqlStatement(sql).getParameterNames().stream().distinct().toList();
  }

  public void validateSelectOnly(String sql) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL 不能为空");
    }
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(sql);
      if (statements.getStatements().size() != 1
          || !(statements.getStatements().get(0) instanceof Select)) {
        throw new IllegalArgumentException("数据服务第一阶段仅支持单条 SELECT 查询");
      }
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL 解析失败：" + exception.getMessage(), exception);
    }
  }

  public record CompiledSql(String sql, List<Object> parameters) {}
}
