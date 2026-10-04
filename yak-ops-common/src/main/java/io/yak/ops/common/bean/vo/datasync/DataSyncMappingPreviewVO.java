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

    /** 目标物理表当前是否已经存在。 */
    private boolean targetTableExists;

    /** 本次预览是否允许目标表不存在时自动建表。 */
    private boolean autoCreateTable;

    /** 目标表不存在且自动建表可行时生成的 CREATE TABLE SQL。 */
    private String createTableSql;

    /** 自动建表规划中的非阻塞提示。 */
    private List<String> warnings = List.of();

    /** 自动建表规划中的阻塞原因。 */
    private List<String> unsupportedReasons = List.of();

    /** 按来源字段生成的同名字段映射明细。 */
    private List<DataSyncFieldMappingVO> mappings = List.of();
}
