package io.yak.ops.common.enums.datasync;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 离线同步目标数据写入方式。
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncWriteMode {

    /** 保留目标已有数据并追加写入。 */
    APPEND(1, "追加写入"),

    /** 同步前清空目标数据，再写入本次全量数据。 */
    OVERWRITE(2, "覆盖写入"),

    /** 按目标主键更新已有数据，不存在时新增。 */
    UPSERT(3, "更新写入");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
