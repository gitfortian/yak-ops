package io.yak.ops.business.dataservice.service;

import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.DocumentationInput;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.ParameterDoc;
import io.yak.ops.business.dataservice.service.DataServiceDocumentationService.ResponseFieldDoc;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.dataservice.service.DataServiceService.RuntimeDefinition;
import io.yak.ops.business.dataservice.service.DataServiceService.ServiceSettingsInput;
import io.yak.ops.business.dataservice.service.DataServiceService.SourceSnapshot;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourcePage;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Single backend transition from an immutable upstream revision to a Data Service runtime snapshot.
 *
 * <p>Executable SQL and datasource identity are always resolved server-side. Sources that own their
 * service definition also make name/path/contracts/limits authoritative; Data Service then owns only
 * runtime concerns such as enablement, access control, resilience and observability.
 */
@Service
@ConditionalOnDataSourceEnabled
public class DataServicePublicationService {

  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_SOURCE_PAGE_SIZE = 100;

  private final DataServiceService dataServiceService;
  private final DataServiceDocumentationService documentationService;
  private final Map<String, DataServiceSourceProvider> providers;

  public DataServicePublicationService(
      DataServiceService dataServiceService,
      DataServiceDocumentationService documentationService,
      List<DataServiceSourceProvider> sourceProviders) {
    this.dataServiceService = dataServiceService;
    this.documentationService = documentationService;
    Map<String, DataServiceSourceProvider> discovered = new LinkedHashMap<>();
    for (DataServiceSourceProvider provider :
        sourceProviders == null ? List.<DataServiceSourceProvider>of() : sourceProviders) {
      String type = normalizeSourceType(provider.sourceType());
      DataServiceSourceProvider duplicate = discovered.putIfAbsent(type, provider);
      if (duplicate != null) {
        throw new IllegalStateException("数据服务发布来源类型重复：" + type);
      }
    }
    this.providers = Map.copyOf(discovered);
  }

  public SourcePage sources(String sourceType, int pageNo, int pageSize, String keyword) {
    int normalizedPageNo = Math.max(1, pageNo);
    int normalizedPageSize = Math.max(1, Math.min(MAX_SOURCE_PAGE_SIZE, pageSize));
    return provider(sourceType).list(normalizedPageNo, normalizedPageSize, normalizeKeyword(keyword));
  }

  public PublicationState state(String sourceType, String sourceRef) {
    SourceIdentity identity = normalizeIdentity(sourceType, sourceRef);
    ResolvedSource resolved = requireResolvedSource(provider(identity.sourceType()), identity.sourceRef());
    SourceDescriptor source = resolved.descriptor();
    Optional<ApiView> existing = dataServiceService.findBySource(identity.sourceType(), identity.sourceRef());
    boolean updateAvailable = existing
        .map(api -> !Objects.equals(api.sourceRevisionId(), source.sourceRevisionId()))
        .orElse(false);
    return new PublicationState(existing.isPresent(), updateAvailable, source, existing.orElse(null));
  }

  @Transactional
  public ApiView publish(PublishRequest request) {
    if (request == null) throw new IllegalArgumentException("发布配置不能为空");
    SourceIdentity identity = normalizeIdentity(request.sourceType(), request.sourceRef());
    DataServiceSourceProvider provider = provider(identity.sourceType());
    ResolvedSource resolved = requireResolvedSource(provider, identity.sourceRef());
    SourceDescriptor source = resolved.descriptor();
    requirePublishable(source);

    Optional<ApiView> existing = dataServiceService.findBySource(identity.sourceType(), identity.sourceRef());
    ServiceSettingsInput settings = provider.managesServiceDefinition()
        ? sourceManagedSettings(source, request, existing)
        : legacySettings(source, request, existing);

    ApiView saved = dataServiceService.saveFromSource(
        new SourceSnapshot(
            identity.sourceType(),
            identity.sourceRef(),
            source.sourceRevisionId(),
            source.sourceRevisionNo()),
        new RuntimeDefinition(source.dataSourceId(), resolved.sql()),
        settings);

    if (provider.managesServiceDefinition()) {
      syncSourceContract(saved.id(), resolved.contract());
    }
    return saved;
  }

