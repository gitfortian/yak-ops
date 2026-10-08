package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 数据同步任务级字段映射输入。
 *
 * <p>mapping 为空表示继续使用系统默认的大小写不敏感同名映射；显式 mapping 的字段顺序属于任务定义。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Data
public class DataSyncMappingDTO {

    /** 显式来源到目标字段映射；同一来源字段和目标字段均只能出现一次。 */
    @Valid
    @NotEmpty(message = "字段映射不能为空")
    @Size(max = 1024, message = "字段映射不能超过 1024 项")
    private List<DataSyncColumnMappingDTO> columns;
}
