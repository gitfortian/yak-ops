package io.yak.ops.business.dataservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceDocumentationMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceDocumentationPO;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Parameter/response documentation and single-service OpenAPI generation. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceDocumentationService {

  private static final Set<String> PARAMETER_TYPES =
      Set.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME");
  private static final Set<String> RESPONSE_TYPES =
      Set.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "OBJECT");

  private final DataServiceDocumentationMapper documentationMapper;
  private final DataServiceService dataServiceService;
  private final ObjectMapper objectMapper;

  public ApiDocumentation get(Long apiId) {
    ApiView api = dataServiceService.get(apiId);
    DataServiceDocumentationPO stored = documentationMapper.selectById(apiId);
    return toView(api, stored);
  }

  @Transactional
  public ApiDocumentation save(Long apiId, DocumentationInput input) {
    if (input == null) throw new IllegalArgumentException("API 文档配置不能为空");
    ApiView api = dataServiceService.get(apiId);
    List<ParameterDoc> parameters = normalizeParameters(api.parameterNames(), input.parameters());
    List<ResponseFieldDoc> responseFields = normalizeResponseFields(input.responseFields());

    DataServiceDocumentationPO po = documentationMapper.selectById(apiId);
    boolean create = po == null;
    if (create) {
      po = new DataServiceDocumentationPO();
      po.setApiId(apiId);
    }
    po.setSqlHash(sqlHash(api.sql()));
    po.setParameterSchemaJson(json(parameters));
    po.setResponseSchemaJson(json(responseFields));
    po.setUpdateTime(LocalDateTime.now());
    if (create) documentationMapper.insert(po);
    else documentationMapper.updateById(po);
    return toView(api, po);
  }

  @Transactional
  public void deleteForApi(Long apiId) {
    if (apiId != null) documentationMapper.deleteById(apiId);
  }

  public Map<String, Object> openApi(Long apiId) {
    ApiDocumentation doc = get(apiId);
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("openapi", "3.0.3");
    root.put("info", map(
        "title", doc.name(),
        "version", "1.0.0",
        "description", StringUtils.hasText(doc.description()) ? doc.description() : "Yak Ops Data Service"));
    root.put("x-yak-documented", doc.documented());
    root.put("x-yak-schema-stale", doc.schemaStale());

    List<Map<String, Object>> parameters = new ArrayList<>();
    for (ParameterDoc parameter : doc.parameters()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", parameter.name());
      item.put("in", "query");
      item.put("required", true);
      if (StringUtils.hasText(parameter.description())) item.put("description", parameter.description());
      item.put("schema", schema(parameter.type(), false));
      if (StringUtils.hasText(parameter.example())) item.put("example", parameter.example());
      parameters.add(item);
    }

    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("summary", doc.name());
    operation.put("operationId", "dataService_" + doc.apiId());
    operation.put("x-yak-schema-stale", doc.schemaStale());
    if (StringUtils.hasText(doc.description())) operation.put("description", doc.description());
    operation.put("parameters", parameters);
    if ("API_KEY".equals(doc.authMode())) {
      operation.put("security", List.of(Map.of("ApiKeyAuth", List.of())));
    }
    operation.put("responses", responses(doc.schemaStale() ? List.of() : doc.responseFields()));
    root.put("paths", Map.of(doc.runtimePath(), Map.of("get", operation)));

    if ("API_KEY".equals(doc.authMode())) {
      root.put("components", Map.of(
          "securitySchemes", Map.of(
              "ApiKeyAuth", map(
                  "type", "apiKey",
                  "in", "header",
                  "name", "X-API-Key"))));
    }
    return root;
  }

  private ApiDocumentation toView(ApiView api, DataServiceDocumentationPO stored) {
    List<ParameterDoc> savedParameters = stored == null
        ? List.of()
        : read(stored.getParameterSchemaJson(), new TypeReference<List<ParameterDoc>>() {});
    List<ResponseFieldDoc> savedResponseFields = stored == null
        ? List.of()
        : read(stored.getResponseSchemaJson(), new TypeReference<List<ResponseFieldDoc>>() {});
    List<ParameterDoc> parameters = mergeCurrentParameters(api.parameterNames(), savedParameters);
    boolean documented = stored != null;
    boolean stale = documented
        && StringUtils.hasText(stored.getSqlHash())
        && !stored.getSqlHash().equals(sqlHash(api.sql()));
    return new ApiDocumentation(
        api.id(),
        api.name(),
        api.runtimePath(),
        api.authMode(),
        api.description(),
        documented,
        stale,
        parameters,
        savedResponseFields,
        stored == null ? null : stored.getUpdateTime());
  }

  private List<ParameterDoc> normalizeParameters(
      List<String> currentNames,
      List<ParameterDoc> input) {
    List<ParameterDoc> supplied = input == null ? List.of() : input;
    Set<String> current = new LinkedHashSet<>(currentNames == null ? List.of() : currentNames);
    Set<String> seen = new LinkedHashSet<>();
    Map<String, ParameterDoc> byName = new LinkedHashMap<>();
    for (ParameterDoc parameter : supplied) {
      if (parameter == null || !StringUtils.hasText(parameter.name())) {
        throw new IllegalArgumentException("参数名称不能为空");
      }
      String name = parameter.name().trim();
      if (!current.contains(name)) throw new IllegalArgumentException("SQL 中不存在参数：" + name);
      if (!seen.add(name)) throw new IllegalArgumentException("参数文档重复：" + name);
      byName.put(name, new ParameterDoc(
          name,
          normalizeType(parameter.type(), PARAMETER_TYPES),
          true,
          trim(parameter.description(), 500),
          trim(parameter.example(), 500)));
    }
    List<ParameterDoc> result = new ArrayList<>();
    for (String name : current) {
      result.add(byName.getOrDefault(name, new ParameterDoc(name, "STRING", true, null, null)));
    }
    return result;
  }

  private List<ParameterDoc> mergeCurrentParameters(
      List<String> currentNames,
      List<ParameterDoc> saved) {
    Map<String, ParameterDoc> savedByName = (saved == null ? List.<ParameterDoc>of() : saved)
        .stream()
        .filter(item -> item != null && StringUtils.hasText(item.name()))
        .collect(Collectors.toMap(
            item -> item.name().trim(),
            Function.identity(),
            (first, ignored) -> first,
            LinkedHashMap::new));
    List<ParameterDoc> result = new ArrayList<>();
    for (String name : currentNames == null ? List.<String>of() : currentNames) {
      ParameterDoc item = savedByName.get(name);
      result.add(item == null
          ? new ParameterDoc(name, "STRING", true, null, null)
          : new ParameterDoc(
              name,
              normalizeType(item.type(), PARAMETER_TYPES),
              true,
              trim(item.description(), 500),
              trim(item.example(), 500)));
    }
    return result;
  }

  private List<ResponseFieldDoc> normalizeResponseFields(List<ResponseFieldDoc> input) {
    List<ResponseFieldDoc> supplied = input == null ? List.of() : input;
    if (supplied.size() > 200) throw new IllegalArgumentException("响应字段不能超过 200 个");
    Set<String> seen = new LinkedHashSet<>();
    List<ResponseFieldDoc> result = new ArrayList<>();
    for (ResponseFieldDoc field : supplied) {
      if (field == null || !StringUtils.hasText(field.name())) {
        throw new IllegalArgumentException("响应字段名称不能为空");
      }
      String name = field.name().trim();
      if (!seen.add(name)) throw new IllegalArgumentException("响应字段重复：" + name);
      result.add(new ResponseFieldDoc(
          name,
          normalizeType(field.type(), RESPONSE_TYPES),
          field.nullable(),
          trim(field.description(), 500),
          trim(field.example(), 500)));
    }
    return result;
  }

  private Map<String, Object> responses(List<ResponseFieldDoc> fields) {
    Map<String, Object> rowProperties = new LinkedHashMap<>();
    for (ResponseFieldDoc field : fields == null ? List.<ResponseFieldDoc>of() : fields) {
      Map<String, Object> fieldSchema = schema(field.type(), field.nullable());
      if (StringUtils.hasText(field.description())) fieldSchema.put("description", field.description());
      if (StringUtils.hasText(field.example())) fieldSchema.put("example", field.example());
      rowProperties.put(field.name(), fieldSchema);
    }

    Map<String, Object> dataProperties = new LinkedHashMap<>();
    dataProperties.put("columns", map("type", "array", "items", Map.of("type", "string")));
    dataProperties.put("rows", map(
        "type", "array",
        "items", map("type", "object", "properties", rowProperties, "additionalProperties", true)));
    dataProperties.put("truncated", Map.of("type", "boolean"));
    dataProperties.put("rowCount", map("type", "integer", "format", "int32"));
    dataProperties.put("durationMs", map("type", "integer", "format", "int64"));

    Map<String, Object> envelope = map(
        "type", "object",
        "properties", map(
            "code", map("type", "integer", "format", "int32"),
            "data", map("type", "object", "properties", dataProperties),
            "msg", Map.of("type", "string"),
            "message", Map.of("type", "string")));

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", map(
        "description", "Success",
        "content", Map.of("application/json", Map.of("schema", envelope))));
    responses.put("401", Map.of("description", "Missing or invalid API Key"));
    responses.put("429", Map.of("description", "Rate limit exceeded"));
    responses.put("503", Map.of("description", "Runtime circuit breaker is open"));
    return responses;
  }

  private Map<String, Object> schema(String type, boolean nullable) {
    String normalized = normalizeType(type, RESPONSE_TYPES);
    Map<String, Object> schema = new LinkedHashMap<>();
    switch (normalized) {
      case "INTEGER" -> {
        schema.put("type", "integer");
        schema.put("format", "int64");
      }
      case "NUMBER" -> {
        schema.put("type", "number");
        schema.put("format", "double");
      }
      case "BOOLEAN" -> schema.put("type", "boolean");
      case "DATE" -> {
        schema.put("type", "string");
        schema.put("format", "date");
      }
      case "DATETIME" -> {
        schema.put("type", "string");
        schema.put("format", "date-time");
      }
      case "OBJECT" -> {
        schema.put("type", "object");
        schema.put("additionalProperties", true);
      }
      default -> schema.put("type", "string");
    }
    if (nullable) schema.put("nullable", true);
    return schema;
  }

  private String normalizeType(String type, Set<String> allowed) {
    String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : "STRING";
    if (!allowed.contains(normalized)) throw new IllegalArgumentException("不支持的 Schema 类型：" + normalized);
    return normalized;
  }

  private String sqlHash(String sql) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest((sql == null ? "" : sql).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法计算 SQL 文档指纹", exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("API 文档序列化失败", exception);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    if (!StringUtils.hasText(value)) {
      try {
        return objectMapper.readValue("[]", type);
      } catch (Exception exception) {
        throw new IllegalStateException("API 文档初始化失败", exception);
      }
    }
    try {
      return objectMapper.readValue(value, type);
    } catch (Exception exception) {
      throw new IllegalStateException("API 文档内容无法解析", exception);
    }
  }

  private String trim(String value, int maxLength) {
    if (!StringUtils.hasText(value)) return null;
    String result = value.trim();
    return result.length() <= maxLength ? result : result.substring(0, maxLength);
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index + 1 < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  public record ParameterDoc(
      String name,
      String type,
      boolean required,
      String description,
      String example) {}

  public record ResponseFieldDoc(
      String name,
      String type,
      boolean nullable,
      String description,
      String example) {}

  public record DocumentationInput(
      List<ParameterDoc> parameters,
      List<ResponseFieldDoc> responseFields) {}

  public record ApiDocumentation(
      Long apiId,
      String name,
      String runtimePath,
      String authMode,
      String description,
      boolean documented,
      boolean schemaStale,
      List<ParameterDoc> parameters,
      List<ResponseFieldDoc> responseFields,
      LocalDateTime updateTime) {}
}
