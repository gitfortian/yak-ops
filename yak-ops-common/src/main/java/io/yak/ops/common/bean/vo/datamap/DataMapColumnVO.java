package io.yak.ops.common.bean.vo.datamap;

import lombok.Getter;
import lombok.Setter;

/** One collected column of an asset. */
@Getter
@Setter
public class DataMapColumnVO {

    /** Engine-reported ordinal position, from one. */
    private Integer ordinal;

    /** Column name. */
    private String name;

    /** Engine-reported native type name. */
    private String typeName;

    /** java.sql.Types code. */
    private Integer jdbcType;

    /** Declared or reported column size. */
    private Integer size;

    /** Declared or reported numeric scale. */
    private Integer scale;

    /** Whether the column admits nulls. */
    private Boolean nullable;

    /** Whether the column participates in the primary key. */
    private Boolean primaryKey;

    /** Ordinal inside the declared primary key. */
    private Integer primaryKeyPosition;

    /** Engine-reported column comment. */
    private String remarks;
}
