package io.yak.ops.business.development.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.service.DataServiceService;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiInput;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.development.domain.DevelopmentReleaseDetail;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Explicit transition from an immutable ONLINE SQL release to a Data Service snapshot. */
@Service
@ConditionalOnDataSourceEnabled
public class DevelopmentDataServiceService {

  static final String SOURCE_TYPE = "DATA_DEVELOPMENT_RELEASE";
  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private final DevelopmentReleaseService releaseService;
  private final DataServiceService dataServiceService;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceService(
      DevelopmentReleaseService releaseService,
      DataServiceService dataServiceService,
      ObjectMapper objectMapper) {
    this.releaseService = releaseService;
    this.dataServiceService = dataServiceService;
    this.objectMapper = objectMapper;
  }

  public ReleaseDataServiceState state(long assetId) {
    DevelopmentReleaseDetail release = requireSqlRelease(assetId, false);
    Optional<ApiView> service = findService(assetId);
    boolean updateAvailable = service
        .map(value -> !Objects.equals(
            value.sourceRevisionId(), release.currentRevision().id()))
        .orElse(false);
    return new ReleaseDataServiceState(
        service.isPresent(),
        updateAvailable,
        release.release().currentRevisionNo(),
        release.release().status().name(),
        service.orElse(null));
  }

  public ApiView publish(long assetId, PublishCommand command) {
    DevelopmentReleaseDetail release = requireSqlRelease(assetId, true);
    DevelopmentTaskRevision revision = release.currentRevision();
    TaskDefinition definition = revision.definition();
    SourceConfig source = sourceConfig(definition.configJson());
    Optional<ApiView> existing = findService(assetId);
    PublishCommand request = command == null ? new PublishCommand(null, null, null, null, null, null) : command;

    String name = firstText(
        request.name(),
        existing.map(ApiView::name).orElse(null),
        release.release().taskName());
    String path = firstText(
        request.path(),
        existing.map(ApiView::path).orElse(null),
        "/query/" + assetId);
    Integer maxRows = request.maxRows() != null
        ? request.maxRows()
        : existing.map(ApiView::maxRows).orElse(DEFAULT_MAX_ROWS);
    Integer timeoutSeconds = request.timeoutSeconds() != null
        ? request.timeoutSeconds()
        : existing.map(ApiView::timeoutSeconds).orElse(source.timeoutSeconds());
    Boolean enabled = request.enabled() != null
        ? request.enabled()
        : existing.map(ApiView::enabled).orElse(Boolean.TRUE);
    String description = request.description() != null
        ? request.description()
        : existing.map(ApiView::description).orElse(null);

    return dataServiceService.saveFromSource(
        new SourceSnapshot(
            SOURCE_TYPE,
            Long.toString(assetId),
            revision.id(),
            revision.revisionNo()),
        new ApiInput(
            name,
            path,
            source.dataSourceId(),
            definition.content(),
            maxRows,
            timeoutSeconds,
            enabled,
            description));
  }

  private Optional<ApiView> findService(long assetId) {
    return dataServiceService.findBySource(SOURCE_TYPE, Long.toString(assetId));
  }

  private DevelopmentReleaseDetail requireSqlRelease(long assetId, boolean requireOnline) {
    DevelopmentReleaseDetail detail = releaseService.get(assetId);
    TaskDefinition definition = detail.currentRevision().definition();
    if (!"SQL".equalsIgnoreCase(definition.taskType())) {
      throw new IllegalArgumentException("当前仅支持 SQL 发布版本发布为数据服务");
    }
    if (requireOnline && detail.release().status() != TaskAssetStatus.ONLINE) {
      throw new IllegalArgumentException("只有 ONLINE 的 SQL 发布版本可以发布/更新数据服务");
    }
    return detail;
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

  private String firstText(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) return value.trim();
    }
    return null;
  }

  public record PublishCommand(
      String name,
      String path,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String description) {}

  public record ReleaseDataServiceState(
      boolean published,
      boolean updateAvailable,
      int releaseRevisionNo,
      String releaseStatus,
      ApiView detail) {}

  private record SourceConfig(Long dataSourceId, Integer timeoutSeconds) {}
}
