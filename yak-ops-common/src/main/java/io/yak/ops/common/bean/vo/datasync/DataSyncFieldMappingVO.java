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

    /** 来源表字段名称。 */
    private String sourceName;

    /** 来源数据库返回的原生字段类型名称。 */
    private String sourceType;

    /** 同名匹配到的目标表字段名称；目标缺失该字段时为空。 */
    private String targetName;

    /** 目标数据库返回的原生字段类型名称；目标字段不存在时为空。 */
    private String targetType;

    /** 当前无 Transform 模式下，该来源字段是否可以直接写入目标字段。 */
    private boolean compatible;

    /** 不兼容时给编辑器展示的原因；兼容时为空。 */
    private String message;
}
