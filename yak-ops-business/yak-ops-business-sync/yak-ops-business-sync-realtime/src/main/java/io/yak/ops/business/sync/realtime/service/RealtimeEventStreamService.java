package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobChangeEvent;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Broadcasts committed realtime job changes and heartbeats to authenticated UI clients. */
@Service
public class RealtimeEventStreamService {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeEventStreamService.class);
  private static final long STREAM_TIMEOUT_MILLIS = Duration.ofHours(1).toMillis();
  private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "yak-realtime-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  public RealtimeEventStreamService() {
    heartbeat.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
  }

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
    emitters.add(emitter);
    Runnable cleanup = () -> emitters.remove(emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());
    send(emitter, "connected", Map.of("timestamp", Instant.now().toString()));
    return emitter;
  }

  @TransactionalEventListener(
      phase = TransactionPhase.AFTER_COMMIT,
      fallbackExecution = true)
  public void publish(RealtimeJobChangeEvent event) {
    for (SseEmitter emitter : emitters) {
      send(emitter, "realtime", event);
    }
  }

  private void heartbeat() {
    for (SseEmitter emitter : emitters) {
      send(emitter, "ping", Map.of("timestamp", Instant.now().toString()));
    }
  }

  private void send(SseEmitter emitter, String name, Object payload) {
    try {
      emitter.send(
          SseEmitter.event()
              .name(name)
              .id(Long.toString(System.nanoTime()))
              .data(payload));
    } catch (IOException | IllegalStateException exception) {
      emitters.remove(emitter);
      LOG.debug("Realtime SSE client disconnected: {}", exception.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    heartbeat.shutdownNow();
    emitters.forEach(SseEmitter::complete);
    emitters.clear();
  }
}
