package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Explicit transition policy for the observed-state axis. */
@Component
public class RealtimeStateMachine {

  private final Map<ObservedState, EnumSet<ObservedState>> transitions =
      new EnumMap<>(ObservedState.class);

  public RealtimeStateMachine() {
    allow(
        ObservedState.STOPPED,
        ObservedState.STARTING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN);
    allow(
        ObservedState.STARTING,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.STOPPING, ObservedState.STOPPED, ObservedState.FAILED, ObservedState.UNKNOWN);
    allow(ObservedState.FAILED, ObservedState.STOPPED, ObservedState.STARTING);
    allow(
        ObservedState.UNKNOWN,
        ObservedState.RUNNING,
        ObservedState.STOPPED,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.CONFLICT);
    allow(
        ObservedState.CONFLICT,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.STOPPED,
        ObservedState.UNKNOWN);
  }

  public void requireTransition(String fromValue, String toValue) {
    ObservedState from = ObservedState.valueOf(fromValue);
    ObservedState to = ObservedState.valueOf(toValue);
    if (from == to) {
      return;
    }
    if (!transitions.getOrDefault(from, EnumSet.noneOf(ObservedState.class)).contains(to)) {
      throw new IllegalStateException("非法实时任务状态迁移：" + from + " -> " + to);
    }
  }

  private void allow(ObservedState from, ObservedState... targets) {
    transitions.put(from, EnumSet.copyOf(Arrays.asList(targets)));
  }
}
