package io.yak.ops.business.datasync.scheduler;

/**
 * 表示底层调度引擎无法完成注册、修改、删除或查询操作，不暴露具体 Scheduler 实现异常。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public class ScheduleEngineException extends RuntimeException {

    public ScheduleEngineException(String message) {
        super(message);
    }

    public ScheduleEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
