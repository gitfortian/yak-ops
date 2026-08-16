package io.yak.ops.business.development.service;

import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublicationState;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublishRequest;
import io.yak.ops.business.dataservice.service.DataServiceService.ApiView;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Service;

/**
 * Backward-compatible Data Development facade.
 *
 * <p>The actual publish transition now lives in Data Service so every entry point uses the same
 * server-side source resolution and snapshot rules.
 */
@Service
@ConditionalOnDataSourceEnabled
public class DevelopmentDataServiceService {

  static final String SOURCE_TYPE = DevelopmentDataServiceSourceProvider.SOURCE_TYPE;

  private final DataServicePublicationService publicationService;

  public DevelopmentDataServiceService(DataServicePublicationService publicationService) {
    this.publicationService = publicationService;
  }

  public ReleaseDataServiceState state(long assetId) {
    PublicationState state = publicationService.state(SOURCE_TYPE, Long.toString(assetId));
    return new ReleaseDataServiceState(
        state.published(),
        state.updateAvailable(),
        state.source().sourceRevisionNo(),
        state.source().status(),
        state.detail());
  }

  public ApiView publish(long assetId, PublishCommand command) {
    PublishCommand request = command == null
        ? new PublishCommand(null, null, null, null, null, null)
        : command;
    return publicationService.publish(new PublishRequest(
        SOURCE_TYPE,
        Long.toString(assetId),
        request.name(),
        request.path(),
        request.maxRows(),
        request.timeoutSeconds(),
        request.enabled(),
        request.description()));
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
}
