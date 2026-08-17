package io.yak.ops.business.dataset;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** Stable BI-consumption asset. Dataset versions freeze their own executable source contract. */
public record Dataset(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    String name,
    String description,
    DatasetStatus status,
    @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
    Instant createTime,
    Instant updateTime) {
}

enum DatasetStatus {
  ONLINE,
  OFFLINE
}

enum DatasetSourceType {
  QUERY_REVISION,
  SQL_QUERY,
  TABLE,
  VIEW
}

enum DatasetFieldDataType {
  STRING,
  NUMBER,
  DATE,
  DATETIME,
  BOOLEAN,
  UNKNOWN
}

enum DatasetFieldRole {
  DIMENSION,
  MEASURE
}

record DatasetVersion(
    @JsonSerialize(using = ToStringSerializer.class) long id,
    @JsonSerialize(using = ToStringSerializer.class) long datasetId,
    int versionNo,
    DatasetSourceType sourceType,
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskAssetId,
    @JsonSerialize(using = ToStringSerializer.class) long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String dataSourceId,
    String sql,
    String schemaSnapshot,
    Instant createTime) {

  /** Keeps existing QUERY_REVISION-focused tests/callers source compatible. */
  DatasetVersion(
      long id,
      long datasetId,
      int versionNo,
      DatasetSourceType sourceType,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String schemaSnapshot,
      Instant createTime) {
    this(
        id,
        datasetId,
        versionNo,
        sourceType,
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        null,
        null,
        schemaSnapshot,
        createTime);
  }
}

record DatasetField(
    String fieldId,
    @JsonSerialize(using = ToStringSerializer.class) long versionId,
    String physicalName,
    String displayName,
    DatasetFieldDataType dataType,
    boolean nullable,
    String description,
    DatasetFieldRole defaultRole,
    int sortOrder) {
}

record DatasetDetail(
    Dataset dataset,
    DatasetVersion currentVersion,
    List<DatasetVersion> versions,
    List<DatasetField> fields) {
}
