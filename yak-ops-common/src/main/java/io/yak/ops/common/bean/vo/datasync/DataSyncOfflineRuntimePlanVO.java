package io.yak.ops.common.bean.vo.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import lombok.Data;

/**
 * OFFLINE Execution 创建时固化的运行参数规划摘要。
 *
 * <p>有效运行参数仍保存在 definitionSnapshot.runtimeConfig；本对象只记录本次规划依据与分片结果，
 * 供历史执行解释和诊断使用。</p>
 *
 * @author weifuwan
 * @since 2026-10-06
 */
@Data
public class DataSyncOfflineRuntimePlanVO {

    /** 本次 Execution 使用的规划策略。 */
    private DataSyncRuntimePolicy policy;

    /** 自动规划读取到的来源表行数；不可用或 FIXED 时为空。 */
    private Long sourceRowCount;

    /** 根据映射后的 Source Logical Schema 估算的单行字节数。 */
    private Integer estimatedRowBytes;

    /** 可用于动态范围分片的单整数主键；不可分片时为空。 */
    private String splitColumn;

    /** 按 rowCount / splitSize 估算的分片数量；整表读取为 1。 */
    private Integer splitCount;

    /** 自动规划是否成功读取 Source MIN / MAX / COUNT 统计。 */
    private Boolean statisticsAvailable;
}
