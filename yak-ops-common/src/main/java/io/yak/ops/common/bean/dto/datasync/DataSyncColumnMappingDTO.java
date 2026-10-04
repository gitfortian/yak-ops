package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据同步单个来源字段到目标字段的映射输入。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Data
public class DataSyncColumnMappingDTO {

    /** 来源字段名称。 */
    @NotBlank(message = "来源映射字段不能为空")
    @Size(max = 128, message = "来源映射字段名称不能超过 128 个字符")
    private String source;

    /** 目标字段名称。 */
    @NotBlank(message = "目标映射字段不能为空")
    @Size(max = 128, message = "目标映射字段名称不能超过 128 个字符")
    private String target;
}
