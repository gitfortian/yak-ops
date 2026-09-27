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

    private boolean compatible;

    private List<DataSyncFieldMappingVO> mappings = List.of();
}
