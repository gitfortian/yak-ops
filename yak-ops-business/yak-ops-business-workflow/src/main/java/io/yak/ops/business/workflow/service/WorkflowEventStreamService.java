package io.yak.ops.business.workflow.service;

import io.yak.ops.business.workflow.model.WorkflowInstanceVO;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 工作流实例状态 SSE 推送。 */
@Service
public class WorkflowEventStreamService {

  private static final Logger log = LoggerFactory.getLogger(WorkflowEventStreamService.class);
  private static final long STREAM_TIMEOUT_MILLIS = Duration.ofHours(1).toMillis();
  private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
  private static final String EVENT_NAME = "workflow";
  private static final String HEARTBEAT_EVENT_NAME = "ping";

  private final ConcurrentMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();
  private final ScheduledExecutorService heartbeatScheduler =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yak-workflow-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
      });

  public WorkflowEventStreamService() {
    heartbeatScheduler.scheduleAtFixedRate(
        this::heartbeat,
        HEARTBEAT_INTERVAL_SECONDS,
        HEARTBEAT_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  public SseEmitter subscribe(String executionId, WorkflowInstanceVO snapshot) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
    CopyOnWriteArrayList<SseEmitter> executionEmitters =
        emitters.computeIfAbsent(executionId, ignored -> new CopyOnWriteArrayList<>());
    executionEmitters.add(emitter);

    Runnable cleanup = () -> remove(executionId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());

    if (!send(executionId, emitter, snapshot)) {
      return emitter;
    }
    if (isTerminal(snapshot.status())) {
      cleanup.run();
      emitter.complete();
    }
    return emitter;
  }

  public void publish(WorkflowInstanceVO snapshot) {
    List<SseEmitter> executionEmitters = emitters.get(snapshot.id());
    if (executionEmitters == null || executionEmitters.isEmpty()) {
      return;
    }

    for (SseEmitter emitter : executionEmitters) {
      send(snapshot.id(), emitter, snapshot);
    }

    if (isTerminal(snapshot.status())) {
      CopyOnWriteArrayList<SseEmitter> terminalEmitters = emitters.remove(snapshot.id());
      if (terminalEmitters != null) {
        terminalEmitters.forEach(SseEmitter::complete);
      }
    }
  }

  private boolean send(
      String executionId,
      SseEmitter emitter,
      WorkflowInstanceVO snapshot) {
    try {
      emitter.send(SseEmitter.event()
          .name(EVENT_NAME)
          .id(Long.toString(System.nanoTime()))
          .data(snapshot));
      return true;
    } catch (IOException | IllegalStateException exception) {
      remove(executionId, emitter);
      log.debug(
          "[workflow] SSE client disconnected execution={}, message={}",
          executionId,
          exception.getMessage());
      return false;
    }
  }

  /**
   * 工作流可能数十秒都停留在 RUNNING；heartbeat 防止代理把没有业务事件的 SSE 连接当作空闲连接关闭。
   * 前端只消费 workflow 事件，因此 ping 不会污染运行快照状态机。
   */
  private void heartbeat() {
    if (emitters.isEmpty()) {
      return;
    }
    Map<String, Object> payload = Map.of("timestamp", Instant.now().toString());
    emitters.forEach((executionId, executionEmitters) -> {
      for (SseEmitter emitter : executionEmitters) {
        try {
          emitter.send(SseEmitter.event()
              .name(HEARTBEAT_EVENT_NAME)
              .id("ping-" + System.nanoTime())
              .data(payload));
        } catch (IOException | IllegalStateException exception) {
          remove(executionId, emitter);
        }
      }
    });
  }

  private void remove(String executionId, SseEmitter emitter) {
    emitters.computeIfPresent(executionId, (ignored, executionEmitters) -> {
      executionEmitters.remove(emitter);
      return executionEmitters.isEmpty() ? null : executionEmitters;
    });
  }

  private boolean isTerminal(String status) {
    return "SUCCESS".equals(status)
        || "SUCCESS_WITH_WARNINGS".equals(status)
        || "FAILED".equals(status)
        || "WARNING".equals(status)
        || "CANCELED".equals(status)
        || "TIMED_OUT".equals(status);
  }

  @PreDestroy
  void shutdown() {
    heartbeatScheduler.shutdownNow();
  }
}
