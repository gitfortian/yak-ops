package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步单个来源字段到目标字段的 Schema 映射预览结果。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncFieldMappingVO {

    /** 来源表字段名称。 */
    private String sourceName;

    /** 来源数据库返回的原生字段类型名称。 */
    private String sourceType;

    /** 任务映射解析后的目标字段名称；目标物理字段缺失时仍保留配置名称。 */
    private String targetName;

    /** 目标数据库返回的原生字段类型名称；目标字段不存在时为空。 */
    private String targetType;

    /** 当前无 Transform 模式下，该来源字段是否可以直接写入目标字段。 */
    private boolean compatible;

    /** 不兼容时给编辑器展示的原因；兼容时为空。 */
    private String message;
}
