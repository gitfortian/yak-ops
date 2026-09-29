package io.yak.ops.business.datasync.execution.lifecycle;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.dao.repository.datasync.DataSyncAttemptRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * 收口单节点 Data Sync 应用启动恢复语义。
 *
 * <p>LocalExecution 本身不能跨进程恢复，因此上一进程遗留的 PENDING / RUNNING / RETRY_WAITING Execution 会标记为 LOST。
 * REALTIME 的 Debezium state 不在这里删除；用户后续重新运行同一 Task definitionVersion 时复用已有 offset/state。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
@DependsOn("yakOpsFlyway")
public class DataSyncExecutionRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncExecutionRecovery.class);

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncAttemptRepository attemptRepository;

    @PostConstruct
    public void recoverLostExecutions() {
        int attempts = attemptRepository.markActiveAsLost(
                DateUtils.now(),
                DataSyncErrorCode.EXECUTION_LOST.getCode(),
                DataSyncErrorCode.EXECUTION_LOST.getMessage());
        int executions = instanceRepository.markActiveAsLost(
                DateUtils.now(),
                DataSyncErrorCode.EXECUTION_LOST.getCode(),
                DataSyncErrorCode.EXECUTION_LOST.getMessage());
        if (executions > 0 || attempts > 0) {
            LOG.warn("应用启动发现遗留数据同步执行，已标记为 LOST，executions={}, attempts={}", executions, attempts);
        }
    }
}
