package io.yak.ops.business.workflow.domain;

/**
 * 调度启动调用栈内的 Trigger 绑定作用域。
 *
 * <p>只用于把 engine.start 刚持久化的 WorkflowExecution ID 在同一事务里提前写回
 * Trigger Ledger；作用域由 WorkflowLaunchService 使用 try-with-resources 严格清理。</p>
 */
public final class WorkflowScheduleLaunchBindingScope {
  private static final ThreadLocal<String> TRIGGER_ID = new ThreadLocal<>();

  private WorkflowScheduleLaunchBindingScope() {
  }

  public static Scope open(String triggerId) {
    if (triggerId == null || triggerId.isBlank()) {
      throw new IllegalArgumentException("调度 triggerId 不能为空");
    }
    if (TRIGGER_ID.get() != null) {
      throw new IllegalStateException("当前线程已经存在工作流调度启动绑定作用域");
    }
    TRIGGER_ID.set(triggerId.trim());
    return TRIGGER_ID::remove;
  }

  public static String currentTriggerId() {
    return TRIGGER_ID.get();
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
