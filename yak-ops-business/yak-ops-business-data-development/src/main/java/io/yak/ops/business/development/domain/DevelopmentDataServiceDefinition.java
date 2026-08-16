package io.yak.ops.business.development.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;

/** Authoring definition owned by a DATA_SERVICE node in Data Development. */
public record DevelopmentDataServiceDefinition(
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskAssetId,
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String serviceName,
    String path,
    String method,
    List<ParameterContract> parameters,
    List<ResponseFieldContract> responseFields,
    int maxRows,
    int timeoutSeconds,
    String description) {

  public DevelopmentDataServiceDefinition {
    parameters = parameters == null ? List.of() : List.copyOf(parameters);
    responseFields = responseFields == null ? List.of() : List.copyOf(responseFields);
  }

  /**
   * Lightweight v1 publish invariant.
   *
   * <p>A published Data Service Revision must be directly deployable by the current Runtime. Drafts
   * may still be edited freely; this validation is applied only when an immutable revision is
   * persisted.
   */
  public void validatePublishable() {
    if (!"GET".equalsIgnoreCase(method)) {
      throw new IllegalArgumentException("当前 Data Service Runtime 仅支持 GET");
    }
    if (responseFields.isEmpty()) {
      throw new IllegalArgumentException("发布前请先预览并确认响应字段 Contract");
    }
    for (ParameterContract parameter : parameters) {
      if (parameter == null) {
        throw new IllegalArgumentException("请求参数 Contract 不能为空");
      }
      if (!parameter.required()) {
        throw new IllegalArgumentException(
            "第一版 Runtime 仅支持必填请求参数，请将参数设为必填：" + parameter.name());
      }
      if ("OBJECT".equalsIgnoreCase(parameter.type())) {
        throw new IllegalArgumentException(
            "第一版 Runtime 请求参数暂不支持 OBJECT：" + parameter.name());
      }
    }
  }

  public record ParameterContract(
      String name,
      String type,
      boolean required,
      String description,
      String example) {}

  public record ResponseFieldContract(
      String name,
      String type,
      boolean nullable,
      String description,
      String example) {}
}
