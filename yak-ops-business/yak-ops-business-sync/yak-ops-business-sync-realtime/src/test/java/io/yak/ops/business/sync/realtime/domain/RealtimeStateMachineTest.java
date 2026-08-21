package io.yak.ops.business.sync.realtime.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeStateMachineTest {

  private final RealtimeStateMachine stateMachine = new RealtimeStateMachine();

  @Test
  void acceptsLifecycleAndRecoveryTransitions() {
    assertThatCode(() -> stateMachine.requireTransition("STOPPED", "STARTING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("UNKNOWN", "RUNNING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireTransition("STOPPING", "STOPPED"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsImpossibleDirectTransition() {
    assertThatThrownBy(() -> stateMachine.requireTransition("STOPPED", "RUNNING"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非法实时任务状态迁移");
  }
}
