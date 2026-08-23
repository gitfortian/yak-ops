package io.yak.ops.business.sync.realtime.observability;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobEventView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.service.RealtimeEventStreamService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Stable application entry for realtime observability and event read-side use-cases. */
@Service("realtimeObservabilityApplicationService")
public class RealtimeObservabilityService {

  private final io.yak.ops.business.sync.realtime.service.RealtimeObservabilityService observability;
  private final RealtimeEventStreamService eventStream;
  private final RealtimeJobStore store;

  public RealtimeObservabilityService(
      io.yak.ops.business.sync.realtime.service.RealtimeObservabilityService observability,
      RealtimeEventStreamService eventStream,
      RealtimeJobStore store) {
    this.observability = observability;
    this.eventStream = eventStream;
    this.store = store;
  }

  public SseEmitter subscribe() {
    return eventStream.subscribe();
  }

  public List<RealtimeJobEventView> events(long id) {
    store.definition(id)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + id));
    return store.events(id);
  }

  public RealtimeObservabilityView snapshot(long id) {
    return observability.snapshot(id);
  }

  public String submissionLog(long id, int tailLines) {
    return observability.submissionLog(id, tailLines);
  }

  public RuntimeLog runtimeLog(long id, int maxExceptions) {
    return observability.runtimeLog(id, maxExceptions);
  }
}
