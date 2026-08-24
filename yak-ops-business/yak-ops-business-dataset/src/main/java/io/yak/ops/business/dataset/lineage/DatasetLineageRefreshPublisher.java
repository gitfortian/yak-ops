package io.yak.ops.business.dataset.lineage;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes Dataset lineage refresh requests without leaking Spring events into business roles. */
@Component
public class DatasetLineageRefreshPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public DatasetLineageRefreshPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void request(long datasetId) {
    eventPublisher.publishEvent(new DatasetLineageRefreshRequested(datasetId));
  }
}
