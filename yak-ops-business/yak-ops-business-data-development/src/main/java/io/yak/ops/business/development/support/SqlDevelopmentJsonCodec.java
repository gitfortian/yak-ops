package io.yak.ops.business.development.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.SqlParameterDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** JSON and digest boundary for durable SQL snapshots. */
@Component
public class SqlDevelopmentJsonCodec {

  private static final TypeReference<List<SqlParameterDefinition>> PARAMETER_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public SqlDevelopmentJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("SQL 数据开发 JSON 序列化失败", exception);
    }
  }

  public List<SqlParameterDefinition> readParameters(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return List.copyOf(objectMapper.readValue(json, PARAMETER_TYPE));
    } catch (Exception exception) {
      throw new IllegalStateException("SQL 参数快照解析失败", exception);
    }
  }

  public Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception exception) {
      throw new IllegalStateException("SQL 执行 JSON 解析失败", exception);
    }
  }

  public <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("SQL 快照解析失败", exception);
    }
  }

  public String digest(String sql, List<SqlParameterDefinition> parameters, Long dataSourceId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String canonical = dataSourceId + "\n" + sql + "\n" + write(parameters);
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SQL 快照摘要计算失败", exception);
    }
  }
}