  /**
   * Updates service-facing settings only for legacy/unmanaged sources.
   *
   * <p>For source-managed APIs these fields belong to the upstream Data Service Node Revision and
   * must be changed there, then propagated with republish.
   */
  @Transactional
  public ApiView updateSettings(Long apiId, PublicationSettings settings) {
    ApiView current = dataServiceService.get(apiId);
    if (isSourceManaged(current)) {
      throw new IllegalStateException(
          "当前 API 定义由数据开发 Data Service Node 管理，请发布新的 Revision 后重新发布 Runtime");
    }
    PublicationSettings values = settings == null
        ? new PublicationSettings(null, null, null, null, null, null)
        : settings;
    return dataServiceService.updateSettings(
        apiId,
        new ServiceSettingsInput(
            values.name(),
            values.path(),
            values.maxRows(),
            values.timeoutSeconds(),
            values.enabled(),
            values.description()));
  }

  public boolean managesServiceDefinition(Long apiId) {
    return isSourceManaged(dataServiceService.get(apiId));
  }

  /** Refreshes the immutable Runtime snapshot from the latest published upstream revision. */
  @Transactional
  public ApiView republish(Long apiId, PublicationSettings settings) {
    ApiView current = dataServiceService.get(apiId);
    if (!StringUtils.hasText(current.sourceType()) || !StringUtils.hasText(current.sourceRef())) {
      throw new IllegalArgumentException("旧版手工数据服务没有发布来源，不能执行重新发布");
    }
    DataServiceSourceProvider sourceProvider = providerIfPresent(current.sourceType());
    if (sourceProvider == null) {
      throw new IllegalArgumentException(
          "该 API 使用历史冻结来源，Runtime Snapshot 可继续运行，但不再支持重新发布："
              + current.sourceType());
    }

    PublicationSettings values = settings == null
        ? new PublicationSettings(null, null, null, null, null, null)
        : settings;
    ApiView refreshed = publish(new PublishRequest(
        current.sourceType(),
        current.sourceRef(),
        values.name(),
        values.path(),
        values.maxRows(),
        values.timeoutSeconds(),
        values.enabled(),
        values.description()));
    if (!Objects.equals(current.id(), refreshed.id())) {
      throw new IllegalStateException("重新发布没有更新原数据服务，请检查来源唯一性：" + apiId);
    }
    return refreshed;
  }

  private boolean isSourceManaged(ApiView api) {
    if (api == null) return false;
    DataServiceSourceProvider provider = providerIfPresent(api.sourceType());
    return provider != null && provider.managesServiceDefinition();
  }

  private ServiceSettingsInput sourceManagedSettings(
      SourceDescriptor source,
      PublishRequest request,
      Optional<ApiView> existing) {
    Boolean enabled = request.enabled() != null
        ? request.enabled()
        : existing.map(ApiView::enabled).orElse(Boolean.TRUE);
    return new ServiceSettingsInput(
        source.name(),
        source.defaultPath(),
        source.maxRows() == null ? DEFAULT_MAX_ROWS : source.maxRows(),
        source.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : source.timeoutSeconds(),
        enabled,
        source.description());
  }

  private ServiceSettingsInput legacySettings(
      SourceDescriptor source,
      PublishRequest request,
      Optional<ApiView> existing) {
    String name = firstText(request.name(), existing.map(ApiView::name).orElse(null), source.name());
    String path = firstText(request.path(), existing.map(ApiView::path).orElse(null), source.defaultPath());
    Integer maxRows = request.maxRows() != null
        ? request.maxRows()
        : existing.map(ApiView::maxRows)
            .orElse(source.maxRows() == null ? DEFAULT_MAX_ROWS : source.maxRows());
    Integer timeoutSeconds = request.timeoutSeconds() != null
        ? request.timeoutSeconds()
        : existing.map(ApiView::timeoutSeconds)
            .orElse(source.timeoutSeconds() == null ? DEFAULT_TIMEOUT_SECONDS : source.timeoutSeconds());
    Boolean enabled = request.enabled() != null
        ? request.enabled()
        : existing.map(ApiView::enabled).orElse(Boolean.TRUE);
    String description = request.description() != null
        ? request.description()
        : existing.map(ApiView::description).orElse(source.description());
    return new ServiceSettingsInput(name, path, maxRows, timeoutSeconds, enabled, description);
  }

