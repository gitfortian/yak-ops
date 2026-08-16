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
