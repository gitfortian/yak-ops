package io.yak.ops.business.datasync.scheduler;

/**
 * 接收调度引擎产生的 Data Sync 到点事件；是否创建 Instance、跳过并发或进入 Retry 由 Data Sync 业务层决定。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public interface DataSyncScheduleFireListener {

    void onFire(DataSyncScheduleFire fire);
}
