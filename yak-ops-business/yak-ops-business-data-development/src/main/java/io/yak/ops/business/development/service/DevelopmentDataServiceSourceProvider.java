package io.yak.ops.business.development.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.development.domain.DevelopmentReleaseDetail;
import io.yak.ops.business.development.domain.DevelopmentReleasePage;
import io.yak.ops.business.development.domain.DevelopmentReleaseSummary;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Exposes immutable Data Development SQL releases through the generic Data Service source port. */
@Component
@ConditionalOnDataSourceEnabled
public class DevelopmentDataServiceSourceProvider implements DataServiceSourceProvider {

  public static final String SOURCE_TYPE = "DATA_DEVELOPMENT_RELEASE";
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private final DevelopmentReleaseService releaseService;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceSourceProvider(
      DevelopmentReleaseService releaseService,
      ObjectMapper objectMapper) {
    this.releaseService = releaseService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String sourceType() {
    return SOURCE_TYPE;
  }

  @Override
  public SourcePage list(int pageNo, int pageSize, String keyword) {
    DevelopmentReleasePage page = releaseService.page(pageNo, pageSize, "ONLINE", "SQL", keyword);
    List<SourceDescriptor> records = page.records().stream()
        .map(summary -> resolve(Long.toString(summary.assetId())).descriptor())
        .toList();
    return new SourcePage(records, page.total(), page.pageNo(), page.pageSize());
  }

  @Override
  public ResolvedSource resolve(String sourceRef) {
    long assetId = parseAssetId(sourceRef);
    DevelopmentReleaseDetail release = releaseService.get(assetId);
    DevelopmentTaskRevision revision = release.currentRevision();
    TaskDefinition definition = revision.definition();
    if (!"SQL".equalsIgnoreCase(definition.taskType())) {
      throw new IllegalArgumentException("当前仅支持 SQL 发布版本发布为数据服务");
    }

    SourceConfig config = sourceConfig(definition.configJson());
    DevelopmentReleaseSummary summary = release.release();
    SourceDescriptor descriptor = new SourceDescriptor(
        SOURCE_TYPE,
        Long.toString(assetId),
        summary.taskName(),
        definition.taskType(),
        summary.status().name(),
        revision.id(),
        revision.revisionNo(),
        config.dataSourceId(),
        config.timeoutSeconds(),
        "/query/" + assetId,
        summary.updateTime());
    return new ResolvedSource(descriptor, definition.content());
  }

  private long parseAssetId(String sourceRef) {
    if (!StringUtils.hasText(sourceRef)) throw new IllegalArgumentException("sourceRef 不能为空");
    try {
      long value = Long.parseLong(sourceRef.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("数据开发发布来源 sourceRef 必须是有效 assetId：" + sourceRef, exception);
    }
  }

  private SourceConfig sourceConfig(String configJson) {
    String raw = StringUtils.hasText(configJson) ? configJson.trim() : "{}";
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson 必须是 JSON Object");
      }
      String reference = root.path("dataSourceId").asText(null);
      if (!StringUtils.hasText(reference)) {
        throw new IllegalArgumentException("SQL 发布版本缺少 dataSourceId，无法发布数据服务");
      }
      long dataSourceId;
      try {
        dataSourceId = Long.parseLong(reference.trim());
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("SQL 发布版本 dataSourceId 非法：" + reference, exception);
      }
      if (dataSourceId <= 0L) {
        throw new IllegalArgumentException("SQL 发布版本 dataSourceId 必须大于 0");
      }

      int timeoutSeconds = root.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS);
      if (timeoutSeconds < 1 || timeoutSeconds > 3_600) {
        timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
      }
      return new SourceConfig(dataSourceId, timeoutSeconds);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL 发布版本 configJson 非法，无法发布数据服务", exception);
    }
  }

  private record SourceConfig(Long dataSourceId, Integer timeoutSeconds) {}
}
