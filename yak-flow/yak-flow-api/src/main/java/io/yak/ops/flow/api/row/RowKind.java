package io.yak.ops.flow.api.row;

/**
 * 描述一条 YakRow 对目标表产生的变更语义，使批量数据与 CDC 数据共享同一行协议。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public enum RowKind {

    /** 新增一行；普通批量 Source 默认使用该类型。 */
    INSERT,

    /** 更新前的旧值，用于需要完整 changelog 的场景。 */
    UPDATE_BEFORE,

    /** 更新后的新值。 */
    UPDATE_AFTER,

    /** 删除已有行。 */
    DELETE
}
