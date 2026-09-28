package io.yak.ops.common.bean.vo.datasync;

import java.util.List;
import lombok.Data;

/**
 * 离线同步字段自动映射整体预览结果。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncMappingPreviewVO {

    /** 当前来源表全部字段是否满足无 Transform 的直接同步条件。 */
    private boolean compatible;

    /** 按来源字段生成的同名字段映射明细。 */
    private List<DataSyncFieldMappingVO> mappings = List.of();
}
