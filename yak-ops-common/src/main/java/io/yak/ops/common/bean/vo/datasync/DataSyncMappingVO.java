package io.yak.ops.common.bean.vo.datasync;

import java.util.List;
import lombok.Data;

/**
 * 数据同步任务级字段映射响应。
 *
 * <p>为空表示该任务继续使用系统默认的大小写不敏感同名映射。</p>
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Data
public class DataSyncMappingVO {

    /** 显式来源到目标字段映射，顺序与任务定义保持一致。 */
    private List<DataSyncColumnMappingVO> columns = List.of();
}
