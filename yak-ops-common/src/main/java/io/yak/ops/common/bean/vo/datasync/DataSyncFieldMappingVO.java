package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 离线同步同名字段自动映射结果。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncFieldMappingVO {

    private String sourceName;

    private String sourceType;

    private String targetName;

    private String targetType;

    private boolean compatible;

    private String message;
}