  private void syncSourceContract(Long apiId, SourceContract contract) {
    SourceContract value = contract == null ? SourceContract.empty() : contract;
    List<ParameterDoc> parameters = value.parameters().stream()
        .map(item -> new ParameterDoc(
            item.name(), item.type(), item.required(), item.description(), item.example()))
        .toList();
    List<ResponseFieldDoc> responseFields = value.responseFields().stream()
        .map(item -> new ResponseFieldDoc(
            item.name(), item.type(), item.nullable(), item.description(), item.example()))
        .toList();
    documentationService.save(apiId, new DocumentationInput(parameters, responseFields));
  }

  private DataServiceSourceProvider provider(String sourceType) {
    String normalized = normalizeSourceType(sourceType);
    DataServiceSourceProvider provider = providers.get(normalized);
    if (provider == null) {
      throw new IllegalArgumentException("不支持的数据服务发布来源：" + normalized);
    }
    return provider;
  }

  private DataServiceSourceProvider providerIfPresent(String sourceType) {
    if (!StringUtils.hasText(sourceType)) return null;
    return providers.get(normalizeSourceType(sourceType));
  }

  private ResolvedSource requireResolvedSource(DataServiceSourceProvider provider, String sourceRef) {
    ResolvedSource resolved = provider.resolve(sourceRef);
    if (resolved == null || resolved.descriptor() == null) {
      throw new IllegalStateException("发布来源解析结果为空：" + provider.sourceType() + "/" + sourceRef);
    }
    SourceDescriptor source = resolved.descriptor();
    SourceIdentity descriptorIdentity = normalizeIdentity(source.sourceType(), source.sourceRef());
    SourceIdentity requestedIdentity = normalizeIdentity(provider.sourceType(), sourceRef);
    if (!descriptorIdentity.equals(requestedIdentity)) {
      throw new IllegalStateException(
          "发布来源返回了不匹配的来源标识：" + source.sourceType() + "/" + source.sourceRef());
    }
    return resolved;
  }

  private void requirePublishable(SourceDescriptor source) {
    if (!"ONLINE".equalsIgnoreCase(source.status())) {
      throw new IllegalArgumentException("只有 ONLINE 的发布来源可以发布/更新数据服务");
    }
    if (source.sourceRevisionId() == null || source.sourceRevisionId() <= 0L
        || source.sourceRevisionNo() == null || source.sourceRevisionNo() <= 0) {
      throw new IllegalArgumentException("发布来源缺少有效的不可变 Revision");
    }
    if (source.dataSourceId() == null || source.dataSourceId() <= 0L) {
      throw new IllegalArgumentException("发布来源缺少有效的数据源");
    }
  }

  private SourceIdentity normalizeIdentity(String sourceType, String sourceRef) {
    String type = normalizeSourceType(sourceType);
    if (!StringUtils.hasText(sourceRef)) throw new IllegalArgumentException("sourceRef 不能为空");
    return new SourceIdentity(type, sourceRef.trim());
  }

  private String normalizeSourceType(String sourceType) {
    if (!StringUtils.hasText(sourceType)) throw new IllegalArgumentException("sourceType 不能为空");
    String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > 64) throw new IllegalArgumentException("sourceType 不能超过 64 个字符");
    return normalized;
  }

  private String normalizeKeyword(String keyword) {
    return StringUtils.hasText(keyword) ? keyword.trim() : null;
  }

  private String firstText(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) return value.trim();
    }
    return null;
  }

  public record PublishRequest(
      String sourceType,
      String sourceRef,
      String name,
      String path,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String description) {}

  public record PublicationSettings(
      String name,
      String path,
      Integer maxRows,
      Integer timeoutSeconds,
      Boolean enabled,
      String description) {}

  public record PublicationState(
      boolean published,
      boolean updateAvailable,
      SourceDescriptor source,
      ApiView detail) {}

  private record SourceIdentity(String sourceType, String sourceRef) {}
}
