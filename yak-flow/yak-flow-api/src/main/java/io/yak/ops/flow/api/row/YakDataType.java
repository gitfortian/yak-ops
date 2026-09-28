package io.yak.ops.flow.api.row;

/**
 * YakFlow 跨连接器共享的逻辑类型契约；类型类别由 YakTypeKind 表达，参数化类型由具体实现承载自身属性。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
public interface YakDataType {

    /**
     * 返回逻辑类型类别。
     *
     * @return 类型类别
     */
    YakTypeKind kind();
}
