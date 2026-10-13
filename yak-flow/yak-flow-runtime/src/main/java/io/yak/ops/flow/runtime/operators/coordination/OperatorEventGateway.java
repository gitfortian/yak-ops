package io.yak.ops.flow.runtime.operators.coordination;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous path for an operator to send control events to its coordinator.
 *
 * <p>Events travel from a Task to its Coordinator. Completion acknowledges Coordinator
 * processing; split consumption and checkpoint completion have separate lifecycles.
 */
@FunctionalInterface
public interface OperatorEventGateway {

    CompletionStage<Void> sendEventToCoordinator(OperatorEvent event);
}
