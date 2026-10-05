package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步单个来源字段到目标字段的映射响应。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Data
public class DataSyncColumnMappingVO {

    /** 来源字段名称。 */
    private String source;

    /** 目标字段名称。 */
    private String target;
}
